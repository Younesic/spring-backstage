package io.github.younesic.backstage.core.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ToolingResolverTest {

    private final List<String> warnings = new ArrayList<>();
    private final ToolingResolver.Warn warn = warnings::add;

    private static Map<String, String> vars() {
        return Map.of(
                "groupId", "io.acme.shop",
                "artifactId", "orders",
                "version", "1.4.0",
                "branch", "main",
                "harborProject", "shop");
    }

    private static CiEnvironment noCi() {
        return CiEnvironment.detect(Map.of());
    }

    @Test
    void conventionDefaultsWhenNoEnvNoOverride() {
        Map<String, String> out = ToolingResolver.resolve(null, Map.of(), noCi(), vars(), warn);
        assertEquals("io.acme.shop_orders", out.get("sonarqube.org/project-key"));
        assertEquals("orders", out.get("argocd/app-name"));
        assertEquals("shop/orders", out.get("goharbor.io/repository-slug"));
        assertEquals("orders", out.get("dependencytrack/project-name"));
        assertEquals("1.4.0", out.get("dependencytrack/project-version"));
        assertFalse(out.containsKey("dependencytrack/project-id"),
                "project-id is opt-in: omitted without DTRACK_PROJECT_ID");
    }

    @Test
    void envBeatsConvention() {
        CiEnvironment ci = CiEnvironment.detect(Map.of(
                "GITLAB_CI", "true",
                "SONAR_PROJECT_KEY", "env-sonar-key"));
        Map<String, String> out = ToolingResolver.resolve(null, Map.of(), ci, vars(), warn);
        assertEquals("env-sonar-key", out.get("sonarqube.org/project-key"));
        // Harbor has no env source — the registry-host-bearing CI_REGISTRY_IMAGE is intentionally NOT
        // consumed; the {harborProject}/{artifactId} convention is authoritative.
        assertEquals("shop/orders", out.get("goharbor.io/repository-slug"));
    }

    @Test
    void harborIgnoresCiRegistryImageAndUsesConvention() {
        CiEnvironment ci = CiEnvironment.detect(Map.of(
                "GITLAB_CI", "true",
                "CI_REGISTRY_IMAGE", "registry.gitlab.com/shop/orders"));
        Map<String, String> out = ToolingResolver.resolve(null, Map.of(), ci, vars(), warn);
        assertEquals("shop/orders", out.get("goharbor.io/repository-slug"));
    }

    @Test
    void overrideBeatsEverything() {
        CiEnvironment ci = CiEnvironment.detect(Map.of("SONAR_PROJECT_KEY", "env-sonar-key"));
        Map<String, String> overrides = ToolingResolver.parseOverrides(
                new String[] {"sonarqube.org/project-key=explicit", "argocd/app-name=orders-prod"}, warn);
        Map<String, String> out = ToolingResolver.resolve(null, overrides, ci, vars(), warn);
        assertEquals("explicit", out.get("sonarqube.org/project-key"));
        assertEquals("orders-prod", out.get("argocd/app-name"));
    }

    @Test
    void envBeatsConventionForEveryStandardKey() {
        // When the pipeline exports the real values, they win over the theoretical conventions.
        CiEnvironment ci = CiEnvironment.detect(Map.of(
                "SONAR_PROJECT_KEY", "real-sonar",
                "ARGOCD_APP_NAME", "orders-prod",
                "HARBOR_REPOSITORY", "shop/orders-image",
                "DTRACK_PROJECT_NAME", "orders-svc",
                "DTRACK_PROJECT_VERSION", "2.3.4"));
        Map<String, String> out = ToolingResolver.resolve(null, Map.of(), ci, vars(), warn);
        assertEquals("real-sonar", out.get("sonarqube.org/project-key"));
        assertEquals("orders-prod", out.get("argocd/app-name"));
        assertEquals("shop/orders-image", out.get("goharbor.io/repository-slug"));
        assertEquals("orders-svc", out.get("dependencytrack/project-name"));
        assertEquals("2.3.4", out.get("dependencytrack/project-version"));
    }

    @Test
    void optInDtrackIdEmittedOnlyWithEnv() {
        CiEnvironment ci = CiEnvironment.detect(Map.of("DTRACK_PROJECT_ID", "abc-123-uuid"));
        Map<String, String> out = ToolingResolver.resolve(null, Map.of(), ci, vars(), warn);
        assertEquals("abc-123-uuid", out.get("dependencytrack/project-id"));
    }

    @Test
    void userRuleOverridesDefaultConventionByKey() {
        ToolingRule custom = new ToolingRule("argocd/app-name", List.of(), "{artifactId}-{branch}");
        Map<String, String> out = ToolingResolver.resolve(List.of(custom), Map.of(), noCi(), vars(), warn);
        assertEquals("orders-main", out.get("argocd/app-name"));
    }

    @Test
    void disabledUserRuleSuppressesADefault() {
        ToolingRule disabled = new ToolingRule("sonarqube.org/project-key", List.of(), "{groupId}_{artifactId}");
        disabled.enabled = false;
        Map<String, String> out = ToolingResolver.resolve(List.of(disabled), Map.of(), noCi(), vars(), warn);
        assertFalse(out.containsKey("sonarqube.org/project-key"));
    }

    @Test
    void newUserRuleAddsACustomKey() {
        ToolingRule extra = new ToolingRule("backstage.io/kubernetes-id", List.of(), "{artifactId}");
        Map<String, String> out = ToolingResolver.resolve(List.of(extra), Map.of(), noCi(), vars(), warn);
        assertEquals("orders", out.get("backstage.io/kubernetes-id"));
    }

    @Test
    void unresolvedConventionPlaceholderOmitsAnnotation() {
        // No harborProject var -> the {harborProject}/{artifactId} convention cannot resolve.
        Map<String, String> incomplete = Map.of("artifactId", "orders");
        Map<String, String> out = ToolingResolver.resolve(null, Map.of(), noCi(), incomplete, warn);
        assertFalse(out.containsKey("goharbor.io/repository-slug"),
                "an under-specified convention must be omitted, never emitted with a literal {placeholder}");
        assertEquals("orders", out.get("argocd/app-name"));
    }

    @Test
    void registryOrderIsDeterministic() {
        Map<String, String> out = ToolingResolver.resolve(null, Map.of(), noCi(), vars(), warn);
        assertEquals(List.of(
                        "sonarqube.org/project-key",
                        "argocd/app-name",
                        "goharbor.io/repository-slug",
                        "dependencytrack/project-name",
                        "dependencytrack/project-version"),
                List.copyOf(out.keySet()));
    }

    @Test
    void invalidRuleKeyIsIgnoredWithWarning() {
        ToolingRule bad = new ToolingRule("Invalid Key With Spaces", List.of(), "x");
        Map<String, String> out = ToolingResolver.resolve(List.of(bad), Map.of(), noCi(), vars(), warn);
        assertFalse(out.containsKey("Invalid Key With Spaces"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("invalid key")));
    }

    @Test
    void parseOverridesDropsMalformedEntries() {
        Map<String, String> out = ToolingResolver.parseOverrides(
                new String[] {"argocd/app-name=ok", "no-equals-sign", "=missing-key", "  "}, warn);
        assertEquals(Map.of("argocd/app-name", "ok"), out);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("malformed")));
    }

    @Test
    void parseOverridesKeepsValueWithEmbeddedEquals() {
        Map<String, String> out = ToolingResolver.parseOverrides(
                new String[] {"some.tool/expr=a=b=c"}, warn);
        assertEquals("a=b=c", out.get("some.tool/expr"));
    }
}

package io.github.younesic.backstage.core.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class DefaultToolingRulesTest {

    @Test
    void registersTheSixStandardTools() {
        List<String> keys = DefaultToolingRules.all().stream().map(r -> r.key).collect(Collectors.toList());
        assertEquals(List.of(
                "sonarqube.org/project-key",
                "argocd/app-name",
                "goharbor.io/repository-slug",
                "dependencytrack/project-name",
                "dependencytrack/project-version",
                "dependencytrack/project-id"), keys);
    }

    @Test
    void dtrackProjectIdHasNoConventionSoItIsOptIn() {
        ToolingRule projectId = DefaultToolingRules.all().stream()
                .filter(r -> r.key.equals("dependencytrack/project-id"))
                .findFirst().orElseThrow();
        assertTrue(projectId.convention == null || projectId.convention.isEmpty(),
                "project-id must have no convention so it is omitted unless DTRACK_PROJECT_ID is set");
        assertEquals(List.of("DTRACK_PROJECT_ID"), projectId.envNames());
    }

    @Test
    void everyDefaultRuleIsEnabled() {
        assertTrue(DefaultToolingRules.all().stream().allMatch(r -> r.enabled));
    }

    @Test
    void allReturnsAFreshListEachCall() {
        assertNotSame(DefaultToolingRules.all(), DefaultToolingRules.all());
    }
}

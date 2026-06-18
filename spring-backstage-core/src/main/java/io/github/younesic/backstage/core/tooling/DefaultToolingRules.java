package io.github.younesic.backstage.core.tooling;

import java.util.List;

/**
 * The built-in tooling-annotation registry: conventions and CI env sources for the platform's standard
 * tools (SonarQube, ArgoCD, Harbor, Dependency-Track). Keys are the <em>bare</em> keys the matching
 * Backstage frontend plugins read — they are intentionally NOT under the generator's
 * {@code annotationPrefix}. Every value is derived; nothing here ever fails the build, and any key with
 * no resolvable source is simply omitted.
 *
 * <p>Templates accept {@code {groupId}}, {@code {artifactId}}, {@code {version}}, {@code {branch}} and
 * {@code {harborProject}}. Plugin {@code <toolingAnnotations>} rules are merged over these <em>by key</em>
 * (override the convention/env, or disable a default with {@code enabled=false}) — see
 * {@link ToolingResolver#merge}.
 *
 * <p>Dependency-Track runtime value: the build cannot know the project UUID (created when the SBOM is
 * uploaded). The default therefore emits the <em>stable</em> {@code project-name} + {@code project-version}
 * (resolvable by name post-ingestion). {@code dependencytrack/project-id} is opt-in only — emitted solely
 * when {@code DTRACK_PROJECT_ID} is present (no convention), so it stays absent unless a pipeline pins it.
 */
public final class DefaultToolingRules {

    private DefaultToolingRules() {
    }

    /** A fresh, ordered copy of the built-in rules (registry order = emission order). */
    public static List<ToolingRule> all() {
        return List.of(
                new ToolingRule("sonarqube.org/project-key", List.of("SONAR_PROJECT_KEY"), "{groupId}_{artifactId}"),
                new ToolingRule("argocd/app-name", List.of(), "{artifactId}"),
                // No env source: the Harbor plugin expects a bare "project/repository" slug (no registry
                // host). GitLab's CI_REGISTRY_IMAGE carries the registry host (e.g. registry.gitlab.com/
                // group/proj), which the plugin cannot resolve — so the convention is authoritative.
                new ToolingRule("goharbor.io/repository-slug", List.of(), "{harborProject}/{artifactId}"),
                new ToolingRule("dependencytrack/project-name", List.of(), "{artifactId}"),
                new ToolingRule("dependencytrack/project-version", List.of(), "{version}"),
                new ToolingRule("dependencytrack/project-id", List.of("DTRACK_PROJECT_ID"), null));
    }
}

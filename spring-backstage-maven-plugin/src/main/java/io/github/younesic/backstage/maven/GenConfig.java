package io.github.younesic.backstage.maven;

import java.util.List;

import io.github.younesic.backstage.core.tooling.ToolingRule;

/** Generation settings shared by the per-module and aggregate mojos. */
final class GenConfig {
    final String annotationPrefix;
    final String apiDefinitionRef;
    final boolean emitApi;
    final String sourceLocationBranch;
    final boolean inferConsumedApis;
    /** User-supplied tooling rules merged over the built-in defaults (may be empty/null). */
    final List<ToolingRule> toolingRules;
    /** Harbor project namespace for the {@code goharbor.io/repository-slug} convention (may be null). */
    final String harborProject;
    /** SCM provider override ({@code github}/{@code gitlab}) for self-hosted hosts; null = auto-detect. */
    final String scmProvider;

    GenConfig(String annotationPrefix, String apiDefinitionRef, boolean emitApi, String sourceLocationBranch,
            boolean inferConsumedApis, List<ToolingRule> toolingRules, String harborProject, String scmProvider) {
        this.annotationPrefix = annotationPrefix;
        this.apiDefinitionRef = apiDefinitionRef;
        this.emitApi = emitApi;
        this.sourceLocationBranch = sourceLocationBranch;
        this.inferConsumedApis = inferConsumedApis;
        this.toolingRules = toolingRules;
        this.harborProject = harborProject;
        this.scmProvider = scmProvider;
    }
}

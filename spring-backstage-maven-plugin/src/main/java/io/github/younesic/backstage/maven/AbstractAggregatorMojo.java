package io.github.younesic.backstage.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.github.younesic.backstage.core.build.NameUniqueness;
import io.github.younesic.backstage.core.derive.GitMetadata;
import io.github.younesic.backstage.core.derive.GitMetadata.RepoInfo;
import io.github.younesic.backstage.core.model.CatalogEntity;
import io.github.younesic.backstage.core.tooling.ToolingRule;

/**
 * Shared base for the reactor-wide aggregator goals ({@code generate-catalog-info-aggregate} and
 * {@code check-catalog-names}). Collects every annotated, non-pom module from {@code ${reactorProjects}}
 * through the same {@link CatalogGeneration} code path as the per-module mojo, and enforces global
 * name uniqueness. Modules must already be compiled (run after {@code mvn package}/{@code process-classes}).
 */
abstract class AbstractAggregatorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
    protected List<MavenProject> reactorProjects;

    /** For an aggregator mojo this is the top-level project (repo/branch are derived from its basedir). */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(property = "backstage.skip", defaultValue = "false")
    protected boolean skip;

    @Parameter(property = "backstage.annotationPrefix", defaultValue = "spring-backstage.io")
    protected String annotationPrefix;

    @Parameter(property = "backstage.apiDefinitionRef", defaultValue = "./openapi.yaml")
    protected String apiDefinitionRef;

    @Parameter(property = "backstage.emitApi", defaultValue = "true")
    protected boolean emitApi;

    @Parameter(property = "backstage.sourceLocationBranch")
    protected String sourceLocationBranch;

    @Parameter(property = "backstage.repoBaseUrl")
    protected String repoBaseUrl;

    @Parameter(property = "backstage.inferConsumedApis", defaultValue = "false")
    protected boolean inferConsumedApis;

    @Parameter(property = "backstage.failOnDuplicateName", defaultValue = "true")
    protected boolean failOnDuplicateName;

    /** Tooling-annotation rules merged over the built-in defaults by key. */
    @Parameter
    protected List<ToolingRule> toolingAnnotations;

    /** Harbor project namespace for the {@code goharbor.io/repository-slug} convention. */
    @Parameter(property = "backstage.harborProject")
    protected String harborProject;

    /** SCM provider ({@code github}/{@code gitlab}) for the project-slug key; blank = auto-detect. */
    @Parameter(property = "backstage.scmProvider")
    protected String scmProvider;

    /** Maven {@code <scm>} URL, used as the last fallback for repo slug / source-location. */
    @Parameter(defaultValue = "${project.scm.url}", readonly = true)
    protected String scmUrl;

    protected RepoInfo discoverRepo() {
        return GitMetadata.discover(project.getBasedir().toPath(), sourceLocationBranch, repoBaseUrl, scmUrl)
                .orElse(null);
    }

    /** Run every reactor module through the shared generation path; collect the catalogued ones. */
    protected List<ModuleResult> collect(RepoInfo repo) throws MojoFailureException {
        GenConfig cfg = new GenConfig(annotationPrefix, apiDefinitionRef, emitApi, sourceLocationBranch,
                inferConsumedApis, toolingAnnotations, harborProject, scmProvider);
        List<ModuleResult> results = new ArrayList<>();
        for (MavenProject module : reactorProjects) {
            try {
                CatalogGeneration.forModule(module, repo, cfg, getLog()).ifPresent(results::add);
            } catch (GenerationException e) {
                throw new MojoFailureException(e.getMessage(), e);
            }
        }
        return results;
    }

    /** Fail (or warn) on duplicate entity names across modules, checked per kind (Component/API/Resource). */
    protected void enforceUniqueness(List<ModuleResult> results) throws MojoFailureException {
        List<NameUniqueness.Entry> entries = new ArrayList<>();
        for (ModuleResult r : results) {
            for (CatalogEntity e : r.entities) {
                entries.add(new NameUniqueness.Entry(e.kindName() + "/" + e.entityName(), r.project.getArtifactId()));
            }
        }
        Map<String, List<String>> dups = NameUniqueness.duplicates(entries);
        if (dups.isEmpty()) {
            return;
        }
        String message = NameUniqueness.describe(dups);
        if (failOnDuplicateName) {
            throw new MojoFailureException(message);
        }
        getLog().warn(message);
    }
}

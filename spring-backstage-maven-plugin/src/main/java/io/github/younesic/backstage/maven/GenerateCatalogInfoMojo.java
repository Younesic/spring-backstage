package io.github.younesic.backstage.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import io.github.younesic.backstage.core.derive.GitMetadata;
import io.github.younesic.backstage.core.derive.GitMetadata.RepoInfo;
import io.github.younesic.backstage.core.serialization.CatalogSerializer;
import io.github.younesic.backstage.core.tooling.ToolingRule;

/**
 * Per-module generation: writes a tool-owned {@code catalog-info.generated.yaml} for the current module
 * when it carries {@code @BackstageComponent}. Runs once per reactor module (bound to
 * {@code process-classes}); {@code pom} modules and unannotated modules are skipped. The
 * human-maintained {@code catalog-info.yaml} is never touched.
 *
 * <p>In a monorepo each module writes its own file in its own {@code basedir}; the recursive central
 * discovery glob picks them up — no aggregation required. Cross-module name uniqueness cannot be seen
 * from a single module: run the {@code check-catalog-names} goal in CI to enforce it.
 */
@Mojo(name = "generate-catalog-info",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
public class GenerateCatalogInfoMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Skip generation entirely. */
    @Parameter(property = "backstage.skip", defaultValue = "false")
    private boolean skip;

    /** DNS-style prefix for the sentinel and tool-owned annotations (e.g. {@code spring-backstage.io}). */
    @Parameter(property = "backstage.annotationPrefix", defaultValue = "spring-backstage.io")
    private String annotationPrefix;

    /**
     * Output path for the generated entity file, relative to the module base directory. Parent
     * directories are created as needed. Defaults to {@code catalog-info.generated.yaml} at the module
     * root (Mode 1). For Mode 2 (already-recursive {@code /**}{@code /catalog-info.yaml} glob), set a
     * sub-folder file, e.g. {@code catalog/catalog-info.yaml}.
     */
    @Parameter(property = "backstage.outputPath", defaultValue = "catalog-info.generated.yaml")
    private String outputPath;

    /** Relative {@code $text} reference written into the API entity's {@code spec.definition}. */
    @Parameter(property = "backstage.apiDefinitionRef", defaultValue = "./openapi.yaml")
    private String apiDefinitionRef;

    /** Global switch for the springdoc API synergy (combined with the annotation's {@code emitApi}). */
    @Parameter(property = "backstage.emitApi", defaultValue = "true")
    private boolean emitApi;

    /** Branch used in {@code source-location}; defaults to the current branch (HEAD), else {@code main}. */
    @Parameter(property = "backstage.sourceLocationBranch")
    private String sourceLocationBranch;

    /** Override for the repo base URL (e.g. {@code https://github.com/org/repo}) when git is unavailable. */
    @Parameter(property = "backstage.repoBaseUrl")
    private String repoBaseUrl;

    /** Reserved (Feign inference of {@code consumesApis}) — not yet implemented; default off. */
    @Parameter(property = "backstage.inferConsumedApis", defaultValue = "false")
    private boolean inferConsumedApis;

    /** Fail the build if this (non-pom) module has no {@code @BackstageComponent} (default: no-op skip). */
    @Parameter(property = "backstage.failOnMissingAnnotation", defaultValue = "false")
    private boolean failOnMissingAnnotation;

    /**
     * Tooling-annotation rules merged over the built-in defaults by key (add a tool, override a
     * convention/env source, or disable a default with {@code <enabled>false</enabled>}). Bound from
     * {@code <toolingAnnotations><toolingAnnotation>…}.
     */
    @Parameter
    private List<ToolingRule> toolingAnnotations;

    /** Harbor project namespace for the {@code goharbor.io/repository-slug} convention. */
    @Parameter(property = "backstage.harborProject")
    private String harborProject;

    /** SCM provider ({@code github}/{@code gitlab}) for the project-slug key; blank = auto-detect. */
    @Parameter(property = "backstage.scmProvider")
    private String scmProvider;

    /** Maven {@code <scm>} URL, used as the last fallback for repo slug / source-location. */
    @Parameter(defaultValue = "${project.scm.url}", readonly = true)
    private String scmUrl;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("backstage.skip=true — skipping catalog-info generation.");
            return;
        }
        if ("pom".equals(project.getPackaging())) {
            getLog().info("Backstage: skipping pom module '" + project.getArtifactId() + "'.");
            return;
        }

        Path basedir = project.getBasedir().toPath();
        RepoInfo repo = GitMetadata.discover(basedir, sourceLocationBranch, repoBaseUrl, scmUrl).orElse(null);
        GenConfig cfg = new GenConfig(annotationPrefix, apiDefinitionRef, emitApi, sourceLocationBranch,
                inferConsumedApis, toolingAnnotations, harborProject, scmProvider);

        Optional<ModuleResult> result;
        try {
            result = CatalogGeneration.forModule(project, repo, cfg, getLog());
        } catch (GenerationException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }

        if (result.isEmpty()) {
            if (failOnMissingAnnotation) {
                throw new MojoFailureException("backstage.failOnMissingAnnotation=true but no @BackstageComponent"
                        + " was found in module '" + project.getArtifactId() + "'.");
            }
            return; // already logged by CatalogGeneration
        }

        writeOutput(basedir.resolve(outputPath),
                new CatalogSerializer().serialize(result.get().entities), result.get().entities.size());
    }

    private void writeOutput(Path output, String yaml, int count) throws MojoExecutionException {
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + output + ": " + e.getMessage(), e);
        }
        getLog().info("Backstage: wrote " + count + " entit" + (count == 1 ? "y" : "ies") + " to " + output);
    }
}

package io.github.younesic.backstage.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import io.github.younesic.backstage.core.build.CatalogBuilder;
import io.github.younesic.backstage.core.build.GenerationRequest;
import io.github.younesic.backstage.core.derive.GitMetadata;
import io.github.younesic.backstage.core.derive.GitMetadata.RepoInfo;
import io.github.younesic.backstage.core.derive.Names;
import io.github.younesic.backstage.core.derive.OpenApiDetector;
import io.github.younesic.backstage.core.model.CatalogEntity;
import io.github.younesic.backstage.core.tooling.CiEnvironment;
import io.github.younesic.backstage.core.tooling.ToolingResolver;

/**
 * Single code path shared by the per-module mojo and the aggregate/check aggregator mojos: given one
 * Maven module, produce its Backstage entities (Component + optional API). Returns empty — never
 * throws — for the silent-skip cases (pom packaging, no compiled classes, no annotation).
 */
final class CatalogGeneration {

    private CatalogGeneration() {
    }

    /**
     * @return the module's entities, or empty when the module is intentionally not catalogued.
     * @throws GenerationException for actionable errors (multiple components, blank owner/lifecycle).
     */
    static Optional<ModuleResult> forModule(MavenProject module, RepoInfo repo, GenConfig cfg, Log log)
            throws GenerationException {

        if ("pom".equals(module.getPackaging())) {
            // Parent / BOM / aggregator: never catalogued, even if annotated by mistake.
            if (Files.isDirectory(Path.of(module.getBuild().getOutputDirectory()))
                    && ModuleScanner.scan(Path.of(module.getBuild().getOutputDirectory())).isPresent()) {
                log.warn("Backstage: module '" + module.getArtifactId()
                        + "' has packaging=pom but carries @BackstageComponent — skipped (pom modules are never catalogued).");
            } else {
                log.info("Backstage: skipping pom module '" + module.getArtifactId() + "'.");
            }
            return Optional.empty();
        }

        Path outputDir = Path.of(module.getBuild().getOutputDirectory());
        Optional<AnnotatedComponent> declaredOpt = ModuleScanner.scan(outputDir);
        if (declaredOpt.isEmpty()) {
            String reason = Files.isDirectory(outputDir)
                    ? "no @BackstageComponent in " + module.getArtifactId()
                    : "no compiled classes for " + module.getArtifactId() + " (run a build first)";
            log.info("Backstage: " + reason + " — nothing to generate.");
            return Optional.empty();
        }

        AnnotatedComponent declared = declaredOpt.get();
        requireGovernance(declared);

        String name = resolveName(declared.name, module);
        boolean springdoc = OpenApiDetector.isSpringdocPresent(coordinates(module));
        boolean willEmitApi = declared.emitApi && cfg.emitApi;
        Path basedir = module.getBasedir().toPath();

        // Read the CI environment once; reuse it for both source-location and the tooling registry.
        CiEnvironment ci = CiEnvironment.detect(System.getenv());
        // On a detached-HEAD CI checkout the git branch is unknown — fall back to the CI ref before main.
        String branchFallback = notBlank(cfg.sourceLocationBranch) ? cfg.sourceLocationBranch : ci.branch();
        String sourceLocation = repo != null
                ? GitMetadata.sourceLocation(repo, basedir, branchFallback) : null;

        if (cfg.inferConsumedApis) {
            log.warn("Backstage: inferConsumedApis (Feign inference) is reserved and not yet implemented; ignoring.");
        }

        Map<String, String> tooling = resolveTooling(module, repo, declared, cfg, ci, log);

        int resourceCount = declared.resources == null ? 0 : declared.resources.size();
        log.info("Backstage: component '" + name + "' owner=" + declared.owner
                + " lifecycle=" + declared.lifecycle.toLowerCase(Locale.ROOT)
                + " springdoc=" + springdoc + (springdoc && willEmitApi ? " (+API)" : "")
                + (resourceCount > 0 ? " (+" + resourceCount + " resource" + (resourceCount == 1 ? "" : "s") + ")" : "")
                + (sourceLocation != null ? " @ " + sourceLocation : ""));

        GenerationRequest request = GenerationRequest.builder()
                .annotationPrefix(cfg.annotationPrefix)
                .name(name)
                .type(declared.type)
                .owner(declared.owner)
                .lifecycle(declared.lifecycle.toLowerCase(Locale.ROOT))
                .system(declared.system)
                .description(declared.description)
                .tags(Names.normalizeTags(declared.tags))
                .dependsOn(cleanRefs(declared.dependsOn))
                .consumesApis(Arrays.asList(declared.consumesApis))
                .providesApis(Arrays.asList(declared.providesApis))
                .resources(declared.resources == null ? List.of() : declared.resources)
                .emitApi(willEmitApi)
                .springdocPresent(springdoc)
                .apiDefinitionRef(cfg.apiDefinitionRef)
                .projectSlug(repo != null ? repo.projectSlug : null)
                .sourceLocation(sourceLocation)
                .techDocsRef(techDocsRef(basedir))
                .buildVersion(module.getVersion())
                .toolingAnnotations(tooling)
                .build();

        try {
            List<CatalogEntity> entities = new CatalogBuilder().build(request);
            return Optional.of(new ModuleResult(name, entities, module));
        } catch (IllegalArgumentException e) {
            throw new GenerationException(e.getMessage(), e);
        }
    }

    /**
     * Resolve the component-scoped tooling/integration annotations from the registry: explicit
     * {@code @BackstageComponent.annotations} override &gt; CI environment variable &gt; org convention.
     * Reads the live process environment ({@code System.getenv()}) once; degrades gracefully (a key with
     * no source is omitted, never an error).
     */
    private static Map<String, String> resolveTooling(MavenProject module, RepoInfo repo,
            AnnotatedComponent declared, GenConfig cfg, CiEnvironment ci, Log log) {
        String branch = (repo != null && notBlank(repo.branch)) ? repo.branch : ci.branch();
        String harborProject = notBlank(cfg.harborProject) ? cfg.harborProject : lastSegment(module.getGroupId());

        Map<String, String> vars = new LinkedHashMap<>();
        putIfPresent(vars, "groupId", module.getGroupId());
        putIfPresent(vars, "artifactId", module.getArtifactId());
        putIfPresent(vars, "version", module.getVersion());
        putIfPresent(vars, "branch", branch);
        putIfPresent(vars, "harborProject", harborProject);

        ToolingResolver.Warn warn = msg -> log.warn("Backstage: " + msg);
        Map<String, String> overrides = ToolingResolver.parseOverrides(declared.annotations, warn);
        return ToolingResolver.resolve(cfg.toolingRules, overrides, ci, vars, warn);
    }

    private static String lastSegment(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return null;
        }
        int dot = groupId.lastIndexOf('.');
        return dot >= 0 && dot < groupId.length() - 1 ? groupId.substring(dot + 1) : groupId;
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            map.put(key, value.trim());
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static void requireGovernance(AnnotatedComponent declared) throws GenerationException {
        if (declared.owner.isBlank()) {
            throw new GenerationException(
                    "@BackstageComponent.owner is required but missing/blank on " + declared.className
                    + ". Set owner=\"team-...\" (a Backstage Group reference).");
        }
        if (declared.lifecycle == null || declared.lifecycle.isBlank()) {
            throw new GenerationException(
                    "@BackstageComponent.lifecycle is required but missing on " + declared.className
                    + ". Set lifecycle=Lifecycle.PRODUCTION|EXPERIMENTAL|DEPRECATED.");
        }
    }

    /** Per decision: annotation {@code name} → module {@code artifactId} (normalized). No spring.application.name. */
    private static String resolveName(String annotationName, MavenProject module) {
        String source = (annotationName != null && !annotationName.isBlank())
                ? annotationName : module.getArtifactId();
        return Names.normalizeName(source);
    }

    private static String techDocsRef(Path basedir) {
        boolean hasMkdocs = Files.isRegularFile(basedir.resolve("mkdocs.yml"))
                || Files.isRegularFile(basedir.resolve("mkdocs.yaml"));
        return hasMkdocs ? "dir:." : null;
    }

    /** Resolved artifacts (incl. transitive) when available, else declared direct dependencies. */
    private static Set<String> coordinates(MavenProject module) {
        Set<String> coords = new LinkedHashSet<>();
        Set<Artifact> artifacts = module.getArtifacts();
        if (artifacts != null && !artifacts.isEmpty()) {
            for (Artifact a : artifacts) {
                coords.add(a.getGroupId() + ":" + a.getArtifactId());
            }
        } else {
            for (Dependency d : module.getDependencies()) {
                coords.add(d.getGroupId() + ":" + d.getArtifactId());
            }
        }
        return coords;
    }

    /** Declared direct dependencies as {@code groupId:artifactId} — used for inter-module inference. */
    static Set<String> directDependencyKeys(MavenProject module) {
        Set<String> keys = new LinkedHashSet<>();
        for (Dependency d : module.getDependencies()) {
            keys.add(d.getGroupId() + ":" + d.getArtifactId());
        }
        return keys;
    }

    private static List<String> cleanRefs(String[] refs) {
        return Arrays.stream(refs).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
}

package io.github.younesic.backstage.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import io.github.younesic.backstage.core.build.DependencyInference;
import io.github.younesic.backstage.core.derive.GitMetadata.RepoInfo;
import io.github.younesic.backstage.core.model.CatalogEntity;
import io.github.younesic.backstage.core.model.ComponentEntity;
import io.github.younesic.backstage.core.serialization.CatalogSerializer;

/**
 * Aggregate generation: scans the whole reactor and writes a single multi-document
 * {@code catalog-info.generated.yaml} at the repo root — for setups whose central discovery is
 * root-only (non-recursive). Runs once ({@code aggregator = true}). Modules must already be compiled,
 * so invoke it after a build, e.g. {@code mvn -DskipTests package} then
 * {@code mvn spring-backstage:generate-catalog-info-aggregate}.
 */
@Mojo(name = "generate-catalog-info-aggregate",
        aggregator = true,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
public class GenerateCatalogInfoAggregateMojo extends AbstractAggregatorMojo {

    /** Output file, relative to the top-level project base directory. */
    @Parameter(property = "backstage.aggregateOutputPath", defaultValue = "catalog-info.generated.yaml")
    private String aggregateOutputPath;

    /** Infer {@code spec.dependsOn} from inter-module Maven dependencies between annotated modules. */
    @Parameter(property = "backstage.inferDependencies", defaultValue = "false")
    private boolean inferDependencies;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("backstage.skip=true — skipping aggregate generation.");
            return;
        }

        RepoInfo repo = discoverRepo();
        List<ModuleResult> results = collect(repo);
        if (results.isEmpty()) {
            getLog().info("Backstage: no annotated modules in the reactor — nothing to aggregate.");
            return;
        }

        enforceUniqueness(results);

        if (inferDependencies) {
            inferDependsOn(results);
        }

        List<CatalogEntity> all = results.stream()
                .flatMap(r -> r.entities.stream())
                .collect(Collectors.toList());

        Path output = project.getBasedir().toPath().resolve(aggregateOutputPath);
        write(output, new CatalogSerializer().serialize(all), all.size(), results.size());
    }

    private void inferDependsOn(List<ModuleResult> results) {
        Map<String, String> keyToName = new HashMap<>();
        for (ModuleResult r : results) {
            keyToName.put(r.artifactKey(), r.componentName);
        }
        for (ModuleResult r : results) {
            Set<String> depKeys = CatalogGeneration.directDependencyKeys(r.project);
            List<String> inferred = DependencyInference.refs(depKeys, keyToName, r.componentName);
            if (inferred.isEmpty()) {
                continue;
            }
            for (CatalogEntity e : r.entities) {
                if (e instanceof ComponentEntity) {
                    ComponentEntity c = (ComponentEntity) e;
                    List<String> merged = merge(c.spec.dependsOn, inferred);
                    c.spec.dependsOn = merged.isEmpty() ? null : merged;
                    getLog().info("Backstage: '" + r.componentName + "' dependsOn " + merged);
                }
            }
        }
    }

    /** Union of declared + inferred dependsOn, preserving declared order first, de-duplicated. */
    private static List<String> merge(List<String> declared, List<String> inferred) {
        List<String> out = new ArrayList<>();
        if (declared != null) {
            out.addAll(declared);
        }
        for (String ref : inferred) {
            if (!out.contains(ref)) {
                out.add(ref);
            }
        }
        return out;
    }

    private void write(Path output, String yaml, int entityCount, int moduleCount) throws MojoExecutionException {
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + output + ": " + e.getMessage(), e);
        }
        getLog().info("Backstage: wrote " + entityCount + " entit" + (entityCount == 1 ? "y" : "ies")
                + " from " + moduleCount + " module(s) to " + output);
    }
}

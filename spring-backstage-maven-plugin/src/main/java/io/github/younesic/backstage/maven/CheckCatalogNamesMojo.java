package io.github.younesic.backstage.maven;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import io.github.younesic.backstage.core.derive.GitMetadata.RepoInfo;

/**
 * Validation-only aggregator goal for per-module setups: scans the whole reactor and fails the build
 * if two modules would generate the same Component name. Writes nothing — ideal as a CI gate where
 * each module generates its own file (and so cannot see cross-module collisions on its own).
 *
 * <p>Modules must already be compiled. Run e.g. {@code mvn -DskipTests package spring-backstage:check-catalog-names}
 * (the per-module goal also runs, then this validates), or after a build as a standalone goal.
 */
@Mojo(name = "check-catalog-names",
        aggregator = true,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
public class CheckCatalogNamesMojo extends AbstractAggregatorMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("backstage.skip=true — skipping catalog name check.");
            return;
        }

        RepoInfo repo = discoverRepo();
        List<ModuleResult> results = collect(repo);
        enforceUniqueness(results);
        getLog().info("Backstage: " + results.size() + " catalogued module(s); all Component names are unique.");
    }
}

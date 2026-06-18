package io.github.younesic.backstage.maven;

import java.util.List;

import org.apache.maven.project.MavenProject;

import io.github.younesic.backstage.core.model.CatalogEntity;

/** Entities generated for one module, plus the module that produced them (for collision messages). */
final class ModuleResult {
    final String componentName;
    final List<CatalogEntity> entities;
    final MavenProject project;

    ModuleResult(String componentName, List<CatalogEntity> entities, MavenProject project) {
        this.componentName = componentName;
        this.entities = entities;
        this.project = project;
    }

    /** {@code groupId:artifactId} — the Maven coordinate key for inter-module dependency inference. */
    String artifactKey() {
        return project.getGroupId() + ":" + project.getArtifactId();
    }
}

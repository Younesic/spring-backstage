package io.github.younesic.backstage.core.build;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, fully-resolved input to {@link CatalogBuilder}. The caller (e.g. the Maven mojo) has
 * already merged annotation values with derived values; {@code name} is the final normalized name.
 * Optional fields may be {@code null}; {@code tags}/{@code dependsOn} are never {@code null}.
 */
public final class GenerationRequest {

    public final String annotationPrefix;
    public final String name;
    public final String type;
    public final String owner;
    public final String lifecycle;
    public final String system;
    public final String description;
    public final List<String> tags;
    public final List<String> dependsOn;
    public final List<String> consumesApis;
    public final List<String> providesApis;
    public final List<ResourceRequest> resources;
    public final boolean emitApi;
    public final boolean springdocPresent;
    public final String apiDefinitionRef;
    public final String projectSlug;
    /** Annotation key for the slug, e.g. {@code github.com/project-slug} or {@code gitlab.com/project-slug}. */
    public final String projectSlugKey;
    public final String sourceLocation;
    public final String techDocsRef;
    public final String buildVersion;
    public final String gitSha;
    /** Already-resolved tooling/integration annotations (component-scoped); emitted verbatim, ordered. */
    public final Map<String, String> toolingAnnotations;

    private GenerationRequest(Builder b) {
        this.annotationPrefix = b.annotationPrefix;
        this.name = b.name;
        this.type = b.type;
        this.owner = b.owner;
        this.lifecycle = b.lifecycle;
        this.system = b.system;
        this.description = b.description;
        this.tags = List.copyOf(b.tags == null ? List.of() : b.tags);
        this.dependsOn = List.copyOf(b.dependsOn == null ? List.of() : b.dependsOn);
        this.consumesApis = List.copyOf(b.consumesApis == null ? List.of() : b.consumesApis);
        this.providesApis = List.copyOf(b.providesApis == null ? List.of() : b.providesApis);
        this.resources = List.copyOf(b.resources == null ? List.of() : b.resources);
        this.emitApi = b.emitApi;
        this.springdocPresent = b.springdocPresent;
        this.apiDefinitionRef = b.apiDefinitionRef;
        this.projectSlug = b.projectSlug;
        this.projectSlugKey = (b.projectSlugKey == null || b.projectSlugKey.isBlank())
                ? "github.com/project-slug" : b.projectSlugKey;
        this.sourceLocation = b.sourceLocation;
        this.techDocsRef = b.techDocsRef;
        this.buildVersion = b.buildVersion;
        this.gitSha = b.gitSha;
        // Preserve insertion order (LinkedHashMap-backed) so output stays byte-idempotent.
        this.toolingAnnotations = b.toolingAnnotations == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(b.toolingAnnotations));
    }

    /** An API entity is emitted only when explicitly requested AND springdoc is on the build. */
    public boolean shouldEmitApi() {
        return emitApi && springdocPresent;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String annotationPrefix = "spring-backstage.io";
        private String name;
        private String type = "service";
        private String owner;
        private String lifecycle;
        private String system;
        private String description;
        private List<String> tags = List.of();
        private List<String> dependsOn = List.of();
        private List<String> consumesApis = List.of();
        private List<String> providesApis = List.of();
        private List<ResourceRequest> resources = List.of();
        private boolean emitApi = true;
        private boolean springdocPresent;
        private String apiDefinitionRef = "./openapi.yaml";
        private String projectSlug;
        private String projectSlugKey = "github.com/project-slug";
        private String sourceLocation;
        private String techDocsRef;
        private String buildVersion;
        private String gitSha;
        private Map<String, String> toolingAnnotations = Map.of();

        public Builder annotationPrefix(String v) { this.annotationPrefix = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder type(String v) { this.type = v; return this; }
        public Builder owner(String v) { this.owner = v; return this; }
        public Builder lifecycle(String v) { this.lifecycle = v; return this; }
        public Builder system(String v) { this.system = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder tags(List<String> v) { this.tags = v; return this; }
        public Builder dependsOn(List<String> v) { this.dependsOn = v; return this; }
        public Builder consumesApis(List<String> v) { this.consumesApis = v; return this; }
        public Builder providesApis(List<String> v) { this.providesApis = v; return this; }
        public Builder resources(List<ResourceRequest> v) { this.resources = v; return this; }
        public Builder emitApi(boolean v) { this.emitApi = v; return this; }
        public Builder springdocPresent(boolean v) { this.springdocPresent = v; return this; }
        public Builder apiDefinitionRef(String v) { this.apiDefinitionRef = v; return this; }
        public Builder projectSlug(String v) { this.projectSlug = v; return this; }
        public Builder projectSlugKey(String v) { this.projectSlugKey = v; return this; }
        public Builder sourceLocation(String v) { this.sourceLocation = v; return this; }
        public Builder techDocsRef(String v) { this.techDocsRef = v; return this; }
        public Builder buildVersion(String v) { this.buildVersion = v; return this; }
        public Builder gitSha(String v) { this.gitSha = v; return this; }
        public Builder toolingAnnotations(Map<String, String> v) { this.toolingAnnotations = v; return this; }

        public GenerationRequest build() {
            return new GenerationRequest(this);
        }
    }
}

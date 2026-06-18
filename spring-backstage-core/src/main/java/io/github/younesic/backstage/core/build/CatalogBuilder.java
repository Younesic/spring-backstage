package io.github.younesic.backstage.core.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.younesic.backstage.core.derive.Names;
import io.github.younesic.backstage.core.model.ApiDefinition;
import io.github.younesic.backstage.core.model.ApiEntity;
import io.github.younesic.backstage.core.model.ApiSpec;
import io.github.younesic.backstage.core.model.CatalogEntity;
import io.github.younesic.backstage.core.model.ComponentEntity;
import io.github.younesic.backstage.core.model.ComponentSpec;
import io.github.younesic.backstage.core.model.ObjectMeta;
import io.github.younesic.backstage.core.model.ResourceEntity;
import io.github.younesic.backstage.core.model.ResourceSpec;

/**
 * Assembles Backstage {@code Component} (and optional {@code API} / owned {@code Resource}) entities
 * from a fully-resolved {@link GenerationRequest}. Deterministic: same request → byte-identical output
 * (no timestamps), so the generated file is idempotent across rebuilds.
 *
 * <p>Source-of-truth rule: this only emits what the code owns — the Component, the API it exposes, and
 * Resources explicitly declared as service-owned. {@code consumesApis} and {@code dependsOn} are
 * <em>references</em> to entities owned elsewhere and are validated for format only, never created.
 */
public final class CatalogBuilder {

    private static final String OPENAPI = "openapi";

    /** Build the ordered entity list: Component first, then API, then owned Resources. */
    public List<CatalogEntity> build(GenerationRequest req) {
        validate(req);

        ComponentEntity component = new ComponentEntity();
        component.metadata = componentMeta(req);
        component.spec = componentSpec(req);

        List<CatalogEntity> entities = new ArrayList<>();
        entities.add(component);

        // providesApis: springdoc-derived (bare name) + explicit annotation entries, de-duplicated.
        List<String> provides = new ArrayList<>();
        if (req.shouldEmitApi()) {
            String apiName = req.name + "-api";
            provides.add(apiName);
            ApiEntity api = new ApiEntity();
            api.metadata = apiMeta(req, apiName);
            api.spec = apiSpec(req);
            entities.add(api);
        }
        addDistinctTrimmed(provides, req.providesApis);
        component.spec.providesApis = nullIfEmpty(provides);

        // consumesApis: reference only, format-validated, never a generated API.
        component.spec.consumesApis = nullIfEmpty(qualifyAll(req.consumesApis, "api"));

        // dependsOn: declared references (validated) + auto-wired refs to owned Resources.
        List<String> dependsOn = qualifyAll(req.dependsOn, "component");
        Set<String> seenResourceNames = new LinkedHashSet<>();
        for (ResourceRequest r : req.resources) {
            ResourceEntity resource = buildResource(req, r, seenResourceNames);
            entities.add(resource);
            String ref = Names.qualifyRef(resource.metadata.name, "resource");
            if (!dependsOn.contains(ref)) {
                dependsOn.add(ref);
            }
        }
        component.spec.dependsOn = nullIfEmpty(dependsOn);

        return entities;
    }

    private static void validate(GenerationRequest req) {
        requireText(req.annotationPrefix, "annotationPrefix");
        requireText(req.name, "metadata.name");
        requireText(req.owner, "spec.owner (@BackstageComponent.owner)");
        requireText(req.lifecycle, "spec.lifecycle (@BackstageComponent.lifecycle)");
        if (req.shouldEmitApi()) {
            requireText(req.apiDefinitionRef, "apiDefinitionRef");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Backstage catalog generation failed: required field '" + field + "' is missing or blank.");
        }
    }

    private ResourceEntity buildResource(GenerationRequest req, ResourceRequest r, Set<String> seenNames) {
        requireText(r.name, "@BackstageResource.name");
        String name = Names.normalizeName(r.name);
        if (!seenNames.add(name)) {
            throw new IllegalArgumentException(
                    "Duplicate @BackstageResource name '" + name + "' on the same component.");
        }
        requireText(r.type, "@BackstageResource.type (resource '" + name + "')");

        ResourceEntity resource = new ResourceEntity();
        ObjectMeta meta = new ObjectMeta();
        meta.name = name;
        meta.description = trimToNull(r.description);
        meta.annotations = annotations(req, false, false);
        resource.metadata = meta;

        ResourceSpec spec = new ResourceSpec();
        spec.type = r.type.trim();
        // Inherit owner/system from the Component unless explicitly overridden (no lifecycle on Resource).
        spec.owner = Names.qualifyOwner(notBlank(r.owner) ? r.owner : req.owner);
        spec.system = trimToNull(notBlank(r.system) ? r.system : req.system);
        resource.spec = spec;
        return resource;
    }

    private ObjectMeta componentMeta(GenerationRequest req) {
        ObjectMeta meta = new ObjectMeta();
        meta.name = req.name;
        meta.description = trimToNull(req.description);
        meta.tags = req.tags.isEmpty() ? null : new ArrayList<>(req.tags);
        meta.annotations = annotations(req, true, true);
        return meta;
    }

    private ObjectMeta apiMeta(GenerationRequest req, String apiName) {
        ObjectMeta meta = new ObjectMeta();
        meta.name = apiName;
        meta.annotations = annotations(req, false, false);
        return meta;
    }

    /**
     * Ordered annotation map. {@code includeTechDocs} and {@code includeTooling} apply to the Component
     * only — tooling/integration annotations (SonarQube, ArgoCD, …) are component-scoped, so they are
     * never copied onto the derived API or owned Resource entities.
     */
    private Map<String, String> annotations(GenerationRequest req, boolean includeTechDocs, boolean includeTooling) {
        Map<String, String> a = new LinkedHashMap<>();
        putIfPresent(a, "github.com/project-slug", req.projectSlug);
        putIfPresent(a, "backstage.io/source-location", req.sourceLocation);
        if (includeTechDocs) {
            putIfPresent(a, "backstage.io/techdocs-ref", req.techDocsRef);
        }
        // Sentinel: marks every emitted entity as exclusively owned by this generator.
        a.put(req.annotationPrefix + "/generated", "true");
        // Stable build provenance (no timestamp -> preserves idempotence).
        putIfPresent(a, req.annotationPrefix + "/build-version", req.buildVersion);
        putIfPresent(a, req.annotationPrefix + "/build-git-sha", req.gitSha);
        // Derived tooling/integration annotations (already resolved + ordered; values quoted as strings).
        if (includeTooling) {
            for (Map.Entry<String, String> e : req.toolingAnnotations.entrySet()) {
                putIfPresent(a, e.getKey(), e.getValue());
            }
        }
        return a;
    }

    private ComponentSpec componentSpec(GenerationRequest req) {
        ComponentSpec spec = new ComponentSpec();
        spec.type = req.type;
        spec.lifecycle = req.lifecycle;
        spec.owner = Names.qualifyOwner(req.owner);
        spec.system = trimToNull(req.system);
        return spec;
    }

    private ApiSpec apiSpec(GenerationRequest req) {
        ApiSpec spec = new ApiSpec();
        spec.type = OPENAPI;
        spec.lifecycle = req.lifecycle;
        spec.owner = Names.qualifyOwner(req.owner);
        spec.system = trimToNull(req.system);
        spec.definition = ApiDefinition.textRef(req.apiDefinitionRef);
        return spec;
    }

    /** Validate + normalize each ref to {@code kind:namespace/name}, de-duplicated, order preserved. */
    private static List<String> qualifyAll(List<String> refs, String defaultKind) {
        List<String> out = new ArrayList<>();
        for (String r : refs) {
            if (notBlank(r)) {
                String q = Names.qualifyRef(r, defaultKind);
                if (!out.contains(q)) {
                    out.add(q);
                }
            }
        }
        return out;
    }

    private static void addDistinctTrimmed(List<String> target, List<String> extra) {
        for (String e : extra) {
            if (notBlank(e)) {
                String t = e.trim();
                if (!target.contains(t)) {
                    target.add(t);
                }
            }
        }
    }

    private static List<String> nullIfEmpty(List<String> list) {
        return list.isEmpty() ? null : list;
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        String v = trimToNull(value);
        if (v != null) {
            map.put(key, v);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

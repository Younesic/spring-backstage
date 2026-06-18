package io.github.younesic.backstage.core.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.younesic.backstage.core.model.CatalogEntity;
import io.github.younesic.backstage.core.model.ComponentEntity;
import io.github.younesic.backstage.core.model.ResourceEntity;

class RelationsAndResourcesTest {

    private final CatalogBuilder builder = new CatalogBuilder();

    private GenerationRequest.Builder base() {
        return GenerationRequest.builder()
                .name("orders").owner("team-payments").lifecycle("production").system("checkout");
    }

    @Test
    void consumesApisAreReferencedNeverCreated() {
        List<CatalogEntity> e = builder.build(base()
                .consumesApis(List.of("api:default/payments", "inventory")).build());
        assertEquals(1, e.size(), "no API entity is created for consumed APIs");
        ComponentEntity c = (ComponentEntity) e.get(0);
        assertEquals(List.of("api:default/payments", "api:default/inventory"), c.spec.consumesApis);
    }

    @Test
    void providesApisMergeSpringdocAndExplicitDeduped() {
        ComponentEntity c = (ComponentEntity) builder.build(base()
                .springdocPresent(true).emitApi(true)
                .providesApis(List.of("orders-events", "orders-api")).build()).get(0);
        assertEquals(List.of("orders-api", "orders-events"), c.spec.providesApis);
    }

    @Test
    void ownedResourceIsEmittedWiredAndInherits() {
        List<CatalogEntity> e = builder.build(base()
                .resources(List.of(new ResourceRequest("orders-db", "database", null, null, null))).build());
        assertEquals(2, e.size());

        ComponentEntity c = (ComponentEntity) e.get(0);
        ResourceEntity r = (ResourceEntity) e.get(1);
        assertEquals("Resource", r.kind);
        assertEquals("orders-db", r.metadata.name);
        assertEquals("database", r.spec.type);
        assertEquals("group:default/team-payments", r.spec.owner);   // inherited from Component
        assertEquals("checkout", r.spec.system);                     // inherited from Component
        assertEquals("true", r.metadata.annotations.get("spring-backstage.io/generated")); // sentinel
        assertEquals(List.of("resource:default/orders-db"), c.spec.dependsOn); // auto-wired
    }

    @Test
    void resourceOwnerAndSystemCanBeOverridden() {
        ResourceEntity r = (ResourceEntity) builder.build(base()
                .resources(List.of(new ResourceRequest("orders-db", "database", "team-data", "data-platform", null)))
                .build()).get(1);
        assertEquals("group:default/team-data", r.spec.owner);
        assertEquals("data-platform", r.spec.system);
    }

    @Test
    void dependsOnMergesDeclaredRefsAndOwnedResources() {
        ComponentEntity c = (ComponentEntity) builder.build(base()
                .dependsOn(List.of("component:default/inventory"))
                .resources(List.of(new ResourceRequest("orders-db", "database", null, null, null)))
                .build()).get(0);
        assertEquals(List.of("component:default/inventory", "resource:default/orders-db"), c.spec.dependsOn);
    }

    @Test
    void duplicateResourceNameOnSameComponentFails() {
        GenerationRequest req = base().resources(List.of(
                new ResourceRequest("db", "database", null, null, null),
                new ResourceRequest("db", "queue", null, null, null))).build();
        assertThrows(IllegalArgumentException.class, () -> builder.build(req));
    }

    @Test
    void noResourcesByDefault() {
        List<CatalogEntity> e = builder.build(base().build());
        assertEquals(1, e.size(), "opt-in: no @BackstageResource → no Resource emitted");
        assertNull(((ComponentEntity) e.get(0)).spec.dependsOn);
    }
}

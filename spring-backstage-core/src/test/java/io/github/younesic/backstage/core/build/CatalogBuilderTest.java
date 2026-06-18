package io.github.younesic.backstage.core.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.younesic.backstage.core.model.ApiDefinition;
import io.github.younesic.backstage.core.model.ApiEntity;
import io.github.younesic.backstage.core.model.CatalogEntity;
import io.github.younesic.backstage.core.model.ComponentEntity;

class CatalogBuilderTest {

    private final CatalogBuilder builder = new CatalogBuilder();

    private GenerationRequest.Builder base() {
        return GenerationRequest.builder()
                .name("orders-service")
                .owner("team-payments")
                .lifecycle("production");
    }

    @Test
    void buildsComponentOnlyWhenNoSpringdoc() {
        List<CatalogEntity> entities = builder.build(base().springdocPresent(false).build());
        assertEquals(1, entities.size());

        ComponentEntity c = (ComponentEntity) entities.get(0);
        assertEquals("backstage.io/v1alpha1", c.apiVersion);
        assertEquals("Component", c.kind);
        assertEquals("orders-service", c.metadata.name);
        assertEquals("service", c.spec.type);
        assertEquals("production", c.spec.lifecycle);
        assertEquals("group:default/team-payments", c.spec.owner);
        assertEquals("true", c.metadata.annotations.get("spring-backstage.io/generated"));
        assertNull(c.spec.providesApis);
    }

    @Test
    void buildsApiAndLinksWhenSpringdocPresentAndEmit() {
        List<CatalogEntity> entities = builder.build(
                base().springdocPresent(true).emitApi(true).system("checkout").build());
        assertEquals(2, entities.size());

        ComponentEntity c = (ComponentEntity) entities.get(0);
        ApiEntity api = (ApiEntity) entities.get(1);
        assertEquals(List.of("orders-service-api"), c.spec.providesApis);
        assertEquals("orders-service-api", api.metadata.name);
        assertEquals("openapi", api.spec.type);
        assertEquals("production", api.spec.lifecycle);
        assertEquals("group:default/team-payments", api.spec.owner);
        assertEquals("checkout", api.spec.system);
        assertEquals("./openapi.yaml", ((ApiDefinition) api.spec.definition).text);
    }

    @Test
    void noApiWhenEmitApiFalseEvenWithSpringdoc() {
        List<CatalogEntity> entities = builder.build(base().springdocPresent(true).emitApi(false).build());
        assertEquals(1, entities.size());
        assertNull(((ComponentEntity) entities.get(0)).spec.providesApis);
    }

    @Test
    void failsFastOnBlankOwner() {
        GenerationRequest req = base().owner("  ").build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> builder.build(req));
        assertTrue(ex.getMessage().contains("spec.owner"));
    }

    @Test
    void honoursCustomAnnotationPrefix() {
        List<CatalogEntity> entities = builder.build(base().annotationPrefix("backstage.kratix.io").build());
        ComponentEntity c = (ComponentEntity) entities.get(0);
        assertEquals("true", c.metadata.annotations.get("backstage.kratix.io/generated"));
    }

    @Test
    void emitsDerivedAnnotationsInStableOrder() {
        ComponentEntity c = (ComponentEntity) builder.build(base()
                .projectSlug("acme/orders")
                .sourceLocation("url:https://github.com/acme/orders/")
                .buildVersion("1.2.3")
                .build()).get(0);
        assertEquals(List.of(
                        "github.com/project-slug",
                        "backstage.io/source-location",
                        "spring-backstage.io/generated",
                        "spring-backstage.io/build-version"),
                List.copyOf(c.metadata.annotations.keySet()));
    }

    @Test
    void emitsToolingAnnotationsOnComponentOnlyNotApi() {
        Map<String, String> tooling = new LinkedHashMap<>();
        tooling.put("sonarqube.org/project-key", "acme_orders");
        tooling.put("argocd/app-name", "orders");

        List<CatalogEntity> entities = builder.build(base()
                .springdocPresent(true).emitApi(true)
                .toolingAnnotations(tooling).build());

        ComponentEntity c = (ComponentEntity) entities.get(0);
        ApiEntity api = (ApiEntity) entities.get(1);
        assertEquals("acme_orders", c.metadata.annotations.get("sonarqube.org/project-key"));
        assertEquals("orders", c.metadata.annotations.get("argocd/app-name"));
        assertNull(api.metadata.annotations.get("sonarqube.org/project-key"),
                "tooling annotations are component-scoped — never copied onto the API entity");
        assertNull(api.metadata.annotations.get("argocd/app-name"));
    }

    @Test
    void toolingAnnotationsFollowProvenanceInInsertionOrder() {
        Map<String, String> tooling = new LinkedHashMap<>();
        tooling.put("sonarqube.org/project-key", "acme_orders");
        tooling.put("argocd/app-name", "orders");

        ComponentEntity c = (ComponentEntity) builder.build(base()
                .projectSlug("acme/orders")
                .toolingAnnotations(tooling).build()).get(0);

        assertEquals(List.of(
                        "github.com/project-slug",
                        "spring-backstage.io/generated",
                        "sonarqube.org/project-key",
                        "argocd/app-name"),
                List.copyOf(c.metadata.annotations.keySet()));
    }
}

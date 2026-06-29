package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * End-to-end check that the build-time generation produced a valid, well-formed
 * {@code catalog-info.generated.yaml} and left the human {@code catalog-info.yaml} untouched.
 * The plugin runs at {@code process-classes}, so the file exists by the {@code test} phase.
 * Surefire's working directory is the module base directory.
 */
class CatalogGenerationTest {

    private static final Path GENERATED = Path.of("catalog-info.generated.yaml");
    private static final Path HUMAN = Path.of("catalog-info.yaml");

    private final YAMLMapper yaml = new YAMLMapper();

    private List<JsonNode> documents(Path p) throws Exception {
        try (MappingIterator<JsonNode> it = yaml.readerFor(JsonNode.class).readValues(Files.readString(p))) {
            return it.readAll();
        }
    }

    @Test
    void generatesComponentAndApi() throws Exception {
        assertTrue(Files.isRegularFile(GENERATED),
                "expected generated file at " + GENERATED.toAbsolutePath()
                + " — build via the reactor so the plugin executes at process-classes");

        List<JsonNode> docs = documents(GENERATED);
        assertEquals(4, docs.size(),
                "expected Component + API + 2 inferred Resources (postgresql, kafka)");

        JsonNode component = docs.get(0);
        assertEquals("backstage.io/v1alpha1", component.path("apiVersion").asText());
        assertEquals("Component", component.path("kind").asText());
        assertEquals("orders-service", component.path("metadata").path("name").asText());
        assertEquals("service", component.path("spec").path("type").asText());
        assertEquals("production", component.path("spec").path("lifecycle").asText());
        assertEquals("group:default/team-payments", component.path("spec").path("owner").asText());
        assertEquals("checkout", component.path("spec").path("system").asText());
        assertEquals("orders-service-api", component.path("spec").path("providesApis").get(0).asText());

        JsonNode sentinel = component.path("metadata").path("annotations").path("spring-backstage.io/generated");
        assertTrue(sentinel.isTextual(), "sentinel value must be a string, not a boolean");
        assertEquals("true", sentinel.asText());

        List<String> tags = new ArrayList<>();
        component.path("metadata").path("tags").forEach(n -> tags.add(n.asText()));
        assertTrue(tags.contains("java") && tags.contains("spring-boot"), "tags: " + tags);

        JsonNode api = docs.get(1);
        assertEquals("API", api.path("kind").asText());
        assertEquals("orders-service-api", api.path("metadata").path("name").asText());
        assertEquals("openapi", api.path("spec").path("type").asText());
        assertEquals("production", api.path("spec").path("lifecycle").asText());
        assertEquals("./openapi.yaml", api.path("spec").path("definition").path("$text").asText());
    }

    @Test
    void emitsDerivedToolingAnnotations() throws Exception {
        List<JsonNode> docs = documents(GENERATED);
        JsonNode ann = docs.get(0).path("metadata").path("annotations");

        // Convention-derived ({groupId}_{artifactId}); present unless a SONAR_PROJECT_KEY env overrides it.
        assertTrue(ann.path("sonarqube.org/project-key").asText().contains("orders-service"),
                "sonar key: " + ann.path("sonarqube.org/project-key").asText());
        // Explicit @BackstageComponent.annotations override wins over the {artifactId} convention.
        assertEquals("orders-checkout", ann.path("argocd/app-name").asText());
        // Harbor repository-slug convention {harborProject}/{artifactId}.
        assertTrue(ann.path("goharbor.io/repository-slug").asText().endsWith("/orders-service"),
                "harbor slug: " + ann.path("goharbor.io/repository-slug").asText());
        // Dependency-Track stable identifier (name + version) — no runtime UUID needed at build time.
        assertEquals("orders-service", ann.path("dependencytrack/project-name").asText());
        assertTrue(ann.path("dependencytrack/project-version").isTextual());
        // project-id is opt-in (DTRACK_PROJECT_ID) — absent by default.
        assertTrue(ann.path("dependencytrack/project-id").isMissingNode(),
                "project-id must stay absent unless DTRACK_PROJECT_ID is set");

        // Tooling annotations are component-scoped: never copied onto the API entity.
        JsonNode apiAnn = docs.get(1).path("metadata").path("annotations");
        assertTrue(apiAnn.path("argocd/app-name").isMissingNode(),
                "API entity must not carry component tooling annotations");
    }

    @Test
    void infersOwnedResourcesFromDepsAndConfig() throws Exception {
        List<JsonNode> docs = documents(GENERATED);

        // Component links to both inferred resources via dependsOn.
        List<String> dependsOn = new ArrayList<>();
        docs.get(0).path("spec").path("dependsOn").forEach(n -> dependsOn.add(n.asText()));
        assertTrue(dependsOn.contains("resource:default/orders-service-postgresql"), "dependsOn: " + dependsOn);
        assertTrue(dependsOn.contains("resource:default/orders-service-kafka"), "dependsOn: " + dependsOn);

        // The two Resource entities are emitted with the detected type and inherited owner.
        List<String> resourceTypes = new ArrayList<>();
        for (JsonNode d : docs) {
            if ("Resource".equals(d.path("kind").asText())) {
                resourceTypes.add(d.path("metadata").path("name").asText() + ":" + d.path("spec").path("type").asText());
                assertEquals("group:default/team-payments", d.path("spec").path("owner").asText(),
                        "inferred resource inherits the component owner");
            }
        }
        assertTrue(resourceTypes.contains("orders-service-postgresql:postgresql"), "resources: " + resourceTypes);
        assertTrue(resourceTypes.contains("orders-service-kafka:kafka"), "resources: " + resourceTypes);
    }

    @Test
    void humanFileIsNeverModified() throws Exception {
        String human = Files.readString(HUMAN);
        assertTrue(human.contains("kind: Group"), "human governance entities should be intact");
        assertFalse(human.contains("spring-backstage.io/generated"),
                "the generator must never write its sentinel into the human file");
    }

    @Test
    void generatorNeverEmitsALocation() throws Exception {
        String generated = Files.readString(GENERATED);
        assertFalse(generated.contains("kind: Location"),
                "Option B ingests via central discovery — the tool must never emit a Location");
    }

    @Test
    void containsNoNonDeterministicProvenance() throws Exception {
        String generated = Files.readString(GENERATED);
        assertFalse(generated.contains("build-timestamp"),
                "no timestamp must be emitted — it would break idempotence");
    }
}

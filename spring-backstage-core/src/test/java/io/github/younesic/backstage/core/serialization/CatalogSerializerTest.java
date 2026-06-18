package io.github.younesic.backstage.core.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.github.younesic.backstage.core.build.CatalogBuilder;
import io.github.younesic.backstage.core.build.GenerationRequest;
import io.github.younesic.backstage.core.model.CatalogEntity;

class CatalogSerializerTest {

    private final CatalogBuilder builder = new CatalogBuilder();
    private final CatalogSerializer serializer = new CatalogSerializer();
    private final YAMLMapper yaml = new YAMLMapper();

    private List<CatalogEntity> sample(boolean withApi) {
        return builder.build(GenerationRequest.builder()
                .name("orders-service").owner("team-payments").lifecycle("production")
                .system("checkout").tags(List.of("java"))
                .springdocPresent(withApi).emitApi(withApi)
                .projectSlug("acme/orders").sourceLocation("url:https://github.com/acme/orders/")
                .build());
    }

    @Test
    void multiDocStartsWithContentAndHasOneSeparator() {
        String out = serializer.serialize(sample(true));
        assertFalse(out.startsWith("---"), "file must start with content, not a document marker");
        long separators = out.lines().filter("---"::equals).count();
        assertEquals(1, separators, "exactly one '---' between the Component and the API document");
    }

    @Test
    void annotationBooleanValueStaysAString() throws Exception {
        String out = serializer.serialize(sample(false));
        JsonNode root = yaml.readTree(out);
        JsonNode v = root.path("metadata").path("annotations").path("spring-backstage.io/generated");
        assertTrue(v.isTextual(), "annotation value 'true' must remain a quoted string, got node: " + v);
        assertEquals("true", v.asText());
    }

    @Test
    void numericLookingAnnotationValuesStayStrings() throws Exception {
        // Without ALWAYS_QUOTE_NUMBERS_AS_STRINGS, '1.0' would round-trip as a YAML float and '123' as an
        // int — failing Backstage's string-only annotation validation.
        Map<String, String> tooling = new LinkedHashMap<>();
        tooling.put("dependencytrack/project-version", "1.0");
        tooling.put("some.tool/build-number", "123");
        List<CatalogEntity> entities = builder.build(GenerationRequest.builder()
                .name("orders-service").owner("team-payments").lifecycle("production")
                .toolingAnnotations(tooling).build());

        JsonNode ann = yaml.readTree(serializer.serialize(entities)).path("metadata").path("annotations");
        assertTrue(ann.path("dependencytrack/project-version").isTextual(),
                "numeric-looking version must remain a string, got: " + ann.path("dependencytrack/project-version"));
        assertEquals("1.0", ann.path("dependencytrack/project-version").asText());
        assertTrue(ann.path("some.tool/build-number").isTextual());
        assertEquals("123", ann.path("some.tool/build-number").asText());
    }

    @Test
    void emitsRequiredComponentFields() throws Exception {
        JsonNode root = yaml.readTree(serializer.serialize(sample(false)));
        assertEquals("backstage.io/v1alpha1", root.path("apiVersion").asText());
        assertEquals("Component", root.path("kind").asText());
        assertEquals("orders-service", root.path("metadata").path("name").asText());
        assertEquals("group:default/team-payments", root.path("spec").path("owner").asText());
    }

    @Test
    void isDeterministicAcrossRuns() {
        assertEquals(serializer.serialize(sample(true)), new CatalogSerializer().serialize(sample(true)));
    }
}

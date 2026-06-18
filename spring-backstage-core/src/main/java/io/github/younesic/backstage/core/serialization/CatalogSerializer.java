package io.github.younesic.backstage.core.serialization;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.github.younesic.backstage.core.model.CatalogEntity;

/**
 * Serializes Backstage entities to a single valid multi-document YAML string.
 *
 * <p>Recipe verified against the maintained {@code quarkiverse/quarkus-backstage}
 * {@code Serialization}: each entity is serialized on its own (relying on {@code WRITE_DOC_START_MARKER}
 * for the leading {@code ---}) and the results concatenated; the very first marker is then dropped so
 * the file reads {@code <Component>\n---\n<API>}. {@code SequenceWriter} is deliberately avoided
 * (historic multi-doc bug). {@code MINIMIZE_QUOTES} keeps output readable yet still quotes values that
 * would otherwise be coerced (e.g. {@code "true"}), which is exactly what Backstage requires for
 * string-typed annotation values.
 */
public final class CatalogSerializer {

    private final ObjectMapper mapper;

    public CatalogSerializer() {
        YAMLFactory yf = new YAMLFactory()
                .enable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                // MINIMIZE_QUOTES leaves bare numeric-looking strings unquoted (e.g. version "1.0" -> float,
                // a numeric artifactId -> int), which fails Backstage's string-only annotation validation.
                // This keeps every emitted scalar a string while leaving textual values unquoted.
                .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                .disable(YAMLGenerator.Feature.SPLIT_LINES)
                .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);
        this.mapper = new YAMLMapper(yf)
                .registerModule(new Jdk8Module())
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    /** @return a multi-document YAML string (empty string when there are no entities). */
    public String serialize(List<? extends CatalogEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return "";
        }
        String body = entities.stream().map(this::serializeOne).collect(Collectors.joining());
        return stripLeadingDocMarker(body);
    }

    private String serializeOne(CatalogEntity entity) {
        try {
            return mapper.writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize Backstage entity " + entity.kindName() + "/" + entity.entityName(), e);
        }
    }

    private static String stripLeadingDocMarker(String body) {
        String out = body;
        if (out.startsWith("---")) {
            out = out.substring(3);
            if (out.startsWith("\r\n")) {
                out = out.substring(2);
            } else if (out.startsWith("\n")) {
                out = out.substring(1);
            }
        }
        return out;
    }
}

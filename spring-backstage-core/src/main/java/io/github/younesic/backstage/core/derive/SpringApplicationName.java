package io.github.younesic.backstage.core.derive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Reads {@code spring.application.name} from a module's {@code src/main/resources} at build time,
 * supporting {@code application.yml}, {@code application.yaml} and {@code application.properties},
 * and both nested ({@code spring: application: name:}) and flattened ({@code spring.application.name:})
 * YAML forms. Returns empty (not the placeholder) so the caller can fall back to the artifactId.
 */
public final class SpringApplicationName {

    private static final YAMLMapper YAML = new YAMLMapper();

    private SpringApplicationName() {
    }

    public static Optional<String> read(Path resourcesDir) {
        if (resourcesDir == null) {
            return Optional.empty();
        }
        return fromYaml(resourcesDir.resolve("application.yml"))
                .or(() -> fromYaml(resourcesDir.resolve("application.yaml")))
                .or(() -> fromProperties(resourcesDir.resolve("application.properties")));
    }

    static Optional<String> fromYaml(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonNode root = YAML.readTree(file.toFile());
            if (root == null || root.isMissingNode() || root.isNull()) {
                return Optional.empty();
            }
            JsonNode nested = root.path("spring").path("application").path("name");
            if (nested.isValueNode() && !nested.asText().isEmpty()) {
                return Optional.of(nested.asText());
            }
            JsonNode flat = root.get("spring.application.name");
            if (flat != null && flat.isValueNode() && !flat.asText().isEmpty()) {
                return Optional.of(flat.asText());
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    static Optional<String> fromProperties(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty("spring.application.name");
            return (v != null && !v.trim().isEmpty()) ? Optional.of(v.trim()) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}

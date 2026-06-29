package io.github.younesic.backstage.core.derive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ResourceInferenceTest {

    @Test
    void mapsKnownDependencyCoordinatesToTypes() {
        assertEquals("postgresql", ResourceInference.typeForCoordinate("org.postgresql:postgresql"));
        assertEquals("mysql", ResourceInference.typeForCoordinate("com.mysql:mysql-connector-j"));
        assertEquals("kafka", ResourceInference.typeForCoordinate("org.springframework.kafka:spring-kafka"));
        assertEquals("redis", ResourceInference.typeForCoordinate("io.lettuce:lettuce-core"));
        assertEquals("s3", ResourceInference.typeForCoordinate("software.amazon.awssdk:s3"));
        assertNull(ResourceInference.typeForCoordinate("org.springframework.boot:spring-boot-starter-web"));
        assertNull(ResourceInference.typeForCoordinate(null));
    }

    @Test
    void coordinatesInferenceIsDistinctAndOrdered() {
        Set<String> coords = new LinkedHashSet<>(List.of(
                "org.springframework.boot:spring-boot-starter-web",
                "org.postgresql:postgresql",
                "org.springframework.kafka:spring-kafka"));
        assertEquals(List.of("postgresql", "kafka"), ResourceInference.fromCoordinates(coords));
    }

    @Test
    void infersJdbcTypeEvenWhenHostIsVariabilized() {
        // The value is variabilized — only the jdbc sub-protocol is read.
        List<String> types = ResourceInference.typesFromConfig(
                Set.of("spring.datasource.url"),
                List.of("jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}"));
        assertEquals(List.of("postgresql"), types);
    }

    @Test
    void infersFromStructuralKeysRegardlessOfValues() {
        List<String> types = ResourceInference.typesFromConfig(
                Set.of("spring.kafka.bootstrap-servers", "spring.data.redis.host", "spring.rabbitmq.host",
                        "spring.cloud.aws.s3.bucket"),
                List.of("${KAFKA}", "${REDIS}", "${RABBIT}", "${BUCKET}"));
        assertTrue(types.contains("kafka"));
        assertTrue(types.contains("redis"));
        assertTrue(types.contains("rabbitmq"));
        assertTrue(types.contains("s3"));
    }

    @Test
    void ignoresEmbeddedDatabases() {
        assertEquals(List.of(),
                ResourceInference.typesFromConfig(Set.of("spring.datasource.url"), List.of("jdbc:h2:mem:test")));
    }

    @Test
    void combinesJdbcAndKeySignals() {
        List<String> types = ResourceInference.typesFromConfig(
                Set.of("spring.datasource.url", "spring.kafka.bootstrap-servers"),
                List.of("jdbc:mysql://${H}:3306/app", "${BROKERS}"));
        assertTrue(types.contains("mysql"));
        assertTrue(types.contains("kafka"));
        assertFalse(types.contains("postgresql"));
    }
}

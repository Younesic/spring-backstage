package io.github.younesic.backstage.core.derive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class OpenApiDetectorTest {

    @Test
    void detectsSpringBoot3Starter() {
        assertTrue(OpenApiDetector.isSpringdocPresent(List.of(
                "org.springframework.boot:spring-boot-starter-web",
                "org.springdoc:springdoc-openapi-starter-webmvc-ui")));
    }

    @Test
    void detectsSpringBoot2Ui() {
        assertTrue(OpenApiDetector.isSpringdocPresent(List.of("org.springdoc:springdoc-openapi-ui")));
    }

    @Test
    void ignoresLegacySpringfox() {
        assertFalse(OpenApiDetector.isSpringdocPresent(List.of("io.springfox:springfox-boot-starter")));
    }

    @Test
    void handlesEmptyAndNull() {
        assertFalse(OpenApiDetector.isSpringdocPresent(List.of()));
        assertFalse(OpenApiDetector.isSpringdocPresent(null));
    }
}

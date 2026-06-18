package io.github.younesic.backstage.core.derive;

import java.util.Collection;

/**
 * Detects springdoc-openapi by build dependency coordinates. Covers both Spring Boot 2
 * ({@code org.springdoc:springdoc-openapi-ui}, {@code -webmvc-core}, …) and Spring Boot 3
 * ({@code org.springdoc:springdoc-openapi-starter-webmvc-ui}, …). The legacy springfox library
 * ({@code io.springfox:*}, Swagger 2) is intentionally not matched.
 */
public final class OpenApiDetector {

    private OpenApiDetector() {
    }

    /**
     * @param dependencyCoordinates {@code "groupId:artifactId"} of the project's compile/runtime deps.
     * @return {@code true} if springdoc-openapi is present.
     */
    public static boolean isSpringdocPresent(Collection<String> dependencyCoordinates) {
        if (dependencyCoordinates == null) {
            return false;
        }
        return dependencyCoordinates.stream().anyMatch(OpenApiDetector::isSpringdoc);
    }

    static boolean isSpringdoc(String groupArtifact) {
        if (groupArtifact == null) {
            return false;
        }
        return groupArtifact.trim().startsWith("org.springdoc:springdoc-openapi-");
    }
}

package io.github.younesic.backstage.core.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DependencyInferenceTest {

    private final Map<String, String> reactor = Map.of(
            "com.acme:orders-api", "orders-api",
            "com.acme:shared-lib", "shared-lib");

    @Test
    void infersOnlyAnnotatedSiblingsNotExternals() {
        List<String> refs = DependencyInference.refs(
                List.of("com.acme:shared-lib", "org.springframework:spring-core"),
                reactor, "orders-api");
        assertEquals(List.of("component:default/shared-lib"), refs);
    }

    @Test
    void excludesSelf() {
        assertTrue(DependencyInference.refs(
                List.of("com.acme:orders-api"), reactor, "orders-api").isEmpty());
    }

    @Test
    void emptyWhenNoInterModuleDependency() {
        assertTrue(DependencyInference.refs(
                List.of("org.springframework:spring-web"), reactor, "orders-api").isEmpty());
    }
}

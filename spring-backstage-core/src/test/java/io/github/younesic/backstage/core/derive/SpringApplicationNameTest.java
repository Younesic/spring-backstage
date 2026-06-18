package io.github.younesic.backstage.core.derive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringApplicationNameTest {

    @Test
    void readsNestedYaml(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("application.yml"), "spring:\n  application:\n    name: orders\n");
        assertEquals("orders", SpringApplicationName.read(dir).orElseThrow());
    }

    @Test
    void readsFlattenedYaml(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("application.yml"), "spring.application.name: widgets\n");
        assertEquals("widgets", SpringApplicationName.read(dir).orElseThrow());
    }

    @Test
    void readsProperties(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("application.properties"), "spring.application.name=gadgets\n");
        assertEquals("gadgets", SpringApplicationName.read(dir).orElseThrow());
    }

    @Test
    void emptyWhenAbsent(@TempDir Path dir) {
        assertTrue(SpringApplicationName.read(dir).isEmpty());
    }
}

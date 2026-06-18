package io.github.younesic.backstage.core.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class NameUniquenessTest {

    @Test
    void findsDuplicatesWithSortedModuleLists() {
        Map<String, List<String>> dups = NameUniqueness.duplicates(List.of(
                new NameUniqueness.Entry("orders", "mod-b"),
                new NameUniqueness.Entry("orders", "mod-a"),
                new NameUniqueness.Entry("billing", "mod-c")));
        assertEquals(Set.of("orders"), dups.keySet());
        assertEquals(List.of("mod-a", "mod-b"), dups.get("orders"));
    }

    @Test
    void emptyWhenAllUnique() {
        assertTrue(NameUniqueness.duplicates(List.of(
                new NameUniqueness.Entry("a", "m1"),
                new NameUniqueness.Entry("b", "m2"))).isEmpty());
    }

    @Test
    void describeListsOffendingModules() {
        String msg = NameUniqueness.describe(Map.of("orders", List.of("mod-a", "mod-b")));
        assertTrue(msg.contains("orders"));
        assertTrue(msg.contains("mod-a"));
        assertTrue(msg.contains("mod-b"));
    }
}

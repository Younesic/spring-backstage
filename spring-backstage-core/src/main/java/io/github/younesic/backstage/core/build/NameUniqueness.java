package io.github.younesic.backstage.core.build;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Detects {@code metadata.name} collisions across modules of a monorepo. Backstage entity names must
 * be globally unique per kind; two ingested files declaring the same {@code Component} name cause a
 * "conflicting entityRef" in the catalog. In aggregate/validation mode this is checked up-front so the
 * build fails fast with the offending modules listed.
 */
public final class NameUniqueness {

    private NameUniqueness() {
    }

    /** One generated entity and the module (label) that produced it. */
    public static final class Entry {
        public final String name;
        public final String module;

        public Entry(String name, String module) {
            this.name = name;
            this.module = module;
        }
    }

    /**
     * @return for every name produced by more than one module, the (sorted) list of module labels.
     *         Empty when all names are unique. Deterministic ordering for stable messages.
     */
    public static Map<String, List<String>> duplicates(List<Entry> entries) {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        for (Entry e : entries) {
            byName.computeIfAbsent(e.name, k -> new java.util.ArrayList<>()).add(e.module);
        }
        Map<String, List<String>> dups = new TreeMap<>();
        for (Map.Entry<String, List<String>> e : byName.entrySet()) {
            if (e.getValue().size() > 1) {
                List<String> modules = new java.util.ArrayList<>(e.getValue());
                modules.sort(String::compareTo);
                dups.put(e.getKey(), modules);
            }
        }
        return dups;
    }

    /** Human-readable, actionable message for a non-empty duplicates map. */
    public static String describe(Map<String, List<String>> duplicates) {
        StringBuilder sb = new StringBuilder("Duplicate Backstage entity name(s) across modules:");
        for (Map.Entry<String, List<String>> e : duplicates.entrySet()) {
            sb.append("\n  - '").append(e.getKey()).append("' produced by ").append(e.getValue());
        }
        sb.append("\nNames must be globally unique. Set @BackstageComponent(name=\"...\") on the conflicting"
                + " module(s) to disambiguate.");
        return sb.toString();
    }
}

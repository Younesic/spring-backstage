package io.github.younesic.backstage.core.build;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.github.younesic.backstage.core.derive.Names;

/**
 * Infers {@code spec.dependsOn} between Components of a monorepo from inter-module Maven dependencies.
 * A dependency is only turned into a {@code component:default/<name>} reference when the depended-on
 * module is <em>also</em> a generated Component (i.e. annotated). External and transitive dependencies
 * are never inferred — only direct dependencies on a sibling Component. Aggregate mode only.
 */
public final class DependencyInference {

    private DependencyInference() {
    }

    /**
     * @param directDepKeys      the module's direct dependency coordinates ({@code groupId:artifactId}).
     * @param keyToComponentName coordinate → Component name, for every annotated module in the reactor.
     * @param selfComponentName  the depending module's own Component name (excluded from the result).
     * @return sorted {@code component:default/<name>} references; empty when nothing inter-module matches.
     */
    public static List<String> refs(Collection<String> directDepKeys,
            Map<String, String> keyToComponentName, String selfComponentName) {
        TreeSet<String> names = new TreeSet<>();
        for (String key : directDepKeys) {
            String name = keyToComponentName.get(key);
            if (name != null && !name.equals(selfComponentName)) {
                names.add(name);
            }
        }
        return names.stream().map(Names::componentRef).collect(Collectors.toList());
    }
}

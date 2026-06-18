package io.github.younesic.backstage.core.derive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Name/owner/tag normalization to satisfy the (stricter-than-JSON-schema) Backstage validator rules:
 * {@code metadata.name} is 1..63 chars of {@code [a-z0-9]} with single {@code -} separators; tags use
 * {@code ^[a-z0-9+#]+(-[a-z0-9+#]+)*$}. Names are forced lowercase (Backstage uniqueness is
 * case-insensitive) which also matches this repo's kebab-case convention.
 */
public final class Names {

    /** A still-unresolved Maven/Gradle filtering placeholder such as {@code @project.artifactId@}. */
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("^@.*@$");
    private static final int MAX_LENGTH = 63;

    /** One segment (kind / namespace / name) of a Backstage entity reference. */
    private static final Pattern REF_SEGMENT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,62}$");

    private Names() {
    }

    /** Normalize an arbitrary string into a valid {@code metadata.name}; throws if nothing valid remains. */
    public static String normalizeName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Cannot derive metadata.name from a null source");
        }
        String s = raw.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-");
        s = stripSeparators(s);
        if (s.length() > MAX_LENGTH) {
            s = stripSeparators(s.substring(0, MAX_LENGTH));
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot derive a valid Backstage metadata.name from '" + raw + "'. "
                    + "Set @BackstageComponent(name=\"...\") or spring.application.name.");
        }
        return s;
    }

    /**
     * Resolve the final name: {@code spring.application.name} when it is a real literal (not blank and
     * not an unresolved {@code @...@} placeholder), otherwise the Maven artifactId; then normalized.
     */
    public static String deriveName(Optional<String> springApplicationName, String artifactId) {
        String base = springApplicationName
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .filter(v -> !UNRESOLVED_PLACEHOLDER.matcher(v).matches())
                .orElse(artifactId);
        return normalizeName(base);
    }

    /**
     * Validate the FORMAT of a {@code spec.owner} entity reference and normalize it to the
     * fully-qualified {@code kind:namespace/name} form (default kind {@code group}, default namespace
     * {@code default}). Existence is NOT checked — the build does not know the catalogue, so a
     * non-existent owner becomes a dangling reference in Backstage, not a build error.
     *
     * <p>{@code team-x} → {@code group:default/team-x}; {@code group:team-x} →
     * {@code group:default/team-x}; {@code custom/team-x} → {@code group:custom/team-x};
     * {@code user:default/jdoe} kept. Malformed refs (blank, spaces, illegal chars) throw.
     */
    public static String qualifyOwner(String owner) {
        return qualifyRef(owner, "group");
    }

    /**
     * Validate the FORMAT of any entity reference and normalize it to {@code kind:namespace/name}
     * (default {@code namespace} {@code default}, default {@code kind} = {@code defaultKind}). Existence
     * is never checked. Used for {@code consumesApis} ({@code api}), {@code dependsOn} ({@code component})
     * and {@code spec.owner} ({@code group}). Malformed refs throw.
     */
    public static String qualifyRef(String reference, String defaultKind) {
        String ref = reference == null ? "" : reference.trim();
        if (ref.isEmpty()) {
            throw new IllegalArgumentException("entity reference is blank");
        }
        String kind = defaultKind;
        String namespace = "default";
        String rest = ref;

        int colon = rest.indexOf(':');
        if (colon >= 0) {
            kind = rest.substring(0, colon);
            rest = rest.substring(colon + 1);
        }
        int slash = rest.indexOf('/');
        String name;
        if (slash >= 0) {
            namespace = rest.substring(0, slash);
            name = rest.substring(slash + 1);
        } else {
            name = rest;
        }

        requireRefSegment(kind, "kind", ref);
        requireRefSegment(namespace, "namespace", ref);
        requireRefSegment(name, "name", ref);
        return kind + ":" + namespace + "/" + name;
    }

    private static void requireRefSegment(String segment, String role, String ref) {
        if (!REF_SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException(
                    "Invalid spec.owner reference '" + ref + "': the " + role + " segment '" + segment
                    + "' is not a valid Backstage entity-ref part ([A-Za-z0-9] then [A-Za-z0-9._-], <=63 chars).");
        }
    }

    /** Fully-qualified Component reference for a generated module name, e.g. {@code component:default/orders-api}. */
    public static String componentRef(String name) {
        return "component:default/" + name;
    }

    /** Normalize tags to the Backstage tag charset, dropping empties and duplicates while preserving order. */
    public static List<String> normalizeTags(String[] tags) {
        List<String> out = new ArrayList<>();
        if (tags == null) {
            return out;
        }
        for (String t : tags) {
            String n = normalizeTag(t);
            if (n != null && !out.contains(n)) {
                out.add(n);
            }
        }
        return out;
    }

    static String normalizeTag(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^a-z0-9+#]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (s.length() > MAX_LENGTH) {
            s = s.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return s.isEmpty() ? null : s;
    }

    private static String stripSeparators(String s) {
        return s.replaceAll("^[-_.]+", "").replaceAll("[-_.]+$", "");
    }
}

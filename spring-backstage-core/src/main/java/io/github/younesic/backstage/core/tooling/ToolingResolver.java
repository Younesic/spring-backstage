package io.github.younesic.backstage.core.tooling;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the final tooling-annotation map by precedence — explicit {@code @BackstageComponent}
 * override &gt; CI environment variable &gt; org-convention template — omitting any key that resolves to
 * nothing. Pure and deterministic: identical inputs produce an identical ordered map (insertion order =
 * registry order), so the generated catalog file stays byte-idempotent across rebuilds.
 *
 * <p>Graceful degradation is the rule: an unresolved convention placeholder, a missing env var, or an
 * absent source drops only that one annotation. Nothing here ever throws on missing data; the only
 * surfaced signal is a {@link Warn} for genuinely malformed input (bad key, malformed override).
 */
public final class ToolingResolver {

    /** Optional DNS-1123 prefix + {@code /} + name; each label lowercase for the prefix, alnum/._- for the name. */
    static final Pattern KEY = Pattern.compile(
            "^([a-z0-9]([a-z0-9.-]*[a-z0-9])?/)?[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");

    /** Sink for non-fatal warnings (e.g. an invalid annotation key). */
    public interface Warn {
        void warn(String message);
    }

    private ToolingResolver() {
    }

    /**
     * @param userRules user-supplied rules from plugin config; merged over the built-in defaults by key.
     * @param overrides explicit {@code key -> value} pairs from the annotation (highest precedence);
     *                  produced by {@link #parseOverrides(String[], Warn)}.
     * @param ci        detected CI environment (env-var source).
     * @param vars      template variables: {@code groupId}, {@code artifactId}, {@code version},
     *                  {@code branch}, {@code harborProject}.
     * @param warn      sink for non-fatal warnings.
     * @return ordered, validated annotation map; never {@code null}; unresolved keys omitted.
     */
    public static Map<String, String> resolve(List<ToolingRule> userRules, Map<String, String> overrides,
            CiEnvironment ci, Map<String, String> vars, Warn warn) {
        Warn sink = warn == null ? msg -> { } : warn;
        Map<String, String> ovr = overrides == null ? Map.of() : overrides;
        List<ToolingRule> rules = merge(DefaultToolingRules.all(), userRules);

        Map<String, String> out = new LinkedHashMap<>();
        for (ToolingRule rule : rules) {
            if (rule == null || !rule.enabled || isBlank(rule.key)) {
                continue;
            }
            String key = rule.key.trim();
            if (!KEY.matcher(key).matches()) {
                sink.warn("Ignoring tooling annotation with invalid key '" + key + "'.");
                continue;
            }
            String value = resolveValue(rule, key, ovr, ci, vars);
            if (notBlank(value)) {
                out.put(key, value.trim());
            }
        }
        // Overrides for custom keys not present in any rule are still honoured (explicit user intent).
        // Re-validate the key here too: resolve() is public, so a caller may pass a map that did not go
        // through parseOverrides().
        for (Map.Entry<String, String> e : ovr.entrySet()) {
            String key = e.getKey();
            if (notBlank(e.getValue()) && notBlank(key) && KEY.matcher(key.trim()).matches()) {
                out.putIfAbsent(key.trim(), e.getValue().trim());
            }
        }
        return out;
    }

    private static String resolveValue(ToolingRule rule, String key, Map<String, String> overrides,
            CiEnvironment ci, Map<String, String> vars) {
        // 1. explicit annotation override
        String override = overrides.get(key);
        if (notBlank(override)) {
            return override;
        }
        // 2. CI environment variable (first non-blank, in declared order)
        for (String name : rule.envNames()) {
            String v = ci == null ? null : ci.var(name);
            if (notBlank(v)) {
                return v;
            }
        }
        // 3. org-convention template (omitted if any referenced placeholder is unresolved)
        if (notBlank(rule.convention)) {
            return substitute(rule.convention, vars);
        }
        return null;
    }

    /**
     * Merge built-in defaults with user rules <em>by key</em>. A user rule replaces a default at the
     * default's position (order preserved); brand-new keys are appended. {@code null}/blank-key rules
     * are dropped.
     */
    static List<ToolingRule> merge(List<ToolingRule> defaults, List<ToolingRule> userRules) {
        LinkedHashMap<String, ToolingRule> byKey = new LinkedHashMap<>();
        for (ToolingRule d : defaults) {
            if (d != null && notBlank(d.key)) {
                byKey.put(d.key.trim(), d);
            }
        }
        if (userRules != null) {
            for (ToolingRule u : userRules) {
                if (u != null && notBlank(u.key)) {
                    byKey.put(u.key.trim(), u);
                }
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /**
     * Substitute {@code {name}} placeholders from {@code vars}. Returns {@code null} when <em>any</em>
     * referenced placeholder is missing/blank — so an under-specified convention omits its annotation
     * rather than emitting a literal {@code {placeholder}}.
     */
    static String substitute(String template, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String val = vars == null ? null : vars.get(name);
            if (val == null || val.trim().isEmpty()) {
                return null;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val.trim()));
        }
        m.appendTail(sb);
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Parse {@code "key=value"} pairs (from {@code @BackstageComponent.annotations}) into a validated
     * map. Malformed entries (no {@code =}, blank/invalid key) are dropped with a warning. The value is
     * everything after the first {@code =}, allowing values that themselves contain {@code =}.
     */
    public static Map<String, String> parseOverrides(String[] pairs, Warn warn) {
        Warn sink = warn == null ? msg -> { } : warn;
        Map<String, String> out = new LinkedHashMap<>();
        if (pairs == null) {
            return out;
        }
        for (String raw : pairs) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String s = raw.trim();
            int eq = s.indexOf('=');
            if (eq <= 0) {
                sink.warn("Ignoring malformed annotation override '" + raw + "' (expected key=value).");
                continue;
            }
            String key = s.substring(0, eq).trim();
            String value = s.substring(eq + 1).trim();
            if (key.isEmpty() || !KEY.matcher(key).matches()) {
                sink.warn("Ignoring annotation override with invalid key '" + key + "'.");
                continue;
            }
            out.put(key, value);
        }
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

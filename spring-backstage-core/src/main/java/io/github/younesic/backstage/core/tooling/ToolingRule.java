package io.github.younesic.backstage.core.tooling;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry in the tooling-annotation registry: which Backstage annotation {@code key} to emit, and the
 * ordered sources to resolve its value from. Resolution precedence (applied by {@link ToolingResolver}):
 * an explicit {@code @BackstageComponent.annotations} override wins first, then the first non-blank CI
 * {@code env} variable (in declared order), then the {@code convention} template. If none yields a value
 * the key is <em>omitted</em> — a missing tooling annotation never fails the build.
 *
 * <p>Plain mutable POJO with a no-arg constructor so the Maven plugin can bind a
 * {@code <toolingAnnotation>} XML block onto it; the convenience constructor builds the entries in
 * {@link DefaultToolingRules}.
 */
public final class ToolingRule {

    /** Backstage annotation key, e.g. {@code sonarqube.org/project-key}. */
    public String key;

    /** Environment variable names tried in order; the first non-blank one wins. May be empty/null. */
    public List<String> env;

    /** Template resolved against build vars, e.g. {@code {groupId}_{artifactId}}. May be {@code null}. */
    public String convention;

    /** When {@code false}, the rule is disabled — used to switch off a built-in default by key. */
    public boolean enabled = true;

    /** Required for Maven parameter injection. */
    public ToolingRule() {
    }

    public ToolingRule(String key, List<String> env, String convention) {
        this.key = key;
        this.env = env == null ? new ArrayList<>() : new ArrayList<>(env);
        this.convention = convention;
        this.enabled = true;
    }

    /** Never-null view of the env candidate list. */
    public List<String> envNames() {
        return env == null ? List.of() : env;
    }
}

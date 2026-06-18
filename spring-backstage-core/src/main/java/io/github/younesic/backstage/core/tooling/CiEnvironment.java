package io.github.younesic.backstage.core.tooling;

import java.util.Map;

/**
 * Detects the CI system from a snapshot of environment variables and exposes the semantic build facts
 * that vary by system (repo slug, branch, container image). Built from an injected map so it is fully
 * unit-testable — the Maven plugin passes {@code System.getenv()} at runtime.
 *
 * <p>Detection keys on the presence of each system's signature variable: {@code GITHUB_ACTIONS=true},
 * {@code GITLAB_CI=true}, {@code JENKINS_URL} set. Outside CI the system is {@link CiSystem#NONE} and
 * every semantic accessor returns {@code null}, so callers degrade gracefully to git/convention sources.
 */
public final class CiEnvironment {

    /** The recognized CI systems (plus {@code NONE} for local/unknown). */
    public enum CiSystem { GITHUB, GITLAB, JENKINS, NONE }

    private final CiSystem system;
    private final Map<String, String> env;

    private CiEnvironment(CiSystem system, Map<String, String> env) {
        this.system = system;
        this.env = env == null ? Map.of() : env;
    }

    /** Detect the CI system from an env snapshot; never returns {@code null}. */
    public static CiEnvironment detect(Map<String, String> env) {
        Map<String, String> e = env == null ? Map.of() : env;
        if ("true".equalsIgnoreCase(trimToNull(e.get("GITHUB_ACTIONS")))) {
            return new CiEnvironment(CiSystem.GITHUB, e);
        }
        if ("true".equalsIgnoreCase(trimToNull(e.get("GITLAB_CI")))) {
            return new CiEnvironment(CiSystem.GITLAB, e);
        }
        if (notBlank(e.get("JENKINS_URL"))) {
            return new CiEnvironment(CiSystem.JENKINS, e);
        }
        return new CiEnvironment(CiSystem.NONE, e);
    }

    public CiSystem system() {
        return system;
    }

    /** Raw environment variable lookup, trimmed; {@code null} if unset or blank. */
    public String var(String name) {
        return name == null ? null : trimToNull(env.get(name));
    }

    /** Current branch from the CI system's standard variable, or {@code null}. */
    public String branch() {
        switch (system) {
            case GITHUB:
                return var("GITHUB_REF_NAME");
            case GITLAB:
                return var("CI_COMMIT_REF_NAME");
            case JENKINS:
                String b = var("BRANCH_NAME");
                return b != null ? b : stripOriginPrefix(var("GIT_BRANCH"));
            default:
                return null;
        }
    }

    /**
     * Jenkins {@code GIT_BRANCH} comes in several shapes — {@code main}, {@code origin/main},
     * {@code remotes/origin/feature/x}. Strip the optional {@code remotes/} and the remote-name segment,
     * preserving multi-segment branch names.
     */
    private static String stripOriginPrefix(String branch) {
        if (branch == null) {
            return null;
        }
        String b = branch.trim();
        if (b.startsWith("remotes/")) {
            b = b.substring("remotes/".length());
        }
        int slash = b.indexOf('/');
        if (slash >= 0 && slash < b.length() - 1) {
            b = b.substring(slash + 1);
        }
        return b.isEmpty() ? null : b;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

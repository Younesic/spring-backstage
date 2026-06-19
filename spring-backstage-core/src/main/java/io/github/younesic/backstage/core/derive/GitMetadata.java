package io.github.younesic.backstage.core.derive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the {@code github.com/project-slug} and {@code backstage.io/source-location} annotations
 * from the git {@code origin} remote and the working-tree layout.
 *
 * <p>For monorepos the {@code source-location} must point at each module's sub-folder, so this class
 * resolves three things from the filesystem: the repo {@code origin} URL (→ slug + base URL), the
 * <em>working-tree root</em> (the directory containing {@code .git}, used to compute each module's
 * relative path), and the current <em>branch</em> (read from {@code HEAD}). The per-module value is
 * {@code url:<baseUrl>/tree/<branch>/<module-relative-path>/}.
 *
 * <p>Pure helpers ({@link #fromRemoteUrl(String)}, {@link #buildSourceLocation(String, String, String)},
 * {@link #relativeModulePath(Path, Path)}, {@link #parseBranchFromHead(List)},
 * {@link #parseOriginFromConfig(List)}) are unit-testable; {@link #discover(Path, String, String)}
 * is best-effort and returns empty when nothing usable is found.
 */
public final class GitMetadata {

    /**
     * SCM-agnostic remote parser: group(1) = host, group(2) = path (the slug; GitLab nested groups give
     * {@code group/subgroup/project}). Trailing {@code .git}/{@code /} are stripped separately. Matches
     * {@code git@host:path}, {@code https://host/path}, {@code ssh://[git@]host/path} for ANY host, so
     * GitHub, GitLab (.com or self-hosted), and others all resolve.
     */
    static final Pattern REMOTE = Pattern.compile(
            "(?:git@|https?://|ssh://(?:git@)?)([^/:]+)[:/](.+?)(?:\\.git)?/?$");

    /** Default branch used when {@code HEAD} is detached/unknown and no override is given. */
    public static final String DEFAULT_BRANCH = "main";

    private GitMetadata() {
    }

    /** Repository linkage derived for a working tree. */
    public static final class RepoInfo {
        /** {@code <group>/.../<repo>} for the {@code <provider>.com/project-slug} annotation. */
        public final String projectSlug;
        /** {@code https://<host>/<slug>} (no trailing slash). */
        public final String baseUrl;
        /** SCM provider: {@code "github"}, {@code "gitlab"}, or {@code null} when not inferable from host. */
        public final String provider;
        /** Resolved branch, or {@code null} when detached/unknown. */
        public final String branch;
        /** Directory containing {@code .git}, or {@code null} when not found. */
        public final Path workTreeRoot;

        RepoInfo(String projectSlug, String baseUrl, String provider, String branch, Path workTreeRoot) {
            this.projectSlug = projectSlug;
            this.baseUrl = baseUrl;
            this.provider = provider;
            this.branch = branch;
            this.workTreeRoot = workTreeRoot;
        }

        RepoInfo withGit(String branch, Path workTreeRoot) {
            return new RepoInfo(projectSlug, baseUrl, provider, branch, workTreeRoot);
        }
    }

    /** Parse a raw remote URL into provider + slug + base URL; empty if it isn't a recognizable remote. */
    public static Optional<RepoInfo> fromRemoteUrl(String remoteUrl) {
        if (remoteUrl == null) {
            return Optional.empty();
        }
        Matcher m = REMOTE.matcher(remoteUrl.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        String host = m.group(1);
        String slug = m.group(2).replaceAll("\\.git$", "");
        return Optional.of(new RepoInfo(slug, "https://" + host + "/" + slug, providerForHost(host), null, null));
    }

    /** Infer the SCM provider from the host: github.com / gitlab.com or a self-hosted variant. */
    static String providerForHost(String host) {
        if (host == null) {
            return null;
        }
        String h = host.toLowerCase();
        if (h.contains("github")) {
            return "github";
        }
        if (h.contains("gitlab")) {
            return "gitlab";
        }
        return null;
    }

    /** Best-effort discovery from the filesystem starting at {@code start}. */
    public static Optional<RepoInfo> discover(Path start) {
        return discover(start, null, null);
    }

    /** Best-effort discovery with optional overrides; no Maven {@code <scm>} fallback. */
    public static Optional<RepoInfo> discover(Path start, String branchOverride, String baseUrlOverride) {
        return discover(start, branchOverride, baseUrlOverride, null);
    }

    /**
     * Best-effort discovery with optional overrides for CI (detached HEAD, mirror URLs) and a final
     * Maven {@code <scm>}-URL fallback. Resolution order for the base URL: git {@code origin} →
     * {@code baseUrlOverride} → {@code scmUrlFallback}. The {@code scm:<provider>:} prefix of a Maven
     * connection URL is stripped before parsing.
     *
     * @param branchOverride  used verbatim when non-blank (else read from {@code HEAD}).
     * @param baseUrlOverride e.g. {@code https://github.com/org/repo}; used when non-blank even if the
     *                        directory is not a git repo.
     * @param scmUrlFallback  the project's {@code <scm>} URL (e.g. {@code ${project.scm.url}}); used only
     *                        when neither git origin nor {@code baseUrlOverride} yields a base URL.
     */
    public static Optional<RepoInfo> discover(Path start, String branchOverride, String baseUrlOverride,
            String scmUrlFallback) {
        Optional<GitContext> ctx = findGitContext(start);

        RepoInfo fromGit = ctx
                .flatMap(c -> originUrl(c).flatMap(GitMetadata::fromRemoteUrl)
                        .map(r -> r.withGit(branch(c, branchOverride), c.workTreeRoot)))
                .orElse(null);

        if (fromGit != null) {
            return Optional.of(fromGit);
        }

        // No git origin — fall back to an explicit base-URL override, then the Maven <scm> URL.
        final String fallbackUrl = notBlank(baseUrlOverride) ? baseUrlOverride.trim()
                : stripScmPrefix(scmUrlFallback);
        if (notBlank(fallbackUrl)) {
            RepoInfo base = fromRemoteUrl(fallbackUrl)
                    .orElseGet(() -> new RepoInfo(slugFromBaseUrl(fallbackUrl), trimTrailingSlash(fallbackUrl),
                            null, null, null));
            Path workTree = ctx.map(c -> c.workTreeRoot).orElse(null);
            String branch = notBlank(branchOverride) ? branchOverride.trim()
                    : ctx.map(c -> branch(c, null)).orElse(null);
            return Optional.of(base.withGit(branch, workTree));
        }
        return Optional.empty();
    }

    /** Strip a leading {@code scm:<provider>:} from a Maven SCM URL; pass-through for plain URLs. */
    static String stripScmPrefix(String url) {
        if (url == null) {
            return null;
        }
        String u = url.trim();
        if (u.isEmpty()) {
            return null;
        }
        return u.replaceFirst("^scm:[^:]+:", "");
    }

    /** Build the per-module {@code backstage.io/source-location} value. */
    public static String sourceLocation(RepoInfo repo, Path moduleBasedir, String branchFallback) {
        String branch = notBlank(repo.branch) ? repo.branch
                : (notBlank(branchFallback) ? branchFallback : DEFAULT_BRANCH);
        String relPath = repo.workTreeRoot != null ? relativeModulePath(repo.workTreeRoot, moduleBasedir) : "";
        return buildSourceLocation(repo.baseUrl, branch, relPath);
    }

    /** {@code url:<baseUrl>/tree/<branch>/<relPath>/} (root module → {@code .../tree/<branch>/}). */
    static String buildSourceLocation(String baseUrl, String branch, String relPath) {
        String base = "url:" + trimTrailingSlash(baseUrl) + "/tree/" + branch;
        if (relPath == null || relPath.isEmpty()) {
            return base + "/";
        }
        return base + "/" + relPath + "/";
    }

    /** Forward-slash module path relative to the working-tree root; empty if at root or outside it. */
    static String relativeModulePath(Path workTreeRoot, Path moduleBasedir) {
        if (workTreeRoot == null || moduleBasedir == null) {
            return "";
        }
        Path root = workTreeRoot.toAbsolutePath().normalize();
        Path module = moduleBasedir.toAbsolutePath().normalize();
        Path rel;
        try {
            rel = root.relativize(module);
        } catch (IllegalArgumentException e) {
            return "";
        }
        String s = rel.toString().replace('\\', '/');
        if (s.isEmpty() || s.equals(".") || s.startsWith("..")) {
            return "";
        }
        return s;
    }

    static Optional<String> parseBranchFromHead(List<String> headLines) {
        for (String raw : headLines) {
            String line = raw.trim();
            if (line.startsWith("ref:")) {
                String ref = line.substring("ref:".length()).trim();
                int idx = ref.indexOf("refs/heads/");
                if (idx >= 0) {
                    String branch = ref.substring(idx + "refs/heads/".length()).trim();
                    return branch.isEmpty() ? Optional.empty() : Optional.of(branch);
                }
            }
        }
        return Optional.empty(); // detached HEAD (raw SHA) → unknown branch
    }

    static Optional<String> parseOriginFromConfig(List<String> lines) {
        boolean inOrigin = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("[")) {
                inOrigin = line.replaceAll("\\s+", "").equalsIgnoreCase("[remote\"origin\"]");
                continue;
            }
            if (inOrigin && line.startsWith("url")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    return Optional.of(line.substring(eq + 1).trim());
                }
            }
        }
        return Optional.empty();
    }

    // --- filesystem resolution ---------------------------------------------------------------

    /** {@code workTreeRoot} = dir containing {@code .git}; {@code headDir} holds HEAD; {@code configDir} holds config. */
    private static final class GitContext {
        final Path workTreeRoot;
        final Path headDir;
        final Path configDir;

        GitContext(Path workTreeRoot, Path headDir, Path configDir) {
            this.workTreeRoot = workTreeRoot;
            this.headDir = headDir;
            this.configDir = configDir;
        }
    }

    private static Optional<GitContext> findGitContext(Path start) {
        Path dir = start == null ? null : start.toAbsolutePath();
        while (dir != null) {
            Path dotGit = dir.resolve(".git");
            if (Files.isDirectory(dotGit)) {
                return Optional.of(new GitContext(dir, dotGit, dotGit));
            }
            if (Files.isRegularFile(dotGit)) {
                Optional<Path> gitdir = resolveGitFile(dir, dotGit);
                if (gitdir.isPresent()) {
                    // worktree: HEAD lives in the per-worktree gitdir, config in the commondir.
                    return Optional.of(new GitContext(dir, gitdir.get(), resolveCommonDir(gitdir.get())));
                }
            }
            dir = dir.getParent();
        }
        return Optional.empty();
    }

    private static Optional<String> originUrl(GitContext ctx) {
        Path config = ctx.configDir.resolve("config");
        if (!Files.isRegularFile(config)) {
            return Optional.empty();
        }
        try {
            return parseOriginFromConfig(Files.readAllLines(config));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String branch(GitContext ctx, String branchOverride) {
        if (notBlank(branchOverride)) {
            return branchOverride.trim();
        }
        Path head = ctx.headDir.resolve("HEAD");
        if (!Files.isRegularFile(head)) {
            return null;
        }
        try {
            return parseBranchFromHead(Files.readAllLines(head)).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** Resolve a {@code .git} <em>file</em> (worktree/submodule) of the form {@code gitdir: <path>}. */
    private static Optional<Path> resolveGitFile(Path dir, Path dotGitFile) {
        try {
            for (String line : Files.readAllLines(dotGitFile)) {
                if (line.startsWith("gitdir:")) {
                    Path p = Path.of(line.substring("gitdir:".length()).trim());
                    return Optional.of((p.isAbsolute() ? p : dir.resolve(p)).normalize());
                }
            }
        } catch (IOException ignored) {
            // fall through to empty
        }
        return Optional.empty();
    }

    /** For a linked worktree the shared config lives in {@code commondir}. */
    private static Path resolveCommonDir(Path gitdir) {
        Path commondir = gitdir.resolve("commondir");
        if (Files.isRegularFile(commondir)) {
            try {
                Path p = Path.of(Files.readString(commondir).trim());
                return (p.isAbsolute() ? p : gitdir.resolve(p)).normalize();
            } catch (IOException ignored) {
                // fall through
            }
        }
        return gitdir;
    }

    private static String slugFromBaseUrl(String baseUrl) {
        return fromRemoteUrl(baseUrl).map(r -> r.projectSlug).orElse(null);
    }

    private static String trimTrailingSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}

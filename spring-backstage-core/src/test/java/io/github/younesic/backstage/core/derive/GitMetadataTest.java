package io.github.younesic.backstage.core.derive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.younesic.backstage.core.derive.GitMetadata.RepoInfo;

class GitMetadataTest {

    @Test
    void parsesSshRemoteToSlugAndBaseUrl() {
        RepoInfo r = GitMetadata.fromRemoteUrl("git@github.com:my-org/my-repo.git").orElseThrow();
        assertEquals("my-org/my-repo", r.projectSlug);
        assertEquals("https://github.com/my-org/my-repo", r.baseUrl);
    }

    @Test
    void parsesHttpsVariants() {
        assertEquals("my-org/my-repo",
                GitMetadata.fromRemoteUrl("https://github.com/my-org/my-repo.git").orElseThrow().projectSlug);
        assertEquals("my-org/my-repo",
                GitMetadata.fromRemoteUrl("https://github.com/my-org/my-repo").orElseThrow().projectSlug);
        assertEquals("my-org/my-repo",
                GitMetadata.fromRemoteUrl("https://github.com/my-org/my-repo/").orElseThrow().projectSlug);
    }

    @Test
    void parsesGitlabIncludingNestedGroups() {
        RepoInfo simple = GitMetadata.fromRemoteUrl("git@gitlab.com:group/project.git").orElseThrow();
        assertEquals("group/project", simple.projectSlug);
        assertEquals("https://gitlab.com/group/project", simple.baseUrl);
        assertEquals("gitlab", simple.provider);

        RepoInfo nested = GitMetadata.fromRemoteUrl("https://gitlab.com/group/sub/project.git").orElseThrow();
        assertEquals("group/sub/project", nested.projectSlug, "GitLab nested groups keep all path segments");
        assertEquals("gitlab", nested.provider);
    }

    @Test
    void parsesSelfHostedHostsAndInfersProvider() {
        assertEquals("gitlab", GitMetadata.fromRemoteUrl("git@gitlab.acme.io:team/svc.git").orElseThrow().provider);
        assertEquals("github", GitMetadata.fromRemoteUrl("https://github.acme.io/team/svc").orElseThrow().provider);
        // Unknown host still parses (slug + base URL) but leaves provider unresolved.
        RepoInfo unknown = GitMetadata.fromRemoteUrl("git@scm.acme.io:team/svc.git").orElseThrow();
        assertEquals("team/svc", unknown.projectSlug);
        assertEquals("https://scm.acme.io/team/svc", unknown.baseUrl);
        assertEquals(null, unknown.provider);
    }

    @Test
    void ignoresNull() {
        assertTrue(GitMetadata.fromRemoteUrl(null).isEmpty());
    }

    @Test
    void parsesOriginUrlFromConfigLines() {
        var url = GitMetadata.parseOriginFromConfig(List.of(
                "[core]",
                "\trepositoryformatversion = 0",
                "[remote \"origin\"]",
                "\turl = git@github.com:acme/widgets.git",
                "\tfetch = +refs/heads/*:refs/remotes/origin/*",
                "[branch \"main\"]"));
        assertEquals("git@github.com:acme/widgets.git", url.orElseThrow());
    }

    @Test
    void parsesBranchFromHead() {
        assertEquals("main", GitMetadata.parseBranchFromHead(List.of("ref: refs/heads/main")).orElseThrow());
        assertEquals("feature/x",
                GitMetadata.parseBranchFromHead(List.of("ref: refs/heads/feature/x")).orElseThrow());
    }

    @Test
    void detachedHeadHasNoBranch() {
        assertTrue(GitMetadata.parseBranchFromHead(List.of("a1b2c3d4e5f6a7b8")).isEmpty());
    }

    @Test
    void buildsSourceLocationWithAndWithoutSubpath() {
        assertEquals("url:https://github.com/acme/repo/tree/main/services/orders/",
                GitMetadata.buildSourceLocation("https://github.com/acme/repo", "main", "services/orders"));
        assertEquals("url:https://github.com/acme/repo/tree/main/",
                GitMetadata.buildSourceLocation("https://github.com/acme/repo", "main", ""));
    }

    @Test
    void computesRelativeModulePath() {
        Path root = Path.of("/repo");
        assertEquals("services/orders", GitMetadata.relativeModulePath(root, Path.of("/repo/services/orders")));
        assertEquals("", GitMetadata.relativeModulePath(root, Path.of("/repo")));
        assertEquals("", GitMetadata.relativeModulePath(root, Path.of("/other/x")));
    }

    @Test
    void sourceLocationUsesBranchThenFallback() {
        RepoInfo onBranch = new RepoInfo("acme/repo", "https://github.com/acme/repo", "github", "develop", Path.of("/repo"));
        assertEquals("url:https://github.com/acme/repo/tree/develop/services/orders/",
                GitMetadata.sourceLocation(onBranch, Path.of("/repo/services/orders"), "main"));

        RepoInfo detached = new RepoInfo("acme/repo", "https://github.com/acme/repo", "github", null, Path.of("/repo"));
        assertEquals("url:https://github.com/acme/repo/tree/main/services/orders/",
                GitMetadata.sourceLocation(detached, Path.of("/repo/services/orders"), "main"));
    }

    @Test
    void stripsScmProviderPrefix() {
        assertEquals("https://github.com/org/repo.git",
                GitMetadata.stripScmPrefix("scm:git:https://github.com/org/repo.git"));
        assertEquals("git@github.com:org/repo.git",
                GitMetadata.stripScmPrefix("scm:git:git@github.com:org/repo.git"));
        // Plain URL (typical project.scm.url) passes through unchanged.
        assertEquals("https://github.com/org/repo",
                GitMetadata.stripScmPrefix("https://github.com/org/repo"));
        assertEquals(null, GitMetadata.stripScmPrefix(null));
    }

    @Test
    void discoverFallsBackToScmUrlWhenNoGitOrigin(@TempDir Path outsideGit) {
        // A temp dir is not inside a git work tree, so discovery uses the Maven <scm> fallback.
        RepoInfo r = GitMetadata.discover(outsideGit, null, null, "scm:git:https://github.com/acme/from-scm.git")
                .orElseThrow();
        assertEquals("acme/from-scm", r.projectSlug);
        assertEquals("https://github.com/acme/from-scm", r.baseUrl);
    }

    @Test
    void discoverReturnsEmptyWhenNoGitNoOverrideNoScm(@TempDir Path outsideGit) {
        assertTrue(GitMetadata.discover(outsideGit, null, null, null).isEmpty());
    }
}

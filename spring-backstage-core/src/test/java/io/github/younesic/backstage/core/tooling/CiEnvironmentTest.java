package io.github.younesic.backstage.core.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.younesic.backstage.core.tooling.CiEnvironment.CiSystem;

class CiEnvironmentTest {

    @Test
    void detectsGithubActions() {
        CiEnvironment ci = CiEnvironment.detect(Map.of(
                "GITHUB_ACTIONS", "true",
                "GITHUB_REPOSITORY", "acme/orders",
                "GITHUB_REF_NAME", "main"));
        assertEquals(CiSystem.GITHUB, ci.system());
        assertEquals("main", ci.branch());
        assertEquals("acme/orders", ci.var("GITHUB_REPOSITORY"));
    }

    @Test
    void detectsGitlabCi() {
        CiEnvironment ci = CiEnvironment.detect(Map.of(
                "GITLAB_CI", "true",
                "CI_PROJECT_PATH", "group/sub/orders",
                "CI_COMMIT_REF_NAME", "release"));
        assertEquals(CiSystem.GITLAB, ci.system());
        assertEquals("release", ci.branch());
        assertEquals("group/sub/orders", ci.var("CI_PROJECT_PATH"));
    }

    @Test
    void detectsJenkinsAndStripsRemotePrefixFromGitBranch() {
        CiEnvironment fromBranchName = CiEnvironment.detect(Map.of(
                "JENKINS_URL", "https://jenkins.example.com/",
                "BRANCH_NAME", "feature-x"));
        assertEquals(CiSystem.JENKINS, fromBranchName.system());
        assertEquals("feature-x", fromBranchName.branch());

        // GIT_BRANCH appears as origin/main, remotes/origin/main, or a multi-segment branch.
        assertEquals("main", branchFromGit("origin/main"));
        assertEquals("main", branchFromGit("remotes/origin/main"));
        assertEquals("feature/x", branchFromGit("origin/feature/x"));
        assertEquals("feature/x", branchFromGit("remotes/origin/feature/x"));
        assertEquals("main", branchFromGit("main"));
    }

    private static String branchFromGit(String gitBranch) {
        return CiEnvironment.detect(Map.of(
                "JENKINS_URL", "https://jenkins.example.com/",
                "GIT_BRANCH", gitBranch)).branch();
    }

    @Test
    void noneOutsideCi() {
        CiEnvironment ci = CiEnvironment.detect(Map.of("PATH", "/usr/bin"));
        assertEquals(CiSystem.NONE, ci.system());
        assertNull(ci.branch());
    }

    @Test
    void nullAndBlankSignaturesDegradeToNone() {
        assertEquals(CiSystem.NONE, CiEnvironment.detect(null).system());
        assertEquals(CiSystem.NONE, CiEnvironment.detect(Map.of("GITHUB_ACTIONS", "false")).system());
        assertEquals(CiSystem.NONE, CiEnvironment.detect(Map.of("JENKINS_URL", "  ")).system());
    }

    @Test
    void varReadsAndTrimsArbitraryNames() {
        CiEnvironment ci = CiEnvironment.detect(Map.of("SONAR_PROJECT_KEY", "  acme_orders  "));
        assertEquals("acme_orders", ci.var("SONAR_PROJECT_KEY"));
        assertNull(ci.var("MISSING"));
        assertNull(ci.var(null));
    }
}

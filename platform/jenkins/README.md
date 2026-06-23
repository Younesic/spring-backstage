# Using the generator with Jenkins (primary CI)

This is the reference setup for the common enterprise stack: **GitLab for git hosting, Jenkins for CI,
an internal Maven registry (Nexus / Artifactory) for artifacts.** The generator is CI-agnostic — it runs
at Maven build time and reads the pom + git — so Jenkins is a first-class target. Jenkins is detected via
`JENKINS_URL`; the branch comes from `BRANCH_NAME`/`GIT_BRANCH`; the SCM provider (so the slug key is
`gitlab.com/project-slug`) is inferred from the **git remote host**, not from the CI system — which is
exactly right, since Jenkins builds both GitLab and GitHub repos.

## Prerequisites (Jenkins)

- **Config File Provider** plugin: store [`settings.xml`](./settings.xml) as a managed *Global Maven
  settings.xml* with file id `maven-settings` (server id `internal` → your Nexus/Artifactory).
- **Credentials**:
  - `nexus-deploy` / `nexus-read` — username/password for the internal registry (bound to
    `NEXUS_USER`/`NEXUS_PASS`).
  - `gitlab-bot` — a GitLab **project/group access token** with `write_repository`, used to push the
    refreshed descriptor back.
- Docker agent (the examples use the `maven:3.9-eclipse-temurin-17` image).

## 1. Publish the generator (once)

Fork the generator into GitLab and add [`Jenkinsfile.publish`](./Jenkinsfile.publish) as its `Jenkinsfile`.
On `main` it deploys the 3 library modules to your internal registry
(`-DaltDeploymentRepository=internal::<url>`). Set the `MAVEN_DEPLOY_URL` parameter to your repo.

## 2. Regenerate per service

In each service add [`Jenkinsfile.service`](./Jenkinsfile.service) as its `Jenkinsfile`:

1. `mvn process-classes` resolves the plugin from `internal` and writes `catalog-info.generated.yaml`.
2. On `main`, if it changed, the job commits and pushes it back to GitLab with the `gitlab-bot` token.
3. Your central catalog ingestion picks up the committed file.

The service pom needs the `internal` registry as a `<repository>` + `<pluginRepository>` (or inherit the
plugin from your corporate parent POM — see the root README "Zero-touch distribution"), and the
`@BackstageComponent` annotation on the application class.

## 3. CI-derived tooling values

The `environment { }` block in `Jenkinsfile.service` exports the real identifiers
(`SONAR_PROJECT_KEY`, `ARGOCD_APP_NAME`, `HARBOR_REPOSITORY`, `DTRACK_*`) so the annotations reflect
actual values and not the convention fallback. Map your own variable names via the plugin
`<toolingAnnotations>` if they differ.

## Notes

- **Detached HEAD**: Jenkins often checks out a SHA; `BRANCH_NAME`/`GIT_BRANCH` keep `source-location`
  pointing at the right branch.
- **Self-hosted GitLab/GitHub host** with an unusual name: force the slug provider with
  `-Dbackstage.scmProvider=gitlab` (or `<scmProvider>` in the plugin config).
- Nexus/Artifactory are interchangeable — only the `internal` server URL/credentials change.

# Using the generator in a GitLab organization

The generator is SCM-agnostic. On GitLab it auto-detects `GITLAB_CI` and emits the GitLab-flavoured
annotations (`gitlab.com/project-slug`, branch from `CI_COMMIT_REF_NAME`, `backstage.io/source-location`
pointing at your GitLab host). Nothing about the catalog derivation is GitHub-specific; only the
*distribution* differs, and these files cover it.

## 1. Host the artifact internally (don't clone-and-build per service)

Fork/clone this repo into your GitLab, then publish it **once** to your GitLab Package Registry — every
service then resolves the plugin as a normal Maven dependency.

- Add [`.gitlab-ci.publish.yml`](./.gitlab-ci.publish.yml) to the generator fork's `.gitlab-ci.yml`.
- It deploys with the built-in `CI_JOB_TOKEN` (no PAT) to
  `<CI_SERVER_URL>/api/v4/projects/<id>/packages/maven`.
- Prefer a **group-level** registry endpoint so all repos in the group can read it:
  `<CI_SERVER_URL>/api/v4/groups/<group-id>/-/packages/maven`.

> Nexus / Artifactory work too — point `distributionManagement` (or `-DaltDeploymentRepository`) and the
> consumer `<repositories>`/`<pluginRepositories>` at your registry instead.

## 2. Consume from a service

In the service `pom.xml`, add the GitLab registry as a plugin + dependency repository (id `gitlab-maven`),
and add the annotations dependency + the plugin (or inherit both from your corporate parent POM — see the
root README "Zero-touch distribution"). Maven auth uses [`settings.xml`](./settings.xml) (CI_JOB_TOKEN).

Then add [`.gitlab-ci.service.yml`](./.gitlab-ci.service.yml) to regenerate and commit the descriptor on
the default branch.

### Central ingestion — two options

**Option A — self-registration (recommended when repos are spread across many groups).** The CI job
tells Backstage where its descriptor is (`POST /api/catalog/locations`), so it works no matter which
group the repo lives in — no central scan config to maintain. It's already wired (opt-in) in
`.gitlab-ci.service.yml`: set the `BACKSTAGE_URL` and `BACKSTAGE_TOKEN` CI variables. Idempotent.

**Option B — group discovery (pull).** Point Backstage at the team GitLab group(s): paste
[`backstage-discovery.gitlab.yaml`](./backstage-discovery.gitlab.yaml) into your `app-config.yaml`. A
group scan includes subgroups; in prod use one provider entry per team group. Good when teams are tidy
under known groups; heavier if you scan broadly. The `younesic` scan is PoC-only.

## 3. Self-hosted GitLab / GitHub Enterprise

Provider detection keys on the remote host (`*gitlab*` → gitlab, `*github*` → github), then the CI system
(`GITLAB_CI`/`GITHUB_ACTIONS`). For an unusual host name, force it explicitly:

```xml
<configuration>
  <scmProvider>gitlab</scmProvider>   <!-- or github; default auto -->
</configuration>
```
or `-Dbackstage.scmProvider=gitlab`.

## 4. CI-derived tooling values

Export your real values in the job so they win over the conventions (see the commented `variables:` block
in `.gitlab-ci.service.yml`): `SONAR_PROJECT_KEY`, `ARGOCD_APP_NAME`, `HARBOR_REPOSITORY`,
`DTRACK_PROJECT_NAME`/`…_VERSION`. Map your own variable names via the plugin `<toolingAnnotations>` if
they differ.

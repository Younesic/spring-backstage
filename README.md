# Backstage Component Generator

Declare your service's Backstage catalog entity **in code**, the way `springdoc-openapi` declares
your API — then let a Maven plugin generate a Backstage entity file at build time. A **central GitHub
discovery** ingests it from Git; there is **no `Location` entity** and **no per-repo registration**.

> 👉 **Just want to use it?** See **[USAGE.md](./USAGE.md)** — the practical 3-step guide, annotation
> reference, and recipes. This README covers the design, ingestion strategy, and platform integration.

```java
@SpringBootApplication
@BackstageComponent(
    owner = "team-payments",
    lifecycle = Lifecycle.PRODUCTION,
    system = "checkout",
    tags = {"java", "spring-boot"})
public class OrdersServiceApplication { /* ... */ }
```

→ `mvn package` produces, at the configured path (default repo root `catalog-info.generated.yaml`):

```yaml
apiVersion: backstage.io/v1alpha1
kind: Component
metadata:
  name: orders-service                         # derived from spring.application.name
  description: Handles customer orders for the checkout flow.
  annotations:
    github.com/project-slug: my-org/orders-service
    backstage.io/source-location: url:https://github.com/my-org/orders-service/tree/main/
    spring-backstage.io/generated: "true"      # sentinel: this entity is tool-owned
    spring-backstage.io/build-version: 1.4.0
  tags: [java, spring-boot]
spec:
  type: service
  lifecycle: production
  owner: group:default/team-payments           # bare 'team-payments' normalized to this
  system: checkout
  providesApis: [orders-service-api]           # added because springdoc is present
---
apiVersion: backstage.io/v1alpha1
kind: API
metadata:
  name: orders-service-api
  annotations:
    spring-backstage.io/generated: "true"
spec:
  type: openapi
  lifecycle: production
  owner: group:default/team-payments
  system: checkout
  definition:
    $text: ./openapi.yaml
```

## How it works (Option B — central discovery)

- **Build-time, not runtime.** No Actuator endpoint serves the YAML. The plugin generates the file
  at compile/package time; it is committed to Git and read by the platform team's GitHub discovery.
- **Scope = `Component` + `API` only.** `Group`/`User`/`System`/`Resource` are owned elsewhere
  (team catalogs / org location / IdP). The tool **references** them by entity ref and **never**
  creates them or modifies any hand-maintained file.
- **No `Location` emitted.** Ingestion is the central discovery's job — that is the whole point of
  this approach.
- **The output file is owned 100% by the tool**, rewritten in full each run (idempotent, no orphans).

## Modules

| Module | Purpose |
|--------|---------|
| `spring-backstage-annotations` | `@BackstageComponent` + `Lifecycle` (zero deps, CLASS retention). |
| `spring-backstage-core` | Backstage v1alpha1 model, multi-document YAML serializer, field derivation. Build-tool agnostic. |
| `spring-backstage-maven-plugin` | The `generate-catalog-info` mojo (binds to `process-classes`). |
| `examples/orders-service` | A runnable Spring Boot example with springdoc. |
| `platform/` | Platform-team deliverables: discovery `app-config.yaml` snippet + Software Template. |

Requirements: **Java 17+**, **Maven 3.6.3+**.

## Installation (service team)

```xml
<dependency>
  <groupId>io.github.younesic.backstage</groupId>
  <artifactId>spring-backstage-annotations</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```
```xml
<plugin>
  <groupId>io.github.younesic.backstage</groupId>
  <artifactId>spring-backstage-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <executions>
    <execution><goals><goal>generate-catalog-info</goal></goals></execution>
  </executions>
</plugin>
```

Put `@BackstageComponent` on the `@SpringBootApplication` class (once, for the whole service — not on
each bean). `owner` and `lifecycle` are **required**; the rest is optional or derived. Commit the
generated file (or let CI do it — see the skeleton's `.github/workflows/spring-backstage.yml`) so
discovery can read it.

## Choosing the ingestion mode

> **Ask the platform team: "what is our GitHub discovery `catalogPath`?"**

| | Mode 1 (default) | Mode 2 |
|---|---|---|
| `outputPath` | `catalog-info.generated.yaml` (repo root) | `catalog/catalog-info.yaml` (sub-folder) |
| Central change | widen `catalogPath` to `/**/catalog-info*.yaml` | **none** if already `/**/catalog-info.yaml` |

- **Mode 1 (default):** the plugin writes `catalog-info.generated.yaml` at the root; the platform team
  widens the glob (the `*` in `catalog-info*.yaml` matches `.generated`). Keep the glob precise and
  validate on a subset of repos first (a broad org-wide wildcard reads every repo's tree each scan).
- **Mode 2:** if discovery is already recursive (`/**/catalog-info.yaml`), set
  `<outputPath>catalog/catalog-info.yaml</outputPath>` — picked up with zero central change and
  without colliding with the repo-root human file.

The exact `app-config.yaml` provider snippet (both modes, with the verified caveats) and the backend
registration are in [`platform/discovery/app-config.discovery.yaml`](./platform/discovery/app-config.discovery.yaml).

### Ownership rule (important)

The human `catalog-info.yaml` and the generated file are ingested as **two separate locations**. If
both declared the same `kind`+`namespace`+`name`, Backstage raises *"conflicting entityRef"* and
orphans one. The tool only emits `Component`/`API`; **the human file must never re-declare those
names.**

## Derived vs required fields

**Required (annotation — governance concepts absent from code):**

| Field | Annotation | Notes |
|-------|-----------|-------|
| `spec.owner` | `owner` | Bare `team-x` → `group:default/team-x`; full refs (`group:…`, `user:…`) kept. Only the **format** is validated, not existence — a missing Group becomes a dangling ref in Backstage, not a build error. |
| `spec.lifecycle` | `lifecycle` | `EXPERIMENTAL` \| `PRODUCTION` \| `DEPRECATED`. |

Missing/blank/malformed `owner` or missing `lifecycle` **fails the build** with an actionable message.

**Optional (annotation overrides any derived value):** `name`, `type` (default `service`), `system`,
`description`, `tags`, `dependsOn` (manual only), `emitApi`.

**Derived automatically:** `metadata.name` (`spring.application.name` → `artifactId`, normalized to
lowercase kebab ≤63; unresolved `@...@` placeholders ignored); `github.com/project-slug` and
`backstage.io/source-location` (git `origin`); `backstage.io/techdocs-ref: dir:.` (if `mkdocs.yml`);
`spring-backstage.io/build-version`. **No timestamp** is emitted (it would break idempotence).

## springdoc synergy → API entity

If `org.springdoc:springdoc-openapi-*` is on the build, the plugin also emits a `kind: API`
(`spec.type: openapi`, `definition.$text: ./openapi.yaml`) and adds `spec.providesApis` to the
Component. Disable with `@BackstageComponent(emitApi = false)` or `-Dbackstage.emitApi=false`.

springdoc has no static export — its Maven plugin boots the app and GETs `/v3/api-docs.yaml`. The
example wires this behind a profile: `mvn -Popenapi-export verify`. (`$text: ./openapi.yaml` resolves
when the file is served via a git integration — i.e. the discovery path — not for local `file:`
locations.)

## Tooling annotations (autonomous derivation)

Beyond the catalog basics, the plugin derives the **integration annotations** other Backstage plugins
read (SonarQube, ArgoCD, Harbor, Dependency-Track) — with **zero per-project config**. Each key is
resolved by precedence — **most specific wins, first non-blank source kept, otherwise the key is
omitted**. A missing tooling annotation **never fails the build** (only `owner`/`lifecycle` do):

1. **Explicit** — `@BackstageComponent(annotations = {"argocd/app-name=orders-prod"})` (highest).
2. **CI environment** — standard variables of the detected CI system (below) + the Maven model
   (`groupId`, `artifactId`, `version`, `<scm>`).
3. **Org convention** — a template configured in the (parent) POM, e.g. `{groupId}_{artifactId}`.

### Built-in registry (defaults)

| Annotation key | Env source | Convention |
|---|---|---|
| `sonarqube.org/project-key` | `SONAR_PROJECT_KEY` | `{groupId}_{artifactId}` |
| `argocd/app-name` | — | `{artifactId}` |
| `goharbor.io/repository-slug` | — *(convention only — see note)* | `{harborProject}/{artifactId}` |
| `dependencytrack/project-name` | — | `{artifactId}` |
| `dependencytrack/project-version` | — | `{version}` |
| `dependencytrack/project-id` | `DTRACK_PROJECT_ID` | — *(opt-in: omitted unless the env is set)* |

> **Harbor note.** The Harbor plugin expects a bare `project/repository` slug **without** the registry
> host. GitLab's `CI_REGISTRY_IMAGE` carries the host (`registry.gitlab.com/group/proj`), which the
> plugin can't resolve, so it is deliberately **not** used as a source — the `{harborProject}/{artifactId}`
> convention is authoritative. Set `harborProject` (or override the rule) to match your Harbor project.

Keys are the **bare** keys those plugins read (not under `annotationPrefix`). Tooling annotations are
**component-scoped** — emitted on the Component only, never on the derived API/Resource. Values stay
**stable** across rebuilds (no timestamp/SHA), so the file is idempotent. Templates accept
`{groupId} {artifactId} {version} {branch} {harborProject}`; an unresolved placeholder **omits** that
one annotation rather than emitting a literal `{placeholder}`.

### CI system detection

Detected from the environment, selecting the right variable set:

| System | Signature | Repo slug | Branch | Image |
|---|---|---|---|---|
| GitHub Actions | `GITHUB_ACTIONS=true` | `GITHUB_REPOSITORY` | `GITHUB_REF_NAME` | — |
| GitLab CI | `GITLAB_CI=true` | `CI_PROJECT_PATH` | `CI_COMMIT_REF_NAME` | `CI_REGISTRY_IMAGE` |
| Jenkins | `JENKINS_URL` set | — | `BRANCH_NAME` / `GIT_BRANCH` | — |

Outside CI everything falls back to git + convention. **No CODEOWNERS** is ever read — `owner` is
always the declared `@BackstageComponent.owner`.

### Extending / overriding the registry

Add a tool, override a default's source, or disable one — via plugin config (merged **by key**):

```xml
<configuration>
  <harborProject>acme</harborProject>            <!-- {harborProject} for the harbor convention -->
  <toolingAnnotations>
    <toolingAnnotation>                          <!-- add a new key -->
      <key>backstage.io/kubernetes-id</key>
      <convention>{artifactId}</convention>
    </toolingAnnotation>
    <toolingAnnotation>                          <!-- override a default's convention -->
      <key>argocd/app-name</key>
      <convention>{artifactId}-prod</convention>
    </toolingAnnotation>
    <toolingAnnotation>                          <!-- disable a default -->
      <key>sonarqube.org/project-key</key>
      <enabled>false</enabled>
    </toolingAnnotation>
  </toolingAnnotations>
</configuration>
```

### Dependency-Track runtime value (UUID)

The project UUID only exists **after** the build (created when the SBOM is uploaded), so the plugin
never tries to capture it. Three levels, in order of preference:

1. **Stable identifier (default)** — emit `dependencytrack/project-name` + `dependencytrack/project-version`;
   resolvable by name post-ingestion, no runtime value needed.
2. **Backstage delegation (optional, documented contract — not shipped here)** — a platform-side
   processor/Entity-Provider resolves the UUID by name after ingestion: **in** `dependencytrack/project-name`
   (+ `…/project-version`) → query Dependency-Track by name → **out** `dependencytrack/project-id`.
   Centralised on the platform, zero impact on product teams.
3. **Opt-in escape hatch** — a pipeline that wants to pin the exact UUID sets `DTRACK_PROJECT_ID`; the
   plugin reads it **if present**. Off by default; imposes nothing on others.

The plugin **never post-processes** the generated YAML (`sed`/`yq`/`echo >>`) — that would break
idempotence and couple CI to the file structure.

## Relations & owned entities

**Source-of-truth rule: generate what the code owns, reference the rest.** The generator emits only
entities this service owns; everything else is a *reference* (entity ref), validated for **format
only** — a dangling ref resolves in Backstage, it is never a build error.

| | Direction | Owned by | Behaviour |
|---|---|---|---|
| `providesApis` | exposes | this service | **Generated** `API` from springdoc (+ explicit `providesApis = {...}`, merged/deduped). |
| `consumesApis` | calls | other services | **Referenced.** `consumesApis = {"api:default/payments"}` → `spec.consumesApis`. Never creates the API. |
| `dependsOn` | uses | elsewhere | **Referenced.** `dependsOn = {"resource:default/db", "component:default/x"}`. |
| `@BackstageResource` | owns | this service | **Generated** `Resource` (opt-in, see below). |

Bare names are normalized (`payments` → `api:default/payments`).

### Owned Resources (`@BackstageResource`) — opt-in

Repeatable on the Component class. When present, the generator emits a `kind: Resource`, wires
`spec.dependsOn: [resource:default/<name>]` on the Component, and the Resource **inherits**
`owner`/`system` from the Component unless overridden (Backstage Resources have no `lifecycle`).

```java
@BackstageComponent(owner = "team-payments", lifecycle = Lifecycle.PRODUCTION, system = "checkout",
                    consumesApis = {"api:default/payments"})
@BackstageResource(name = "orders-db", type = "database")   // service-owned
public class OrdersApplication { ... }
```

> ⚠️ **Dedicated resources ONLY.** Declare `@BackstageResource` *only* for resources this service owns
> (its own DB/queue/bucket provisioned with it). **Never** for shared infrastructure (a shared Kafka, a
> team database, an org bucket) — two services declaring the same shared Resource produce
> duplicate/conflicting catalog entities. For shared things, **reference** them via `dependsOn`.
> There is deliberately **no inference** from the datasource/Spring config: ownership is declared
> explicitly. No `@BackstageResource` → no Resource emitted (the default stays "reference only").

### Consumed-API inference from `@FeignClient` (reserved)

`-Dbackstage.inferConsumedApis=true` is **reserved** and currently a no-op (logs a warning). A future
heuristic would map each `@FeignClient` to an `api:default/<name>` ref, but it is fragile — the target
API must exist in the catalogue and the client-name→API-name mapping is not reliable — so it is
deferred and off by default. Declare `consumesApis` explicitly for now.

## Goals

| Goal | Runs | Purpose |
|------|------|---------|
| `generate-catalog-info` | per module (bound to `process-classes`) | Write each annotated module's own file. **Default.** |
| `generate-catalog-info-aggregate` | once, reactor-wide (`aggregator`) | Write **one** root file with all entities — for root-only discovery. |
| `check-catalog-names` | once, reactor-wide (`aggregator`) | CI gate: fail on duplicate names. Writes nothing. |

## Plugin configuration

| Parameter | Property | Default |
|-----------|----------|---------|
| `outputPath` | `backstage.outputPath` | `catalog-info.generated.yaml` (per module; parent dirs created) |
| `annotationPrefix` | `backstage.annotationPrefix` | `spring-backstage.io` |
| `apiDefinitionRef` | `backstage.apiDefinitionRef` | `./openapi.yaml` |
| `emitApi` | `backstage.emitApi` | `true` |
| `sourceLocationBranch` | `backstage.sourceLocationBranch` | current branch (`HEAD`), else `main` |
| `repoBaseUrl` | `backstage.repoBaseUrl` | derived from git `origin` (override for CI) |
| `failOnMissingAnnotation` | `backstage.failOnMissingAnnotation` | `false` (no-op if no annotation) |
| `inferConsumedApis` | `backstage.inferConsumedApis` | `false` (reserved — Feign inference, not yet implemented) |
| `toolingAnnotations` | *(nested `<toolingAnnotation>` rules)* | built-in registry (Sonar/ArgoCD/Harbor/Dtrack); merged by key |
| `harborProject` | `backstage.harborProject` | last segment of `groupId` |
| `skip` | `backstage.skip` | `false` |
| `aggregateOutputPath` *(aggregate)* | `backstage.aggregateOutputPath` | `catalog-info.generated.yaml` (repo root) |
| `failOnDuplicateName` *(aggregate/check)* | `backstage.failOnDuplicateName` | `true` |
| `inferDependencies` *(aggregate)* | `backstage.inferDependencies` | `false` |

## Multi-module / monorepo

A repo may contain many Components. Bind the plugin **once** in the parent POM; it then runs per module
and is inherited by every child:

- **Per-module (default).** Each module carrying `@BackstageComponent` writes its own
  `catalog-info.generated.yaml` in its own directory; the recursive discovery glob
  (`/**/catalog-info*.yaml`) picks them all up — **no aggregation needed**.
  - `pom` modules (parent/BOM/aggregator) are always skipped (warned if annotated by mistake).
  - Unannotated modules are a silent no-op.
  - `metadata.name` derives from the module **`artifactId`** (not `spring.application.name`).
  - `backstage.io/source-location` points at each module's sub-folder
    (`url:<repo>/tree/<branch>/<module-path>/`); `github.com/project-slug` stays the repo.
  - A springdoc module gets its **own** `API` entity; two springdoc modules → two APIs.

- **Aggregate (opt-in).** For a root-only (non-recursive) discovery, generate one root file instead.
  Aggregator goals must run **after** the modules are compiled:
  ```bash
  mvn -DskipTests package
  mvn spring-backstage:generate-catalog-info-aggregate          # → <root>/catalog-info.generated.yaml
  mvn spring-backstage:generate-catalog-info-aggregate -Dbackstage.inferDependencies=true
  ```
  With `inferDependencies`, a direct Maven dependency on a *sibling annotated* module becomes
  `spec.dependsOn: [component:default/<name>]` (external/transitive deps are never inferred).

- **Global name uniqueness.** Names must be unique per kind across the repo. The aggregate goal checks
  this; in per-module mode each module can't see its peers, so run the CI gate:
  ```bash
  mvn -DskipTests package spring-backstage:check-catalog-names   # fails listing the colliding modules
  ```

See [`examples/monorepo-demo/`](./examples/monorepo-demo/) (parent pom + two services + a `type=library`
lib + a non-catalogued lib) and [`examples/monorepo-collision/`](./examples/monorepo-collision/).

## Zero-touch distribution (corporate parent POM)

The intended adoption is **inheritance, not per-project wiring**: bind the plugin once in the
**corporate parent POM** (the one teams already extend) so every service inherits it on a standard
phase — teams add nothing. A ready-to-paste block lives in
[`platform/parent-pom/pluginManagement-snippet.xml`](./platform/parent-pom/pluginManagement-snippet.xml):
it pins the version in `<pluginManagement>` and binds an execution to **`verify`**, with the default
tooling registry + conventions in `<configuration>`. Because Maven allows only **one** `<parent>`, we
don't ship our own parent — we insert into the corporate one. Per-project `<plugin>` blocks (as in the
examples) remain valid for repos without a shared parent.

## Golden path (Software Template)

[`platform/software-template/`](./platform/software-template/) contains a
`scaffolder.backstage.io/v1beta3` template that scaffolds a new Spring Boot service already wired with
the generator (pom + annotated `@SpringBootApplication` + a CI workflow that regenerates and commits
the descriptor). It is **discovery-first**: `fetch:template` → `publish:github`, with **no
`catalog:register`** (the central discovery picks the repo up — registering would create a redundant
`Location`).

## Build & test

```bash
mvn install                                       # build + test all modules
mvn -pl examples/orders-service verify            # end-to-end on the example
mvn -Popenapi-export -pl examples/orders-service verify   # also export the live openapi.yaml
```

The example test asserts the generated Component+API are well-formed, that **no `Location` is
emitted**, and that the human `catalog-info.yaml` is untouched. Core tests cover name/owner/tag
normalization, owner-ref **format** validation, git URL parsing, springdoc detection, multi-document
serialization (annotation values stay strings), and deterministic output.

## Annex — alternative integration via `Location` (Option A)

If you cannot use central discovery, the same generated file can be wired by adding a `kind: Location`
(`spec.targets: [./catalog-info.generated.yaml]`) to a hand-maintained `catalog-info.yaml`, once, per
repo. This project does **not** generate that Location; Option B (discovery) is the recommended path.

## Attribution

The YAML serialization recipe and git-URL parsing approach were derived from the Apache-2.0
[`quarkiverse/quarkus-backstage`](https://github.com/quarkiverse/quarkus-backstage). See
[`NOTICE`](./NOTICE).

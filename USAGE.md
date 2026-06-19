# Using the Backstage Catalog Generator

Generate your service's Backstage catalog entities (`Component`, `API`, owned `Resource`) **from
annotations in your code**, at build time — the way `springdoc-openapi` generates your API spec. No
runtime endpoint: the plugin writes a `catalog-info.generated.yaml` that your platform team's GitHub
discovery ingests from Git.

It ships as **two artifacts** you use together:

| Artifact | What it is |
|---|---|
| `io.github.younesic.backstage:spring-backstage-annotations` | The annotations you put on your code (`@BackstageComponent`, `@BackstageResource`). |
| `io.github.younesic.backstage:spring-backstage-maven-plugin` | The Maven plugin that reads them and writes the descriptor. |

> Both must be resolvable from your Maven repository (Maven Central or your org's internal repo).
> Examples below use version `0.1.0-SNAPSHOT`.

**Requirements:** Java 17+, Maven 3.6.3+.

---

## Quick start (3 steps)

### 1. Add the annotations dependency

```xml
<dependency>
  <groupId>io.github.younesic.backstage</groupId>
  <artifactId>spring-backstage-annotations</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Add the plugin

```xml
<plugin>
  <groupId>io.github.younesic.backstage</groupId>
  <artifactId>spring-backstage-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <executions>
    <execution>
      <goals><goal>generate-catalog-info</goal></goals>
    </execution>
  </executions>
</plugin>
```

The goal binds to `process-classes`, so it runs on every `mvn package`.

### 3. Annotate your application class

```java
import io.github.younesic.backstage.annotations.BackstageComponent;
import io.github.younesic.backstage.annotations.Lifecycle;

@SpringBootApplication
@BackstageComponent(owner = "team-payments", lifecycle = Lifecycle.PRODUCTION)
public class OrdersApplication { ... }
```

`owner` and `lifecycle` are the only required fields. Then:

```bash
mvn -q process-classes && cat catalog-info.generated.yaml
```

You should see a `kind: Component`. Done — commit the file (or let CI do it) so discovery picks it up.

---

## What gets generated

```yaml
apiVersion: backstage.io/v1alpha1
kind: Component
metadata:
  name: orders                                   # from the Maven artifactId (unless name=...)
  annotations:
    github.com/project-slug: my-org/orders        # from the git origin
    backstage.io/source-location: url:https://github.com/my-org/orders/tree/main/
    spring-backstage.io/generated: "true"         # sentinel: this entity is tool-owned
spec:
  type: service
  lifecycle: production
  owner: group:default/team-payments              # bare 'team-payments' is normalized
```

The file is **rewritten in full** every build (idempotent — same inputs, byte-identical output).

---

## The annotations

### `@BackstageComponent` (once, on the application class)

| Field | Required | Maps to | Notes |
|---|---|---|---|
| `owner` | ✅ | `spec.owner` | Group ref. Bare `team-x` → `group:default/team-x`; full refs kept. Format-validated, existence not checked. |
| `lifecycle` | ✅ | `spec.lifecycle` | `EXPERIMENTAL` / `PRODUCTION` / `DEPRECATED`. |
| `name` | | `metadata.name` | Defaults to the Maven `artifactId` (normalized to lowercase kebab ≤ 63). |
| `type` | | `spec.type` | Default `service`. Use `library`, `website`, … |
| `system` | | `spec.system` | Reference to an existing System. |
| `description` | | `metadata.description` | |
| `tags` | | `metadata.tags` | Normalized to the Backstage tag charset. |
| `dependsOn` | | `spec.dependsOn` | Refs to existing entities, e.g. `{"resource:default/db", "component:default/x"}`. |
| `consumesApis` | | `spec.consumesApis` | APIs you call, e.g. `{"api:default/payments"}`. **Referenced, never created.** |
| `providesApis` | | `spec.providesApis` | Extra APIs you expose (merged with the springdoc one). |
| `emitApi` | | — | Default `true`. Set `false` to suppress the springdoc API entity. |
| `annotations` | | tooling annotations | `"key=value"` overrides, e.g. `{"argocd/app-name=orders-prod"}`. Highest precedence over env/convention. |

### `@BackstageResource` (repeatable, opt-in) — resources your service owns

```java
@BackstageComponent(owner = "team-payments", lifecycle = Lifecycle.PRODUCTION, system = "checkout")
@BackstageResource(name = "orders-db", type = "database")
@BackstageResource(name = "orders-events", type = "queue")
public class OrdersApplication { ... }
```

Each emits a `kind: Resource`, auto-wires `dependsOn: [resource:default/<name>]` on the Component, and
inherits `owner`/`system` from the Component (override per-resource if needed).

| Field | Required | Notes |
|---|---|---|
| `name` | ✅ | Scope it to the service (`orders-db`, not `postgres`). |
| `type` | ✅ | `database`, `queue`, `bucket`, … |
| `owner` / `system` | | Inherited from the Component unless set. |
| `description` | | |

> ⚠️ **Dedicated resources only.** Declare `@BackstageResource` for things this service *owns* (its own
> DB/queue provisioned with it). **Never** for shared infrastructure (a shared Kafka, a team database) —
> two services declaring the same Resource create conflicting catalog entities. For shared things, just
> **reference** them with `dependsOn`. There is no auto-detection from your datasource config.

---

## Recipes

**Expose an API (springdoc).** Add springdoc and you get a `kind: API` + `providesApis` automatically:
```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.6.0</version>
</dependency>
```
The API's `spec.definition` is `$text: ./openapi.yaml`. To produce that file, export the live spec
(boot the app + springdoc's Maven plugin) in CI — see the example's `openapi-export` profile.

**Consume APIs and depend on things:**
```java
@BackstageComponent(owner = "team-payments", lifecycle = Lifecycle.PRODUCTION,
    consumesApis = {"api:default/payments"},
    dependsOn   = {"resource:default/shared-cache"})   // shared infra → reference, don't own
```

**Monorepo (many Components).** Bind the plugin once in the parent POM. Each module with
`@BackstageComponent` writes its own file; `pom` and unannotated modules are skipped. The recursive
discovery glob picks them all up — nothing else to do. Names must be unique across the repo:
```bash
mvn -DskipTests package spring-backstage:check-catalog-names   # CI gate: fails on duplicate names
```

**Root-only discovery?** Generate one file at the repo root instead:
```bash
mvn -DskipTests package
mvn spring-backstage:generate-catalog-info-aggregate           # → <root>/catalog-info.generated.yaml
mvn spring-backstage:generate-catalog-info-aggregate -Dbackstage.inferDependencies=true   # + inter-module dependsOn
```

**Tooling annotations (Sonar/ArgoCD/Harbor/Dependency-Track).** Derived automatically — no config
needed. Precedence per key: explicit `annotations` override → CI env var → org convention; an
unresolved key is simply omitted (never a build error). Defaults:

Env-first: the real CI value wins; the convention is only a fallback guess.

| Key | Env (real CI value) | Convention (fallback) |
|---|---|---|
| `sonarqube.org/project-key` | `SONAR_PROJECT_KEY` | `{groupId}_{artifactId}` |
| `argocd/app-name` | `ARGOCD_APP_NAME` | `{artifactId}` |
| `goharbor.io/repository-slug` | `HARBOR_REPOSITORY` (host-free slug) | `{harborProject}/{artifactId}` |
| `dependencytrack/project-name` / `…/project-version` | `DTRACK_PROJECT_NAME` / `DTRACK_PROJECT_VERSION` | `{artifactId}` / `{version}` |
| `dependencytrack/project-id` | `DTRACK_PROJECT_ID` | — (opt-in) |

CI system auto-detected (GitHub Actions / GitLab CI / Jenkins). **No CODEOWNERS** is read.
Dependency-Track: the default emits the stable name+version (the UUID is created post-build); set
`DTRACK_PROJECT_ID` to pin the exact UUID, or resolve it Backstage-side by name. Override/extend via the
plugin's `<toolingAnnotations>` (merged by key); see the README for the full registry + parent-POM
distribution snippet.

---

## Plugin configuration (override defaults)

Per-module goal `generate-catalog-info`:

| Property | Default | |
|---|---|---|
| `backstage.outputPath` | `catalog-info.generated.yaml` | Where to write (relative to the module; sub-folders created). |
| `backstage.annotationPrefix` | `spring-backstage.io` | Prefix for the sentinel/tool annotations. |
| `backstage.apiDefinitionRef` | `./openapi.yaml` | `$text` ref in the API entity. |
| `backstage.emitApi` | `true` | Global switch for the springdoc API. |
| `backstage.sourceLocationBranch` | current branch | Branch in `source-location` (override for CI). |
| `backstage.repoBaseUrl` | from git origin | Override when git is unavailable. |
| `backstage.harborProject` | last `groupId` segment | `{harborProject}` for the Harbor convention. |
| `backstage.failOnMissingAnnotation` | `false` | Fail if a (non-pom) module has no annotation. |
| `backstage.skip` | `false` | Skip generation. |

Plus `<toolingAnnotations>` (nested `<toolingAnnotation>` rules, merged by key) to add/override/disable
tooling-registry entries; and `${project.scm.url}` is used as the last fallback for the repo slug /
`source-location` when git `origin` is unavailable.

Aggregate goals add `backstage.aggregateOutputPath`, `backstage.failOnDuplicateName` (default `true`),
`backstage.inferDependencies` (default `false`).

Example: write to a sub-folder named `catalog-info.yaml` (Mode 2 ingestion):
```xml
<configuration><outputPath>catalog/catalog-info.yaml</outputPath></configuration>
```

---

## How it reaches Backstage

The plugin only **generates the file** — it does not push to Backstage. Publishing is:

```
mvn package        →  writes catalog-info.generated.yaml   (commit it, or let CI commit it)
git push           →  the file is in your repo
discovery scan     →  Backstage ingests it on the next pass
```

> Ask your platform team for the GitHub discovery `catalogPath`. If it already matches your file
> (or a `/**/catalog-info*.yaml` glob), there's nothing else to do. See the project README for the
> exact `app-config.yaml` provider snippet and the golden-path Software Template.

---

## Rules & gotchas

- **The generated file is 100% tool-owned** and rewritten each build — never hand-edit it; edit the
  annotation instead. Your hand-maintained `catalog-info.yaml` is never touched.
- **It only generates what you own** — `Component`, the `API` you expose, and `@BackstageResource`s you
  declare. `owner`, `system`, consumed APIs, shared resources are **referenced**, never created.
- **References are format-validated only** — a typo'd or not-yet-existing ref is a dangling reference in
  Backstage (shown but unresolved), not a build error. The build *does* fail fast on missing `owner`/
  `lifecycle` or a malformed ref.
- **Names must be globally unique per kind.** In a monorepo, run `check-catalog-names` in CI.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| No file generated | No `@BackstageComponent` on a compiled class (silent no-op), or `pom` packaging. Run `process-classes`. Set `-Dbackstage.failOnMissingAnnotation=true` to make it explicit. |
| `owner` shows as a dangling ref in Backstage | The Group doesn't exist by that name. Use the real Group ref (e.g. `team-payments` → `group:default/team-payments`). |
| Wrong `project-slug` / `source-location` | The nearest `.git` is a parent monorepo. Give the service its own repo, or override `-Dbackstage.repoBaseUrl=...`. |
| Build fails: "Duplicate ... name" | Two modules generate the same name. Set `@BackstageComponent(name="...")` to disambiguate. |
| `inferConsumedApis` does nothing | It's reserved (Feign inference) and not yet implemented. Declare `consumesApis` explicitly. |

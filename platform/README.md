# Platform integration (Option B — central discovery)

This folder holds the two platform-team deliverables that wire the `@BackstageComponent` generator
into a Backstage instance **via central GitHub discovery** — no per-repo `Location`, no manual
registration.

```
platform/
├── discovery/app-config.discovery.yaml   # the GitHub discovery provider config (org-wide + Mode 2)
└── software-template/                     # the Spring Boot golden-path Software Template
    ├── template.yaml
    └── skeleton/                          # the rendered service (pom + annotated app + CI + app.yml)
```

## Target model: one repo per team, org-wide discovery

Each team owns its **own Git repo** containing its catalog descriptor. The platform team runs a
**single central discovery** that scans the org and ingests every team repo's descriptor:

```
team-a/svc-orders   ─┐
team-b/svc-billing  ─┤
…                    ├──►  catalog.providers.github (org-wide /**/catalog-info*.yaml)  ──►  Backstage
kratix-statestore   ─┘        (the Kratix team's repo = one publisher among many)
```

- **Application teams** publish their `Component`/`API` with this generator: a
  `catalog-info.generated.yaml` produced at build time, committed in their repo.
- **The Kratix team** publishes the infra `Resource`/`Component` it provisions into its
  `kratix-statestore` repo (the existing v2 providers). It is **one publisher among many**, not the
  only source.

> In the current PoC the discovery is scoped to `repository: kratix-statestore` simply because the
> Kratix team is the only publisher so far. The production shape is the org-wide provider in
> `discovery/app-config.discovery.yaml` (`applicationServices`), with the repo filter widened to the
> application-service repos.

## 1. Discovery config (`discovery/app-config.discovery.yaml`)

Add the `catalog.providers.github.<id>` blocks to the platform `app-config.yaml`. Choose the mode
with the service teams:

| | Mode 1 (default) | Mode 2 |
|---|---|---|
| Plugin `outputPath` | `catalog-info.generated.yaml` (repo root) | `catalog/catalog-info.yaml` (sub-folder) |
| Central change | one org-wide provider with `catalogPath: /**/catalog-info*.yaml` | none, if the glob is already `/**/catalog-info.yaml` |
| Picks up | both human + generated files (the `*` matches `.generated`) | a file named exactly `catalog-info.yaml` |

> Question to ask the platform team: **"what is the current `catalogPath` of our GitHub discovery
> provider?"** If it already ends in the exact filename `catalog-info.yaml` (as the PoC's v2 does),
> use Mode 2; otherwise add/widen an org-wide `applicationServices` provider (Mode 1).

**Verified caveats** (from Backstage source/docs):
- Use the **provider** (`catalog.providers.github`), not the legacy `github-discovery` processor — the
  legacy processor does not expand `catalogPath` wildcards. (This instance already uses the provider.)
- **`validateLocationsExist` must stay `false`** (default) with any wildcard.
- **Blast radius:** a wildcard path makes the URL reader read each matched repo's full git tree every
  cycle. Always scope with `filters.repository` (regex) and validate on a subset before org-wide
  rollout.
- **Duplicate-name collision (the #1 risk):** with `orphanStrategy: delete` and several providers
  running, two locations declaring the same `kind`+`namespace`+`name` cause *"conflicting entityRef"*
  and one is orphaned. The Kratix team owns its infra names; each app team owns its `Component`/`API`
  names — they stay disjoint by construction. The generator only ever emits `Component`/`API` and
  marks them with the sentinel annotation; the human file must never re-declare those names.

## 2. Software Template (`software-template/`)

A `scaffolder.backstage.io/v1beta3` template that scaffolds a new Spring Boot service **in its own
repo**, already wired with the generator. Register it in `app-config.yaml`:

```yaml
catalog:
  locations:
    - type: url
      target: https://github.com/Younesic/backstage-component-generator/blob/main/platform/software-template/template.yaml
      rules:
        - allow: [Template]
```

The template is **discovery-first**: `fetch:template` → `publish:github`, with intentionally **no
`catalog:register`** step (that would create a redundant `Location` for a repo the org-wide discovery
already finds). The new service appears in the catalog after CI commits `catalog-info.generated.yaml`
and the next discovery scan runs — tune `schedule.frequency` for snappier feedback.

Requires a configured `integrations.github` token (for `publish:github` and for discovery to read the
org). The skeleton's CI workflow (`.github/workflows/spring-backstage.yml`) regenerates and commits
the descriptor on every push to `main`.

## Where governance lives

In the target model an application team's repo carries the **generated `Component`/`API` only**.
`Group`/`User`/`System`/`Domain` stay centralized (e.g. `portal-catalog` + IdP) and are referenced by
entity ref — never re-declared per service repo. (The `examples/orders-service` keeps a few governance
entities locally only to stay self-contained.)

## 3. Zero-touch distribution (`parent-pom/pluginManagement-snippet.xml`)

Adoption is **inheritance, not per-project wiring**. Paste
[`parent-pom/pluginManagement-snippet.xml`](./parent-pom/pluginManagement-snippet.xml) into the
**corporate parent POM** teams already extend: it pins the plugin version in `<pluginManagement>`, binds
one execution to the standard **`verify`** phase, and carries the org defaults (annotation prefix, Harbor
project, tooling registry). Every service then inherits the generator with **no per-project action**.
Maven allows only one `<parent>`, so we insert into the corporate one rather than shipping our own.

The tooling-integration annotations (SonarQube/ArgoCD/Harbor/Dependency-Track) are derived autonomously
from the CI environment + conventions; **no CODEOWNERS** is read and the plugin **never post-processes**
the YAML. For Dependency-Track, the build emits the stable `project-name`+`project-version`; resolving
the runtime UUID is delegated to a platform-side Backstage processor (documented contract) or pinned via
the opt-in `DTRACK_PROJECT_ID` env. See the generator README for the full registry and contract.

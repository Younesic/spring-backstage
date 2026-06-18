# ${{ values.name }}

${{ values.description }}

Scaffolded from the **Spring Boot Backstage golden path**. This service is already wired with the
`@BackstageComponent` generator: a `catalog-info.generated.yaml` (a Backstage `Component`, plus an
`API` because springdoc is on the classpath) is produced at build time and committed by CI, then
picked up automatically by the central GitHub catalog discovery — no Location, no manual registration.

## Build

```bash
mvn package          # also runs the generator at process-classes
```

The Backstage descriptor is at `catalog-info.generated.yaml` (repo root). Do **not** hand-edit it —
it is owned 100% by the generator and rewritten on every build. Edit the `@BackstageComponent`
annotation on `Application.java` instead.

## How it reaches the catalog

`.github/workflows/spring-backstage.yml` regenerates and commits the descriptor on every push to
`main`. The platform team's discovery provider (`catalogPath: /**/catalog-info*.yaml`) then ingests
it on its next scan.

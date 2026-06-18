# CLAUDE.md — backstage-component-generator

Build-time generator of Backstage `Component`/`API` entities from a Spring Boot annotation
(`@BackstageComponent`), in the spirit of `springdoc-openapi`. See `README.md` for usage.

## Décisions actives

- **Emplacement** : projet Maven multi-module, dossier *frère* sous `self-prov/` (n'altère pas le
  monorepo TypeScript `backstage/`). `groupId = io.github.younesic.backstage`, version `0.1.0-SNAPSHOT`.
- **Toolchain** : JDK 21 installé, mais on compile en `release 17` (`maven.compiler.release=17`).
  Maven 3.9.9. Build complet : `mvn clean install` à la racine du projet.
- **Modules & dépendances** :
  `annotations` (zéro dep, `@Retention(CLASS)`) ← `core` (jackson-yaml + jdk8, build-tool agnostic)
  ← `maven-plugin` (ClassGraph + maven-* provided) ; `examples/orders-service` consomme le plugin
  du reactor. Le module nested `examples/orders-service` a besoin de `<relativePath>../../pom.xml</relativePath>`.
- **Lecture annotation** : ClassGraph scanne `target/classes` (bytecode, pas de chargement de classe).
  **CLASS retention suffit** — ClassGraph lit les annotations RuntimeInvisible (vérifié). Extraire les
  valeurs d'annotation **dans le scope du `try-with-resources ScanResult`** (sinon `Cannot use a
  ScanResult after it has been closed`).
- **Sérialisation YAML (Jackson)** : `MINIMIZE_QUOTES` quote bien `"true"` en string (requis par
  Backstage pour les valeurs d'annotations) — confirmé par test. Multi-doc = sérialiser chaque entité
  puis joindre et retirer le 1er `---` (ne PAS utiliser `SequenceWriter`).
- **⚠ BUG quoting numérique (vérifié, Jackson 2.17.2)** : `MINIMIZE_QUOTES` quote les booléens/mots
  spéciaux (`true/yes/no/on/off/null/~`) et les leading-zero (`'0755'`), MAIS **ne quote PAS** les
  valeurs numériques nues : `1.0` → re-parsé en **float**, `123` → **int**, `2.0` → **float**. Or
  `dependencytrack/project-version: {version}` produit exactement `1.0`/`2.0` pour une version Maven à
  deux segments, et `argocd/app-name`/`dependencytrack/project-name` produisent un int si l'artifactId
  est purement numérique → la valeur d'annotation n'est plus une string → échec de validation Backstage.
  **Fix** : ajouter `.enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)` dans
  `CatalogSerializer` (gère `1.0`/`123`/`2.0`). Note : ce flag ne couvre toujours pas `1e3`/`0x1F`
  (notation scientifique/hexa) — improbable pour version/artifactId mais possible via override
  utilisateur. Le test `CatalogSerializerTest` ne teste QUE `"true"` (déjà quoté) → faux sentiment de
  sécurité ; ajouter un cas `dependencytrack/project-version=1.0`.
  **✅ Fix appliqué** : flag `ALWAYS_QUOTE_NUMBERS_AS_STRINGS` activé dans `CatalogSerializer` + test
  `CatalogSerializerTest.numericLookingAnnotationValuesStayStrings` (couvre `1.0` et `123`).
- **Idempotence** : aucun timestamp émis ; sortie déterministe (test SHA256 identique). Le fichier
  `catalog-info.generated.yaml` est réécrit intégralement à chaque run.

## Annotations outillage — registre autonome (verrouillé)

- **But** : dériver les annotations d'intégration (SonarQube/ArgoCD/Harbor/Dependency-Track) sans config
  par projet. Package `core/tooling` : `ToolingRule` (POJO bindable Maven), `CiEnvironment`,
  `DefaultToolingRules`, `ToolingResolver`.
- **Précédence par clé** : override explicite `@BackstageComponent.annotations` (`"key=value"`) > var
  d'env CI (selon système détecté) > convention org templatée (`{groupId}_{artifactId}`…). Clé non
  résolue ⇒ **omise** (jamais d'échec build ; seuls `owner`/`lifecycle` font fail-fast).
- **Détection CI** depuis `System.getenv()` (lu **une seule fois** dans `CatalogGeneration.resolveTooling`) :
  `GITHUB_ACTIONS`/`GITLAB_CI`/`JENKINS_URL`. `CiEnvironment.detect(Map)` est injectable → testable.
- **Component-scoped** : les annotations outillage vont sur le Component **uniquement** (pas API/Resource) —
  `CatalogBuilder.annotations(req, includeTechDocs, includeTooling)`.
- **Substitution** : un placeholder `{x}` non résolu fait **omettre toute l'annotation** (pas de littéral).
  Ordre de la Map préservé (LinkedHashMap) ⇒ idempotence. `GenerationRequest.toolingAnnotations` recopié
  en `LinkedHashMap` immuable (PAS `Map.copyOf`, qui perd l'ordre).
- **Binding Maven** : `@Parameter List<ToolingRule> toolingAnnotations` ⇒ XML `<toolingAnnotations><toolingAnnotation>`
  (`key`/`env`/`convention`/`enabled`). `ToolingRule` vit dans **core** (réutilisable Gradle) mais sert de
  type de paramètre au plugin. `harborProject` défaut = dernier segment du `groupId`.
- **Dependency-Track (valeur runtime)** : défaut = identifiant **stable** `project-name`+`project-version`
  (UUID inconnu au build) ; `project-id` opt-in via env `DTRACK_PROJECT_ID` (sinon omis) ; délégation
  Backstage = **contrat documenté seulement** (pas de code TS livré). **Pas de CODEOWNERS. Pas de
  post-traitement YAML.**
- **`<scm>` fallback** : `GitMetadata.discover(start, branch, baseUrl, scmUrl)` — `${project.scm.url}`
  (préfixe `scm:<provider>:` strippé) en dernier recours pour slug/source-location.
- **Distribution zéro-touch** = snippet docs `platform/parent-pom/pluginManagement-snippet.xml`
  (`<pluginManagement>` + exécution liée à `verify`) à coller dans le parent corporate existant — pas de
  parent gouvernance livré (un seul `<parent>` Maven possible).

## Option B — intégration par discovery centrale (verrouillée)

- **Modèle cible** : 1 repo Git par équipe (chacun avec son `catalog-info`), une **discovery centrale
  org-wide** (`organization: Younesic`) qui ramasse tous les repos. Le repo `kratix-statestore` (config
  v2 du PoC `self-prov/backstage`) n'est **qu'un publisher parmi d'autres** (l'équipe Kratix, périmètre
  infra) ; le PoC est scopé `repository: kratix-statestore` car c'est le seul publisher actuel.
- **Aucune entité `Location` émise.** L'ingestion passe par la GitHub discovery centrale. Param mojo
  `outputFile` renommé en **`outputPath`** (chemin relatif configurable, crée les dossiers parents).
- **Mode 1 (défaut)** : `outputPath = catalog-info.generated.yaml` à la racine ; l'équipe plateforme
  élargit `catalogPath` à `/**/catalog-info*.yaml` (le `*` matche `.generated`).
  **Mode 2** : `outputPath = catalog/catalog-info.yaml` ramassé par un `/**/catalog-info.yaml` déjà récursif.
- **Provider, pas le processor legacy** : `catalog.providers.github.<id>` + module
  `@backstage/plugin-catalog-backend-module-github`. `validateLocationsExist` **doit rester false** avec
  un wildcard. Scoper `filters.repository` (blast radius : lecture du tree complet par repo à chaque scan).
- **Gotcha #1 — collision de noms** : le fichier humain et le fichier généré sont 2 locations ; même
  `kind+namespace+name` ⇒ "conflicting entityRef". Le tool n'émet que `Component`/`API` ; le fichier
  humain ne doit jamais redéclarer ces noms.
- **owner** : validation du **format** uniquement (ref `[kind:][namespace/]name`), pas de l'existence.
  Normalisé en `kind:namespace/name` (défauts `group`/`default`). Ref malformé ⇒ échec build.
- **Livrables plateforme** sous `platform/` : `discovery/app-config.discovery.yaml` (snippet provider)
  + `software-template/` (template scaffolder v1beta3 **discovery-first, sans catalog:register** +
  skeleton Spring Boot avec workflow CI qui régénère et commit le descripteur).

## Multi-module / monorepo (verrouillé)

- **3 goals** : `generate-catalog-info` (per-module, défaut, `process-classes`),
  `generate-catalog-info-aggregate` (`@Mojo(aggregator=true)`, 1 fichier racine),
  `check-catalog-names` (aggregator, validation CI sans écriture). `aggregator` étant une propriété de
  compilation, **goal dédié, pas de flag** (un mojo ne peut pas être per-module ET aggregator).
- **Logique partagée** dans le plugin : `CatalogGeneration.forModule(...)` (skip `pom`, scan, build) +
  `ModuleScanner`/`AnnotatedComponent`/`ModuleResult`/`GenConfig`. Les 3 mojos sont fins.
- **name** dérivé de l'**artifactId** (pas `spring.application.name`) → identité = module Maven.
- **source-location** = `url:<repo>/tree/<branch>/<relPath>/` (`GitMetadata.RepoInfo` expose
  baseUrl + workTreeRoot + branch lue dans `HEAD`). Overrides : `sourceLocationBranch`, `repoBaseUrl`.
- **Unicité** : `NameUniqueness` (core, pur) → fail-fast en agrégé/check (`failOnDuplicateName=true`).
- **inferDependencies** (agrégé, défaut false) : `DependencyInference` (core, pur) → `dependsOn:
  component:default/<nameB>` quand B est un module annoté ; jamais externe/transitif.
- **Aggregator timing** : les modules doivent être compilés AVANT (l'aggregator tourne à la position
  root) → invoquer après `mvn package` (documenté). Module non compilé → warn + skip.
- **Fixtures DoD** : `examples/monorepo-demo` (in-reactor : parent pom + orders-api + billing-api+springdoc
  + shared-model `type=library` + internal-util non annoté) ; `examples/monorepo-collision` (standalone,
  2× name="dup", hors reactor pour ne pas casser le build). Vérif via Bash (per-module/agrégé/collision/
  idempotence/dependsOn) — tout vert.

## Relations & entités connexes (verrouillé)

- **Règle source-de-vérité** : on **génère** ce que le code possède (`Component`, `API` springdoc,
  `Resource` dédiées), on **référence** le reste (`Group`/`System`/API consommées/Resources partagées).
  Validation **format uniquement** (`Names.qualifyRef(ref, defaultKind)`), jamais d'existence.
- **`consumesApis`** (champ `@BackstageComponent`) → `spec.consumesApis` (refs `api:default/…`),
  **aucune** entité API créée. **`providesApis`** explicite fusionné + dé-doublonné avec le nom springdoc
  (qui reste **bare** `<name>-api`). **`dependsOn`** déclaratif, validé.
- **`@BackstageResource` répétable** (opt-in strict) → entité `Resource` (modèle `ResourceEntity`/`ResourceSpec`,
  **pas de lifecycle** — non standard Backstage) + auto-`dependsOn: [resource:default/<name>]` sur le Component ;
  owner/system **hérités** du Component sauf override. Pas d'annotation → **aucune** Resource. **Aucune
  inférence** datasource/config. Lecture ClassGraph : conteneur `BackstageResources` (2+) OU `BackstageResource` (1).
- **Unicité par kind** : `AbstractAggregatorMojo.enforceUniqueness` clé = `kind/name` (Component `x` et
  Resource `x` ne collisionnent pas ; 2 Resources `x` oui).
- **Feign différé** : flag `inferConsumedApis` (défaut false) **réservé**, no-op + warn si activé.
- Démo : `examples/monorepo-demo/services/orders-api` (consumesApis payments + `@BackstageResource orders-db`).

## Workaround environnement (cette machine)

- **Ports 8080 et 9001 sont pris par Docker** (nginx d'un conteneur sur 8080 ; JMX 9001).
  → l'exemple tourne sur **`server.port: 18080`** et le profil `openapi-export` utilise
  `<jmxPort>19876</jmxPort>` + `apiDocsUrl=http://localhost:18080/v3/api-docs.yaml`.
- Démo synergie springdoc (boote l'app, exporte la spec live) :
  `mvn -Popenapi-export -pl examples/orders-service verify` → produit `examples/orders-service/openapi.yaml`.
- `springdoc-openapi-maven-plugin:1.4` ne connaît pas le paramètre `failOnError` (warning si présent).

# Micronaut 4 → 5 (and JDK 21 → 25)

What this upgrade actually involved, and the traps worth knowing before the next
one. Written after the fact from the work on `mn-5-upgrade`.

## Versions moved

| | Before | After |
|---|---|---|
| Micronaut | 4.10.11 | 5.0.2 |
| Micronaut Gradle plugin | 4.4.2 | 5.0.2 |
| Gradle | 8.14.3 | 9.3.0 |
| JDK | 17 (source/target), 21 runtime | 25 |
| Jackson | 2 (`com.fasterxml`) | 3 (`tools.jackson`) |
| Shadow plugin | `com.github.johnrengelman` 8.1.1 | `com.gradleup` 9.3.0 |

## The one lesson worth internalising

**Almost every serious defect in this upgrade was silent.** Nothing threw at
build time; a customisation hook or version pin simply stopped being honoured,
and behaviour changed underneath.

Five separate instances of the same shape:

| What broke | How it broke | How it surfaced |
|---|---|---|
| OpenSearch client | Factory wanted a Jackson 2 `ObjectMapper`; MN5 only has Jackson 3 | App would not boot at all with OpenSearch configured — **production backend** |
| Elasticsearch credentials | Our builder moved to HttpClient 5; micronaut-elasticsearch still injects the HttpClient **4** type, so ours stopped being consumed | 401 on every request to a secured ES |
| Extended client metrics filter | Gated on `@Requires(bean = ClientRequestMetricRegistryFilter.class)`; Micrometer 5.9 stripped that class's bean annotations. The class still exists, so the `@Requires` still compiled | Filter silently never registered |
| Hazelcast CP subsystem | `buildSrc` pinned 5.4.0 as a plain constraint; `micronaut-cache-bom` (via `micronaut-platform`) raised it to 5.6.0, where CP became Enterprise-only | Every federated lock threw "CP subsystem is a licensed feature" |
| Polaris config binding | Swapped to a raw Jackson `ObjectMapper`, which defaults to `FAIL_ON_UNKNOWN_PROPERTIES=true` | Any stored Host LMS config with an unmodelled key failed client instantiation |

**Practical rule:** after a framework major bump, for every place you customise
framework behaviour, prove the customisation is still *taking effect* — not just
still compiling. A log line in the constructor of a filter or factory is worth
far more than it looks; the metrics filter was diagnosed purely because its
"Extended metrics enabled" line was absent from a startup log.

**Corollary on version pins:** Gradle resolves version conflicts by taking the
**highest** version, and a platform BOM participates in that. Our Hazelcast pin of
5.4.0 worked under Micronaut 4 only by luck — the MN4 cache BOM managed 5.3.7, so
our higher pin won. MN5's cache BOM moved to 5.6.0, which then won for exactly the
same reason. A plain constraint can only ever raise a version; if a pin is
load-bearing it must be `strictly`, with a comment saying why, or the next
platform bump silently undoes it.

## Trap register

### Dependency and BOM

- **Micronaut's platform BOM manages more than you think.** It overrode both the
  Hazelcast pin and the OpenSearch client version. Check `dependencyInsight` for
  anything you rely on being a specific version.
- **Prefer overriding a module BOM to pinning a library.** For OpenSearch, moving
  `micronaut-opensearch-bom` to 2.1.0 was better than forcing `opensearch-java`
  3.9.0 ourselves — same versions, but a combination Micronaut has validated.
- Hazelcast **5.5.0+ makes the CP subsystem Enterprise-only**. 5.4.0 is the last
  Community release with working `getCPSubsystem().getLock(...)`.

### Jackson 2 → 3

- Micronaut 5 injects `tools.jackson.databind.ObjectMapper`. Anything asking for
  `com.fasterxml.jackson.databind.ObjectMapper` will fail to inject.
- Jackson 2 is still on the classpath (logback's JSON layout, opensearch-java's
  legacy mapper), so `com.fasterxml` imports still *compile*. That is what makes
  this dangerous.
- **Do not hand-roll `new ObjectMapper()`** to dodge the problem. It bypasses
  Micronaut's configuration and changes defaults — `FAIL_ON_UNKNOWN_PROPERTIES`
  bit us exactly this way.

### Micronaut Serde 3

- **Serde 3 silently binds `null` into `Object`-typed bean properties.**
  `PolarisConfig.baseUrl` is declared `Object`, so binding it with Micronaut's
  mapper yields null and the config appears absent. This is why Polaris config
  binding legitimately has to use Jackson — with leniency re-enabled.
- MN5 bean introspection is **builder-aware**. A hand-written builder method
  taking a non-introspectable type (e.g. `Consumer<...>`) makes `Map` → bean
  conversion fail. Lombok `@Builder` types appearing in bean method signatures
  are rejected outright; pass the built value instead.

### Gradle 9

- Task validation problems that were **warnings in Gradle 8 are errors in 9**.
  This is what breaks Micronaut's `NativeImageDockerfile`
  (`property 'nativeImageOptions.layers' is missing an input or output
  annotation`) in plugin 5.0.1 and 5.0.2. Workaround: package the `nativeCompile`
  output with plain Docker tasks — see `dockerBuildNativeBinary`.
- JDK 25 requires Gradle 9; you cannot downgrade Gradle to dodge the above.

### Scheduling

- `ScheduledMethodProcessor.scheduleTasks(StartupEvent)` is a **package-private
  `@EventListener`**. A subclass in another package produces a *positionless*
  "Method annotated as executable but is declared private" error that **aborts
  annotation processing before javac finishes type-checking**, masking every other
  compile error. Keep `AppTaskAwareScheduledMethodProcessor` in
  `io.micronaut.scheduling.processor`.
- Silencing that with `@ReflectiveAccess` trades it for a worse problem: the
  generated listener definition then originates from a jar, Gradle's incremental
  compiler cannot track it, and **every incremental build fails** with "Attempt to
  recreate a file for type ...". The fix is to override `scheduleTasks` and
  delegate to `super`, which is only legal from inside that package.

### Tests

- **MN5's first-request cold start is slower.** Tight timeouts start failing
  non-deterministically — different tests each run, all passing in isolation.
  Widen the budget rather than chasing individual tests.
- Watch for failures that *cascade*: one awaitility timeout left a workflow
  in-flight, which then broke the next test's `@BeforeEach` cleanup with FK
  violations. Fix the first failure, not the three that follow it.

### Native image

Three build-time-initialisation failures, each only visible once the previous was
fixed:

1. `org.slf4j.helpers.NOPLoggerFactory` in the image heap → initialise
   **`org.slf4j` as a package**. Listing classes individually is whack-a-mole.
2. `org.xml.sax.helpers.LocatorImpl.publicId` → `ch.qos.logback` is build-time
   initialised, so `logback.xml` is parsed during the build and retains SAX
   objects. Add `org.xml.sax`.
3. `com.fasterxml.jackson.databind.ObjectMapper._jsonFactory` → the logback JSON
   formatter holds a Jackson 2 mapper. Add `com.fasterxml.jackson`.

Also: GraalVM 25 could crash in `PartialEscapeBlockState` during pre-analysis;
`-H:-EscapeAnalysisBeforeAnalysis` disables just that pass while keeping
`Optimize=2`.

## What was deliberately not done

- **Micronaut Test Resources was kept.** Removing it (as the ISO18626 spike did)
  saves only ~13.5s one-time and loses the test-resources server's container reuse
  between consecutive local runs.
- **CP-subsystem locking was kept.** Pinning Hazelcast to 5.4.0 preserves
  behaviour; replacing `FencedLock` with `IMap` locking would change locking
  semantics from fenced/linearizable to best-effort, which does not belong in an
  upgrade.

## Known follow-ups

- Hazelcast is pinned to a 2024 release — check for CVEs fixed in 5.5/5.6, and
  confirm JDK 25 behaviour.
- `folio/Holding.location` is typed `String` but real FOLIO returns an object.
  Pre-existing, not MN5, but it fires an alarm on every occurrence.
- `GraphQLSecurityContextCustomizer` assumes Keycloak-shaped claims and has no
  test coverage; the spike branch has a generic-OIDC fix worth taking separately.
- `testcontainers-bom` (2.0.3) and `testcontainers-postgresql` (2.0.5) disagree.

## Verification baseline

- `./gradlew :dcb:test` — 692 tests, 0 failures, 2 skipped.
- `nativeCompile` succeeds and the binary starts (banner, context, logback output,
  scheduled-method registration) before stopping on an absent database.
- Verified against Elasticsearch 9.4.0 (secured) and OpenSearch 2.14.0 / 2.19.1.

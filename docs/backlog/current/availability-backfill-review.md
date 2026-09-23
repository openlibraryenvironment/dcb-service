# Availability Backfill Review

## Status

This is the controlling backlog for `fix/availability-backfill-progress` and its
merge request. Accumulate the remaining availability-check fixes on that branch
and record each decision here. Keep `AvailabilityCheckJob` disabled where its
load is unsafe until the applicable issues below are resolved or explicitly
accepted.

## Context

`AvailabilityCheckJob` pages through bibliographic records, fetches item data
from member systems, stores location counts in `bib_availability_count`, and
queues affected clusters for shared-index updates. Live availability lookups
also update these counts.

The design has useful foundations: database-side candidate selection, bounded
pages, grace periods, per-source grouping, a federated lock, and reindexing after
updates. Review found the following correctness, load, and test concerns.

## Review Items

- [x] **URGENT: Guarantee progress for results that produce no count rows.**
  Items with no location codes, empty publishers, and suppressed save failures
  can leave a bib immediately eligible for the next chunk. This can repeatedly
  select the same bib within one run, sustaining remote and database load without
  making progress. Record a durable outcome or stop the run.
  **Fixed:** empty publishers and responses containing only missing or blank
  location codes now persist a diagnostic count with a retry grace period.
  Persistence failures fail the update instead of advancing the chunk or queuing
  a live-path index update. Focused tests cover all three cases.

- [x] **Replace stale location rows.** A refresh only upserts locations present
  in the latest response. Decide how to remove or invalidate locations no longer
  returned, including empty results, so obsolete facets and counts disappear.
  **Fixed:** a complete, uncached response now reconciles one bib/source-system
  scope: current counts are upserted and absent rows are deleted. Errors and
  missing/blank location codes retain prior rows because they cannot prove a
  location has gone. PostgreSQL coverage verifies the scoped deletion.

- [x] **Enforce true instance-wide concurrency.** `instance-wide` is currently
  used as `concatMap` prefetch while clusters are processed through an unbounded
  `flatMap`. Define and test caps across clusters, source systems, remote calls,
  mapping work, and database writes.
  **Fixed:** each job chunk now has one nonblocking limiter. `instance-wide`
  limits active remote calls across all clusters; `per-source` is keyed by Host
  LMS across that chunk; `mapping-writes` (default 3) limits mapping, count
  upserts, and stale-row deletions. Tests cover global, per-source, mapping, and
  multi-cluster limits.

- [x] **Remove duplicate live-lookup side effects during backfill.** A scheduled
  fetch enters the live path, which writes counts and queues an index update;
  the job then writes and queues again. Separate fetching from persistence, or
  otherwise guarantee one count update and one index event per result.
  **Fixed:** scheduled backfill now uses a backfill-specific fetch that bypasses
  the timeout cache fallback and live count/index update. The job owns its one
  persistence and reindex pass.

- [x] **Avoid unrelated per-item work and ineffective cache warming.** The
  backfill used the live path, including location memoization per item and a
  1,000-entry, one-day in-memory cache. Decide which side effects the backfill
  actually needs and remove the rest from that path.
  **Fixed:** backfill already bypassed the live cache; it now also bypasses
  location recording. Live lookups continue to record locations and update the
  cache. Focused coverage proves a backfill fetch does neither.

- [x] **Correct legacy grace-period selection.** For rows whose
  `grace_period_end` is null, the query currently excludes old rows and selects
  recent rows. Confirm migration compatibility and correct the cutoff semantics.
  **Fixed:** legacy rows now suppress rechecking only while `last_updated` is
  newer than the cutoff, matching the pre-grace behaviour. Defaults are now 60
  days for mapped rows and 14 days for other rows, both operator-tunable as
  `dcb.jobs.availability.mapped-recheck-grace-period` and
  `dcb.jobs.availability.recheck-grace-period`. PostgreSQL coverage verifies
  the legacy selection boundary.

- [x] **Define completeness per bib.** One current count row currently suppresses
  rechecking the whole bib even when other rows are stale or inconsistent.
  Establish how completeness and refresh state are represented and queried.
  **Closed without action:** a complete scheduled response refreshes all location
  counts for its bib together. Mixed rows require an unusual partial or legacy
  state and do not justify additional completeness state or query complexity.

- [ ] **Shorten transaction scope.** Remote calls for an entire chunk currently
  occur within chunk-level transaction handling. Keep network waits outside
  database transactions and make count replacement atomic at the appropriate
  bib or cluster boundary.
  **Deferred:** chunk-level transactions were an explicit throughput decision.
  Validate their real effect on a representative test system before changing
  them: transaction age, connection-pool use, lock waits, job throughput, and
  slow-LMS behaviour.

- [x] **Investigate nonterminating test-suite cleanup and patron resolution.** A full test run
  on this branch blocked in
  `PatronRequestResolutionServiceTests.shouldExcludeItemWhichAlreadyHasAlreadyBeenRequested`,
  waiting in `Mono.block`. A subsequent full run blocked during shared fixture
  cleanup in `DataAccess.deleteAll`. Establish whether this is fixture leakage,
  a reactive database fault, or a test harness fault; ensure the suite completes
  before enabling backfill.
  **Root-cause lead:** the test R2DBC pool permits one connection, while fixture
  cleanup streams `queryAll` and starts deletes before the read connection is
  released. The delete may therefore wait indefinitely for the same connection.
  The one-connection limit was a 2023 workaround for excessive test connections.
  A five-connection experiment exhausted PostgreSQL because each Micronaut
  context owns both R2DBC and JDBC pools. The prior `maxIdle` setting was not an
  r2dbc-pool option, so idle R2DBC sessions were never evicted. Use two R2DBC
  connections and two JDBC connections; start R2DBC pools empty and evict idle
  sessions after two seconds. Fixture deletion must remain sequential. The
  30-second patron-resolution timeout remains as a diagnostic guard; it does
  not replace the resource fix. **Fixed:** `DataAccess` deletes sequentially, the test
  pool uses two R2DBC and two JDBC connections, and r2dbc-pool now receives its
  actual idle-eviction options. `R2dbcPoolLifecycleTests` verifies released
  connections are evicted. Two full `./gradlew --no-daemon test` runs passed
  (5m17s and 5m16s); PostgreSQL remained at roughly 13--17 sessions during the
  second run. Testcontainers logs a rootless-Podman cleanup permission error
  after Gradle reports success; it is an environment cleanup problem, not a
  blocked test or test failure.

- [x] **Fix resolution ordering when availability dates tie.** During validation
  with the test R2DBC pool maximum raised to five,
  `PatronRequestResolutionServiceTests.shouldKeepOrderOfAvailableItemsWhenAvailabilityDateIsTheSameDate`
  selected item `123456` where the asserted stable result is `651463`. The same
  failure reproduces in isolation. The underlying test also asserted order for
  concurrently populated `allItems`, which is not a supported ordering contract.
  **Fixed:** availability-date ties now select the lowest local item ID; tests
  check the chosen item deterministically and treat the independently gathered
  item list as unordered.

- [ ] **Investigate selected-bib election during concurrent ingest.** The
  cluster-record API fixture contains two editions of *Basic circuit theory*;
  one has substantially richer identifiers. The current improved clustering path
  can select the poorer edition, although `electSelectedBib` says it selects the
  highest metadata score. `IngestService.getBibRecordStream()` processes records
  with `flatMap`, so determine whether election races with metadata persistence
  or the repository ordering needs an explicit tie-breaker. The API test now
  validates metadata shared by both editions; it must not conceal a fix here.

- [ ] **Add focused verification.** Cover candidate selection, grace periods,
  replacement/removal, empty and malformed results, failure progress, live
  updates, index-event deduplication, and actual concurrency limits. Include a
  representative large-data/load test before re-enabling scheduled backfill.

- [x] **Replace the legacy R2DBC transaction implementation.** The application
  replaces Micronaut's transaction implementation with a 658-line local copy.
  It first appeared in Steve Osguthorpe's 2024 framework-upgrade commit
  `c449d151a`, whose history gives no reason for the replacement. A 2025 follow-up
  (`c682b0305`) converted a synchronous closed-connection commit failure into a
  reactive error but did not prevent the connection closure; its source carries a
  TODO to delegate or remove it. The implementation survived the Micronaut 5
  migration without a review. Default to Micronaut 5's maintained implementation;
  retain the old one only as the restart-required compatibility switch
  `dcb.r2dbc.legacy-transaction-operations.enabled=true`. Accumulating alarms use
  `REQUIRES_NEW`: a direct alarm subscription made inside a failed Reactor callback
  otherwise inherits that callback's propagated transaction and is rolled back.
  Focused tests prove the Sierra timeout alarm commits after its caller rolls back,
  required versus `REQUIRES_NEW` R2DBC semantics, and JDBC/R2DBC coexistence.
  **Fixed:** the full suite passed twice with Micronaut's default implementation
  and once with the compatibility switch. This is sufficient to say the local
  replacement is not needed for supported, exercised contracts; history cannot
  establish its original 2024 trigger, so retain the restart-only fallback until
  a separately approved removal.

- [x] **Isolate MockServer request history between fulfilment test methods.** CI
  pipeline 62 on `4c50b1478` failed only the exact MockServer verification in
  `PlaceRequestAtSupplyingAgencyTests`: the placed request and local barcode were
  correct, but MockServer retained 15 prior requests. The `PER_CLASS` test creates
  database fixtures in `@BeforeEach` but does not reset MockServer before adding
  each method's Sierra expectations. **Fixed:** reset MockServer before installing
  each method's credentials and expectations, retaining the exact HTTP assertion.
  The focused class and full suite passed locally; confirm the next CI pipeline.

For each item, record the decision and evidence before checking it. A checked
item may mean fixed, explicitly accepted, superseded, or closed without action;
state which outcome applies.

## Completion Criteria

- Every review item has a recorded decision and supporting evidence.
- Any retained behaviour has explicit operational limits and observability.
- Scheduled backfill is enabled only after correctness and load validation at a
  representative catalogue size.

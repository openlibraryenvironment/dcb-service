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

- [x] **Investigate the nonterminating patron-resolution test.** A full test run
  on this branch blocked in
  `PatronRequestResolutionServiceTests.shouldExcludeItemWhichAlreadyHasAlreadyBeenRequested`,
  waiting in `Mono.block` without a test timeout. Establish whether this is a
  nondeterministic production-path deadlock, fixture leakage, or test harness
  fault; fix it and ensure the full suite completes before enabling backfill.
  **Fixed:** the method and its peers now have a 30-second JUnit timeout. The
  reported method and full class pass in isolation; no production-path fault was
  reproduced. A recurrence now fails with a bounded test error rather than
  blocking the suite indefinitely.

- [ ] **Add focused verification.** Cover candidate selection, grace periods,
  replacement/removal, empty and malformed results, failure progress, live
  updates, index-event deduplication, and actual concurrency limits. Include a
  representative large-data/load test before re-enabling scheduled backfill.

For each item, record the decision and evidence before checking it. A checked
item may mean fixed, explicitly accepted, superseded, or closed without action;
state which outcome applies.

## Completion Criteria

- Every review item has a recorded decision and supporting evidence.
- Any retained behaviour has explicit operational limits and observability.
- Scheduled backfill is enabled only after correctness and load validation at a
  representative catalogue size.

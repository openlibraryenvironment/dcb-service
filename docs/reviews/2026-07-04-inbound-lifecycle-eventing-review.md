# Inbound Lifecycle Eventing Review

Date: 2026-07-04

## Scope

Review the proposed convergence of polling and reactive inbound lifecycle
evidence.

Covered documents:

- `ARCHITECTURE.md`
- `docs/backlog/current/inbound-lifecycle-convergence.md`
- `docs/backlog/current/ncip-v202-dual-declarative-agency-spike.md`
- `docs/non-imperative-support.md`

Implementation update: the first schema-free slice introduces a
`LifecycleEvidenceIngestor` for NCIP inbound evidence and additive lifecycle
metadata on polling audit rows. Polling still needs to be adapted into the same
projection boundary.

Placement update: the imperative supplying-agency strategy now reloads the
active supplier request after delegating to `SupplyingAgencyService`. This keeps
the lifecycle strategy wrapper from re-projecting stale pre-placement supplier
evidence over state already persisted by the existing imperative flow.

## Findings

1. Current NCIP inbound and polling paths still do not fully converge.

   Polling uses `StateChange` and `HostLmsReactions`. NCIP inbound uses
   `InboundLifecycleMessageHandler` as an adapter into
   `LifecycleEvidenceIngestor`. Polling audit rows now carry compatible
   lifecycle metadata, but polling still needs to be adapted into the same
   lifecycle evidence projection path.

2. Current NCIP confirmation broadly follows the right pattern.

   NCIP confirmation updates supplier evidence, then workflow decides DCB
   request state through `HandleSupplierRequestConfirmed`.

3. `ItemShipped` was a known gap and now has first-slice coverage.

   NCIP `ItemShipped` now maps to supplier item `TRANSIT` evidence before
   ingestion. Transit cascade behaviour still needs broader workflow-level
   coverage.

4. Retry semantics are unclear for event-driven flows.

   Polling can re-trigger workflow on later polls. Event-driven NCIP suppresses
   scheduled polling, so failed downstream cascades need explicit retry handling.

5. Admin transaction history is more comparable but not fully unified.

   Polling keeps existing state-change audit entries and now adds `source`,
   `role`, and `resource`. NCIP records inbound lifecycle projection audit
   entries with protocol/correlation details. Both are explainable, but not yet
   one shared audit vocabulary.

6. Workflow no longer imports NCIP for supplier confirmation.

   `HandleSupplierRequestConfirmed` now treats protocol-present supplier
   requests as declarative instead of importing the NCIP adapter. The existing
   protocol adapter architecture test now protects this boundary.

7. The placement strategy wrapper had hidden coupling to imperative state.

   The wrapper originally returned the pre-call supplier request from the
   workflow context after `SupplyingAgencyService` had already persisted newer
   supplier evidence. This could overwrite selected item data. The fix keeps the
   existing Host LMS implementation untouched and reloads the active supplier
   request at the lifecycle strategy boundary.

## Required Direction

Introduce a lifecycle evidence ingestion boundary:

```text
polling or inbound protocol
  -> canonical lifecycle evidence
  -> evidence projection and audit
  -> workflow progression
```

Protocol adapters must not directly decide `PatronRequest.status`.

## Guardrails

- No database schema changes without explicit approval.
- Existing Sierra, Polaris, FOLIO, Alma, and other host LMS contract tests must
  remain valid.
- Workflow and core model packages must not depend on NCIP.
- Existing imperative behaviour remains the default.
- Existing audit brief descriptions must remain stable unless reviewed with the
  admin applications.

## Required Tests

- Polling and NCIP evidence convergence tests.
- `ItemShipped -> TRANSIT -> HandleSupplierInTransit` cascade test.
- No direct protocol mutation of `PatronRequest.status`.
- Audit coherence tests.
- Retry/idempotency tests.
- Architecture dependency tests.

## Validation Notes

- Focused inbound, placement, workflow, polling-audit, and architecture tests
  pass.
- `PlaceRequestAtSupplyingAgencyTests` passes unchanged.
- Full suite passes:
  `GRADLE_USER_HOME="$PWD/.gradle-codex" timeout 30m ./gradlew test --no-daemon --no-build-cache --rerun-tasks`.

## Review Outcome

Proceed incrementally. The first slice is acceptable if focused tests pass and
no schema changes are present. Next work should converge polling projection and
decide NCIP acknowledgement/retry semantics before expanding inbound message
support.

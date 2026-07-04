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

Implementation update: the schema-free slices now introduce
`LifecycleEvidenceProjector` for projection/audit and
`LifecycleEvidenceIngestor` for reactive inbound evidence plus workflow
progression. `TrackingServiceV4` is opt-in and maps polling `StateChange`
records into the projector.

Placement update: the imperative supplying-agency strategy now reloads the
active supplier request after delegating to `SupplyingAgencyService`. This keeps
the lifecycle strategy wrapper from re-projecting stale pre-placement supplier
evidence over state already persisted by the existing imperative flow.

## Findings

1. NCIP inbound and V4 polling now share projection/audit.

   V3 remains default and still uses `HostLmsReactions`. V4 maps
   `StateChange` to lifecycle evidence and calls `LifecycleEvidenceProjector`.
   NCIP inbound calls `LifecycleEvidenceIngestor`, which wraps the same
   projector and then progresses workflow.

2. Current NCIP confirmation broadly follows the right pattern.

   NCIP confirmation updates supplier evidence, then workflow decides DCB
   request state through `HandleSupplierRequestConfirmed`.

3. `ItemShipped` was a known gap and now has first-slice coverage.

   NCIP `ItemShipped` now maps to supplier item `TRANSIT` evidence before
   ingestion. Workflow-level coverage now proves `HandleSupplierInTransit`
   updates borrower and pickup systems for pickup-anywhere transit.

4. Retry semantics are unclear for event-driven flows.

   Polling can re-trigger workflow on later polls. Event-driven NCIP suppresses
   scheduled polling, so failed downstream cascades need explicit retry handling.

5. Admin transaction history is protected for V4 polling projections.

   V4 supplier request, supplier item, borrower request, borrower virtual item,
   pickup request, and pickup item projections preserve the existing polling
   audit brief and legacy keys. NCIP records still use
   `Inbound lifecycle message projected.` with protocol/correlation details.

6. V4 is not yet default.

   It is selected by `dcb.tracking.service=v4`. Automatic polling is registered
   once by `TrackingScheduler`, which is annotated with `@AppTask` and delegates
   to the selected `TrackingService`. V3 and V4 remain unscheduled
   implementations.

7. Workflow no longer imports NCIP for supplier confirmation.

   `HandleSupplierRequestConfirmed` now treats protocol-present supplier
   requests as declarative instead of importing the NCIP adapter. The existing
   protocol adapter architecture test now protects this boundary.

8. The placement strategy wrapper had hidden coupling to imperative state.

   The wrapper originally returned the pre-call supplier request from the
   workflow context after `SupplyingAgencyService` had already persisted newer
   supplier evidence. This could overwrite selected item data. The fix keeps the
   existing Host LMS implementation untouched and reloads the active supplier
   request at the lifecycle strategy boundary.

## Required Direction

Use the lifecycle evidence boundary:

```text
polling or inbound protocol
  -> canonical lifecycle evidence
  -> evidence projection and audit
  -> workflow progression when caller is reactive
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
- V4 parity tests for supplier item, borrower request, borrower virtual item,
  pickup request, and pickup item before making V4 default.

## Validation Notes

- Focused inbound, V4 supplier-confirmation parity, mapping, and architecture
  tests pass.
- `PlaceRequestAtSupplyingAgencyTests` passes unchanged.
- Full suite passes:
  `GRADLE_USER_HOME="$PWD/.gradle-codex" timeout 30m ./gradlew test --no-daemon --no-build-cache --rerun-tasks`.

## Review Outcome

Proceed incrementally. V4 can remain opt-in while remaining parity tests, NCIP
acknowledgement semantics, and retry semantics are settled.

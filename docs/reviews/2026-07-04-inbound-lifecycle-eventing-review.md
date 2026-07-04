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

No eventing implementation has been changed in this review.

## Findings

1. Current NCIP inbound and polling paths converge too late.

   Polling uses `StateChange` and `HostLmsReactions`. NCIP inbound uses
   `InboundLifecycleMessageHandler` and projects evidence directly. Both then
   call workflow progression, but they do not share one lifecycle evidence
   boundary.

2. Current NCIP confirmation broadly follows the right pattern.

   NCIP confirmation updates supplier evidence, then workflow decides DCB
   request state through `HandleSupplierRequestConfirmed`.

3. `ItemShipped` is a known gap.

   Current NCIP `ItemShipped` maps to `SHIPPED`; transit workflow expects
   `TRANSIT`. This likely fails to trigger `HandleSupplierInTransit` and its
   borrower/pickup cascades.

4. Retry semantics are unclear for event-driven flows.

   Polling can re-trigger workflow on later polls. Event-driven NCIP suppresses
   scheduled polling, so failed downstream cascades need explicit retry handling.

5. Admin transaction history is not yet coherent across paths.

   Polling records state-change style audit entries. NCIP records inbound
   lifecycle projection audit entries. Both are explainable, but not unified.

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

## Required Tests

- Polling and NCIP evidence convergence tests.
- `ItemShipped -> TRANSIT -> HandleSupplierInTransit` cascade test.
- No direct protocol mutation of `PatronRequest.status`.
- Audit coherence tests.
- Retry/idempotency tests.
- Architecture dependency tests.

## Review Outcome

Proceed with design of the lifecycle evidence boundary before code changes.
Implementation should be incremental and parity-tested against existing polling
behaviour.


# Inbound Lifecycle Convergence

## Status

Current architecture backlog item.

## Phase 1 Guardrails

- Phase 1 is schema-free.
- No database schema changes are allowed without explicit approval.
- Durable lifecycle evidence inbox/replay storage is explicitly out of scope for
  Phase 1 and needs separate approval.
- Existing external-system contract tests must not be changed unless the
  external contract change is intentional and reviewed.
- Existing transaction-history wording and audit data keys must be preserved
  where possible. Protocol details may be added as extra audit data, but should
  not replace existing state-change audit shape.
- Success means one public ingestion boundary. It does not require deleting all
  old internal tracking/eventing types in the first pass.

## Problem

DCB now has two lifecycle input styles:

- imperative tracking: DCB polls host LMS state and detects changes
- declarative/event-driven tracking: peers send lifecycle evidence, currently
  through inbound NCIP messages

Placement already has a clear strategy pivot between imperative and declarative
adapters. Inbound lifecycle evidence needs the same clarity.

The central question is where imperative tracking output and declarative inbound
messages become one DCB flow.

## Current Shape

### Placement Pivot

Outbound placement converges through lifecycle strategy services:

- `SupplyingAgencyRequestStrategyService`
- `BorrowingAgencyRequestStrategyService`
- strategy resolvers select imperative or declarative implementations
- projectors write canonical placement results onto `SupplierRequest` or
  `PatronRequest`

This is the existing control point for choosing how DCB talks to a host.

### Inbound Paths

Imperative tracking path:

```text
TrackingServiceV3
  -> HostLmsClient polling
  -> StateChangeFactory
  -> HostLmsReactions
  -> PatronRequest/SupplierRequest evidence update
  -> PatronRequestWorkflowService.progressUsing(...)
```

Declarative NCIP path:

```text
NcipController
  -> NcipInboundXmlMapper
  -> NcipInboundMessageMapper
  -> InboundLifecycleMessageHandler
  -> LifecycleEvidenceIngestor
  -> PatronRequest/SupplierRequest evidence update
  -> PatronRequestWorkflowService.progressUsing(...)
```

The first implementation slice introduces `LifecycleEvidenceIngestor` for the
NCIP inbound path. Polling still needs to be adapted into that same boundary.
Polling audit rows now carry additive lifecycle metadata (`source`, `role`,
`resource`) while preserving the existing state-change audit message and keys.

Implementation lesson: the existing placement pivot is also a coupling point.
The imperative supplying-agency strategy delegates to `SupplyingAgencyService`,
which already persists supplier request evidence. The lifecycle wrapper must not
then re-save stale supplier state from the pre-call workflow context. Phase 1
fixes this by reloading the active supplier request at the strategy boundary
before projection.

## Critical Gaps

### Evidence Projection vs DCB Request State

Inbound notifications should update DCB's model of peer-side evidence. They
should not directly decide the DCB patron request lifecycle state.

The intended pattern is:

```text
peer evidence changes
  -> project evidence onto SupplierRequest/PatronRequest local fields
  -> run workflow
  -> Handle... transition decides DCB request state and side effects
```

Current NCIP supplier confirmation follows this pattern in broad shape:

```text
RequestItemResponse / ItemRequested
  -> SupplierRequest.localStatus = CONFIRMED
  -> PatronRequestWorkflowService.progressUsing(...)
  -> HandleSupplierRequestConfirmed
  -> PatronRequest.status = CONFIRMED
```

But the implementation has a separate NCIP projection path. It does not use the
same tracking event vocabulary as polling. The first implementation slice routes
NCIP through `LifecycleEvidenceIngestor`; polling convergence remains follow-up
work.

### Retry Semantics

Polling and event-driven input have different retry behaviour today.

Polling repeatedly calls `progressUsing(...)`. If workflow progression fails
after evidence has been projected, the evidence remains present and a later poll
can re-enter the applicable `Handle...` transition.

Event-driven NCIP suppresses scheduled polling. If an inbound message projects
evidence and the subsequent workflow cascade fails, there may be no natural retry
unless the peer resends the same message or DCB stores pending/unprocessed
lifecycle evidence.

The convergence design must define whether inbound evidence is:

- applied only after the workflow side effects succeed
- persisted as pending evidence and retried until consumed
- projected immediately, with a separate retry trigger for workflow progression

### NCIP ItemShipped Does Not Currently Drive Transit

Original gap: `ItemShipped` mapped to supplier role status `SHIPPED`.

The existing transit workflow expects supplier evidence to be `TRANSIT`:

```text
SupplierRequest.localItemStatus == TRANSIT
or
SupplierRequest.localStatus == TRANSIT
```

The first implementation slice maps `ItemShipped` to supplier item `TRANSIT`
evidence before ingestion. This is intended to trigger `HandleSupplierInTransit`,
which is the transition that cascades transit updates to borrower and pickup
systems, including pickup-anywhere transactions.

This is not a message-specific bug only. It shows the need for a canonical
inbound lifecycle evidence model that can say:

```text
NCIP ItemShipped means supplier item/request entered DCB transit evidence
```

without making workflow depend on NCIP vocabulary.

## Design Question

Should inbound declarative messages be normalized into existing tracking
`StateChange` records and pass through `HostLmsReactions`, or should both
imperative tracking and declarative inbound protocols feed a new protocol-neutral
lifecycle evidence ingestion service?

Do not assume `HostLmsReactions` is the right boundary. NCIP messages often look
like state-change notifications, but they may carry protocol semantics,
acknowledgement rules, correlation data, and role-specific evidence that do not
fit the current `StateChange` model.

The chosen boundary must also preserve a coherent transaction history for the
admin applications. A user reviewing a DCB request must be able to see why the
request is in its current state, regardless of whether the evidence came from
scheduled polling or an inbound protocol message.

## Current Transaction History

Current NCIP declarative requests are visible through normal
`PatronRequestAudit` rows, but not in the same shape as imperative tracking
events.

Placement is represented by workflow audit rows such as:

```text
Action attempted : PlacePatronRequestAtSupplyingAgencyStateTransition
Action completed : PlacePatronRequestAtSupplyingAgencyStateTransition
```

The placement projectors update request evidence fields such as protocol,
local request id, local request status, raw status, and selected item data.

Inbound NCIP evidence is represented by:

```text
Inbound lifecycle message projected.
```

with audit data including protocol, role, operation, host LMS code, host request
id, correlation id, status, raw status, item id/barcode, message timestamp, and
raw message reference. These fields are stored as admin-friendly strings where
they represent canonical lifecycle vocabulary.

Workflow then adds the normal action audit rows, for example:

```text
Action attempted : HandleSupplierRequestConfirmed
Action completed : HandleSupplierRequestConfirmed
```

or, for rejection/missing evidence:

```text
Action attempted : HandleSupplierRequestCancelled
Supplier Request Cancelled (...)
Action completed : HandleSupplierRequestCancelled
```

This is explainable, but it is not yet a single, shared audit vocabulary for
imperative polling and declarative inbound evidence.

Phase 1 should preserve the existing tracking audit shape where possible:

```text
to {toState} from {fromState} - {resourceType}({resourceId})
```

with existing keys such as:

- `patronRequestId`
- `resourceType`
- `resourceId`
- `fromState`
- `toState`
- `fromRawStatus`
- `toRawStatus`

NCIP/protocol details should be additive audit data, for example:

- `source`
- `protocol`
- `messageKind`
- `correlationId`
- `rawMessageReference`

They should not replace the existing state-change message vocabulary unless a
separate admin-facing change is reviewed.

First-slice implementation note: polling rows now add `source=POLLING`,
`role=...`, and `resource=...`. Existing polling brief descriptions and legacy
state-change keys remain unchanged.

## Candidate Outcomes

1. Keep current split, document it.

   Lowest change, but leaves two projection paths to maintain.

2. Route inbound NCIP through `StateChange` and `HostLmsReactions`.

   Reuses current tracking event projection, but may force NCIP evidence into a
   model designed for polling diffs.

3. Introduce a lifecycle evidence ingestion boundary.

   Both `TrackingServiceV3` and protocol adapters emit canonical lifecycle
   evidence. One projector updates `PatronRequest` or `SupplierRequest`, audits,
   and invokes workflow progression.

   This is the selected Phase 1 direction for NCIP inbound evidence. Polling
   adaptation remains follow-up work.

## Decision Log / ADR Notes

Record decisions here during implementation. Promote to a formal ADR if the
decision changes module boundaries, retry semantics, or external behaviour.

### Decision 1: Phase 1 Is Schema-Free

Status: accepted.

Phase 1 must not add tables, columns, indexes, migrations, or persisted domain
model changes. If durable evidence storage is needed, stop and seek explicit
approval with a schema proposal.

### Decision 2: Public Boundary Before Internal Cleanup

Status: accepted for Phase 1.

The first goal is a single public lifecycle evidence ingestion boundary for
polling and reactive inbound messages. Existing internal types such as
`StateChange` may remain as adapters or implementation details until parity is
proven.

### Decision 3: Audit Compatibility First

Status: accepted for Phase 1.

Do not rewrite existing polling audit messages in Phase 1. New lifecycle
metadata must be additive. Any change to admin-facing audit vocabulary needs
separate review with the admin applications.

## Known Follow-Up Work

- Adapt polling into `LifecycleEvidenceIngestor` or a clearly documented
  adapter so polling and NCIP share the same projection path.
- Keep the protocol-adapter architecture guard green as inbound protocols are
  added.
- Decide NCIP acknowledgement semantics: after parse, after projection, or after
  workflow progression.
- Define retry semantics for failed workflow progression after inbound evidence.
- Add broader transit cascade coverage for `ItemShipped -> TRANSIT ->
  HandleSupplierInTransit`.

### Decision 4: NCIP Acknowledgement Semantics

Status: unresolved.

Decide whether NCIP inbound requests are acknowledged:

- after parse/authentication only
- after evidence projection
- after workflow progression

This affects retry behaviour, peer expectations, and whether a workflow cascade
failure should produce an NCIP problem response.

## Preferred Direction

Introduce a small application-layer port for lifecycle evidence ingestion.

```java
interface LifecycleEvidenceIngestor {
  Mono<RequestWorkflowContext> ingest(LifecycleEvidence evidence);
}
```

The model should be protocol-neutral and source-neutral. It should describe what
changed, not how DCB learned about it:

- source: polling, inbound protocol, future webhook, future message bus
- lifecycle role: supplier, borrower, pickup
- resource: request, item, borrower virtual item, pickup request, pickup item
- operation or lifecycle area
- from state and to state where known
- normalized status and raw status
- host LMS code
- patron request id or correlation id
- host request id, item id, item barcode where known
- protocol and protocol/raw message reference

Target shape:

```text
TrackingServiceV3
  -> detects host state change
  -> LifecycleEvidence
  -> LifecycleEvidenceIngestor

NcipController
  -> validates and parses NCIP
  -> LifecycleEvidence
  -> LifecycleEvidenceIngestor

LifecycleEvidenceIngestor
  -> idempotency / replay policy
  -> evidence projection
  -> coherent audit
  -> PatronRequestWorkflowService.progressUsing(...)
```

The important boundary is:

```text
NCIP and polling differ only before LifecycleEvidenceIngestor.
After that, projection, audit, workflow, and retry rules are shared.
```

This should be introduced carefully. Do not churn working host LMS integrations.
A first implementation may adapt NCIP inbound first, then have
`HostLmsReactions` delegate to the new ingestor or adapt existing `StateChange`
records into `LifecycleEvidence`.

## Backout Plan

Keep the existing polling path intact until parity is proven.

One possible approach is to introduce a new tracking implementation, for example
`TrackingServiceV4`, selected by Micronaut injection/configuration rules. This
matches prior practice around `TrackingServiceV3` and allows the old tracking
implementation to remain available during migration.

Keep the backout simple:

- old tracking service remains selectable
- NCIP adapter changes are isolated behind the new lifecycle evidence boundary
- no schema migration means no database rollback
- external host LMS client contracts remain unchanged

## Required Analysis

- Define the canonical inbound evidence model.
- Decide whether `StateChange` is sufficient or should become an implementation
  detail of imperative tracking.
- Confirm whether the existing tracking event path is the convergence point, or
  whether a new lifecycle evidence ingestion boundary should replace it.
- Define the audit vocabulary for lifecycle evidence so admin apps can explain
  state changes consistently across imperative and declarative paths.
- Define retry semantics for event-driven evidence when downstream workflow
  side effects fail.
- Decide NCIP acknowledgement semantics: after parse, after projection, or after
  workflow progression.
- Decide where idempotency belongs.
- Decide where raw protocol references and replay/audit metadata belong.
- Decide whether protocol acknowledgement happens before or after projection and
  workflow progression.
- Confirm how role-specific evidence is represented:
  supplier request, supplier item, borrower request, borrower virtual item,
  pickup request, pickup item.
- Confirm how event-driven tracking suppresses scheduled polling without hiding
  manual diagnostics.
- Confirm package boundaries so workflow and fulfilment do not depend on NCIP.
- Decide how to migrate incrementally without breaking existing host LMS
  behaviour or external protocol contracts.

## Documentation Deliverables

Create or update developer documentation to show both control points:

1. Placement strategy control point:

   ```text
   workflow transition
     -> lifecycle placement strategy service
     -> imperative or declarative adapter
     -> placement result projection
   ```

2. Inbound lifecycle convergence point:

   ```text
   imperative polling OR protocol inbound message
     -> canonical lifecycle evidence
     -> evidence projector
     -> request evidence update
     -> workflow progression
   ```

The docs must make clear that NCIP is a protocol adapter, not a workflow
dependency.

Add explicit developer signposts:

- Create and maintain top-level `ARCHITECTURE.md` as the starting model for
  humans and agents.
- Update `AGENTS.md` so substantial work reads `ARCHITECTURE.md` and definition
  of done includes keeping it current.
- `docs/developerguide.md` section: `Lifecycle Evidence Ingestion`.
- package docs or `package-info.java` for the lifecycle evidence package.
- Javadoc on `LifecycleEvidenceIngestor`: all inbound lifecycle notifications
  enter here.
- NCIP package note: NCIP maps messages to lifecycle evidence only. It must not
  project request state directly or invoke workflow directly.
- Tracking package note: polling is one source of lifecycle evidence, not a
  separate reaction mechanism.
- Agent/developer guidance: new inbound protocols must map to lifecycle evidence
  and must not introduce another projection path.
- Add `MODULE.md` files for significant functional modules where useful, and
  link them from `ARCHITECTURE.md`. Initial likely candidates:
  `request.workflow`, `request.lifecycle`, `tracking`, `core.interaction`,
  `storage`, and `request.lifecycle.ncip`.

`ARCHITECTURE.md` should stay deliberately high level. It should answer:

- What are the main modules?
- What owns what?
- What must not depend on what?
- Where are the important extension points?
- Where is persistence owned?
- Where are external integrations isolated?
- What are the known architectural and boundary rules?

## Test Strategy

Protect current external-system interfaces. Do not rewrite or weaken tests that
represent existing Sierra, Polaris, Alma, FOLIO, or other host LMS contracts
unless the contract itself is intentionally changed and reviewed.

Add tests around the new boundary instead:

1. Convergence tests.

   - Polling-detected supplier request state change creates lifecycle evidence
     and reaches the ingestor.
   - NCIP `RequestItemResponse` creates equivalent lifecycle evidence and
     reaches the same ingestor.
   - Both paths produce the same projected supplier evidence and workflow
     progression.

2. Transit cascade tests.

   - NCIP `ItemShipped` maps to canonical transit evidence.
   - The evidence triggers `HandleSupplierInTransit`.
   - Pickup-anywhere borrower/pickup updates still occur.

3. No direct DCB state mutation tests.

   - Protocol handlers do not set `PatronRequest.status`.
   - Protocol handlers do not call workflow directly once the ingestor exists.
   - DCB request status changes occur through `Handle...` workflow transitions.

4. Audit coherence tests.

   - Polling and NCIP evidence create transaction history with the same
     explanatory concepts: source, role, resource, from state, to state, raw
     status, protocol, correlation id, and protocol reference.
   - Existing state-change audit message shape is preserved where possible:
     `to {toState} from {fromState} - {resourceType}({resourceId})`.

5. Retry and idempotency tests.

   - Duplicate inbound messages do not double-apply.
   - Workflow failure after inbound evidence has arrived has explicit tested
     behaviour: pending evidence retry, delayed projection, or scheduled
     progression retry.

## Architecture Tests

Add ArchUnit or equivalent checks:

- `..request.lifecycle.ncip..` may depend on lifecycle evidence APIs.
- `..tracking..` may depend on lifecycle evidence APIs.
- `..request.workflow..` must not depend on `..request.lifecycle.ncip..`.
- `..request.fulfilment..` must not depend on `..request.lifecycle.ncip..`.
- `..core.model..` must not depend on `..request.lifecycle.ncip..`.
- Protocol packages must not call request repositories directly for lifecycle
  evidence projection.
- Protocol packages must not call `PatronRequestWorkflowService` directly.
- Only lifecycle evidence ingestion/projection code updates request evidence
  fields from inbound messages.
- `PatronRequest.status` changes caused by lifecycle evidence must happen in
  workflow transitions, not protocol adapters.

## Required Reviews

This work needs two explicit reviews before production adoption:

1. Eventing-system review.

   Review the lifecycle evidence ingestion change itself: convergence point,
   retry/idempotency semantics, transaction history, pickup-anywhere cascades,
   and proof that existing imperative integrations still behave as before.

2. Documentation-approach review.

   Review `ARCHITECTURE.md`, `AGENTS.md`, and any `MODULE.md` files for whether
   they give humans and agents a clear starting model without becoming an
   implementation guide.

## Acceptance Criteria

- A written architecture decision identifies the inbound convergence boundary.
- Phase 1 contains no database schema changes.
- Developer docs show placement and inbound lifecycle control points side by
  side.
- Transaction history shows the same explanatory concepts for imperative
  polling and declarative inbound messages: source, role, resource, from state,
  to state, raw status, correlation, and protocol reference.
- Existing admin-facing state-change audit wording and data keys are preserved
  where possible. New protocol details are additive.
- Tests prove imperative tracking and NCIP inbound evidence both reach the same
  projector or explicitly documented equivalent path.
- Existing imperative host behaviour remains default and unchanged.
- Existing external-system contract tests remain valid unless an intentional
  contract change is reviewed.
- NCIP-specific classes remain below `org.olf.dcb.request.lifecycle.ncip`.
- `ARCHITECTURE.md` and any affected `MODULE.md` files are updated.
- The two required reviews are completed or explicitly deferred.

# NCIP v2.02 Dual Declarative Agency Spike

## Status

Current exploratory backlog item.

Branch: `spike/iso18626-declarative-dual-agency`

This item supersedes the ISO18626 protocol leaf from
`docs/backlog/done/iso18626-dual-declarative-agency-spike.md`.

Current spike state: Slice 12 done. Production follow-ons remain.

Blocking questions: none. Status mapping remains message-specific.

## Goal

Pivot the declarative agency spike from ISO18626 to NCIP v2.02 while preserving
the protocol-neutral lifecycle refactor.

Prove a DCB transaction can use declarative NCIP messages for both peer agency
roles:

```text
DCB request workflow
  -> supplier declarative placement through NCIP RequestItem
  -> inbound NCIP supplier evidence advances supplier state
  -> borrower declarative placement through NCIP AcceptItem
  -> inbound NCIP borrower evidence advances borrower state
  -> DCB workflow progresses without scheduled polling for event-driven roles
```

## Reason For Pivot

ISO18626 maps cleanly to DCB placing a request at a supplier.

It does not map cleanly to DCB telling a borrower-mode peer library:

> Your patron has requested this item; prepare local borrower-side state.

NCIP v2.02 has a better vocabulary for this reciprocal agency interaction. The
repository now contains the NCIP schema at `src/xsd/ncip_v2_02.xsd`, including
`RequestItem`, `RequestItemResponse`, `AcceptItem`, and `AcceptItemResponse`.

## Keep

- Declarative vs imperative lifecycle strategy selection.
- Host/role scoped lifecycle capabilities.
- Default imperative behaviour for existing systems.
- Event-driven tracking mode and scheduled-poll suppression.
- Canonical placement result projection.
- Canonical inbound lifecycle message handling.
- Idempotent inbound event handling.
- Architecture tests that keep protocol-specific code removable.

## Replace

- Remove or deactivate `org.olf.dcb.request.lifecycle.iso18626`.
- Add `org.olf.dcb.request.lifecycle.ncip`.
- Replace ISO18626 strategy tests with NCIP strategy tests.
- Replace ISO18626 endpoint-call assertions with NCIP endpoint-call assertions.
- Replace `protocol: iso18626` examples with `protocol: ncip-v202`.

## Non-Goals

- Do not change current imperative host behaviour.
- Do not expose NCIP DTOs/XML outside the NCIP adapter package.
- Do not generate broad NCIP bindings unless needed for this spike.
- Do not solve every NCIP message type.
- Do not add production-grade retry, persistence, or compensation unless needed
  to prove the flow.
- Do not merge to main until explicitly requested.

## Assumed Defaults

1. Activation remains explicit.

   ```yaml
   capabilities:
     supplying-agency-request:
       strategy: declarative
       protocol: ncip-v202
     borrowing-agency-request:
       strategy: declarative
       protocol: ncip-v202
     supplier-tracking:
       mode: event-driven
       protocol: ncip-v202
     borrower-tracking:
       mode: event-driven
       protocol: ncip-v202
   ```

2. Missing capability configuration means existing imperative behaviour.

3. Supplier placement uses NCIP `RequestItem`.

4. Borrower placement/preparation uses NCIP `AcceptItem`.

5. DCB keeps current workflow order:

   ```text
   supplier request -> supplier evidence -> borrower request -> borrower evidence
   ```

6. `nextScheduledPoll = null` blocks automatic polling for event-driven roles.

7. Manual tracking remains available for diagnostics and recovery.

8. Existing `PatronRequest` and `SupplierRequest` fields remain compatibility
   projections, not the canonical protocol model.

9. Inbound NCIP `RequestItemResponse` and `AcceptItemResponse` success messages
   are placement evidence. They map to DCB local status `CONFIRMED` so existing
   supplier-confirmation workflow guards can advance without core workflow
   changes.

10. Inbound response messages must include `ResponseHeader/FromAgencyId/AgencyId`.
    The value is treated as the peer Host LMS code.

11. For this spike, inbound response messages must echo the DCB role-specific
    request correlation id in `RequestId/RequestIdentifierValue`, for example
    `{patronRequestId}:SUPPLIER`. Separating remote request id from correlation
    id remains future work.

12. `ItemShipped` receives an NCIP `ItemShippedResponse`. Inbound
    `RequestItemResponse` and `AcceptItemResponse` receive HTTP 204 because NCIP
    does not define a response-to-a-response message.

13. Outbound NCIP calls use Host LMS scoped config key `ncip-endpoint-url`.
    This is deliberately separate from existing imperative `base-url` config.

14. The spike includes a partial `ORSApplianceHostLMS` adapter. It implements
    supplier `RequestItem` and borrower `AcceptItem` placement only. Wider
    `HostLmsClient` method coverage should grow by lifecycle slice.

15. `HostLmsClient` is the natural plugin point for black-box DCB request tests
    today. Its broad method surface is architectural debt, but not a reason to
    keep NCIP outside the Host LMS model.

16. Extract capability interfaces only when a real caller is moved to depend on
    that capability. The first likely capabilities are
    `CanPlaceSupplyingAgencyRequest` and `CanPlaceBorrowingAgencyRequest`.
    Do not pre-create speculative tracking, cancellation, patron, item, or bib
    capability interfaces.

17. Capability detection must be GraalVM-safe. Use direct Java interface checks
    (`instanceof CanPlaceSupplyingAgencyRequest`) and static references, not
    reflection, annotation introspection, or dynamic method lookup.

18. A base Host LMS adapter class that returns `Mono.empty()` for unsupported
    reactive methods is acceptable. This keeps partial adapters small and
    avoids accidental `null` returns.

19. Full discovery-facing workflow tests are deferred until the NCIP adapter
    implements the surrounding Host LMS methods that DCB calls before/after
    placement, for example patron, bib, item, preflight, and tracking methods.
    Current higher-level coverage proves HostLmsService wiring and outbound
    NCIP HTTP calls.

## Boundary Decisions To Confirm

These are production/profile decisions. They should be made explicit during the
spike, but they do not block starting development.

### 1. NCIP Message Semantics

Default: supplier role sends `RequestItem`; borrower role sends `AcceptItem`.

Need confirm exact NCIP profile meaning with partner expectations.

### 2. NCIP Type Values

Need choose values for:

- `RequestType`
- `RequestScopeType`
- `RequestedActionType`

Default: introduce constants in the NCIP adapter, not workflow code.

### 3. Correlation Identifier Contract

Default: send a role-specific DCB correlation id:

```text
{patronRequestId}:SUPPLIER
{patronRequestId}:BORROWER
```

Store remote `RequestId` from NCIP responses when present.

### 4. Borrower Timing

Default: send borrower `AcceptItem` only after supplier placement is accepted.

Reason: this matches current DCB ordering and avoids preparing borrower state
for a supplier request that may fail.

### 5. Item Identity

Default: `AcceptItem` carries the best available item evidence:

- supplier item id/barcode if known
- DCB request correlation if item not yet known

Do not fabricate virtual item identifiers for declarative flows.

### 6. Inbound Evidence

Default: NCIP responses and inbound NCIP messages map to
`InboundLifecycleMessage`.

The NCIP adapter owns message-specific parsing and status mapping.

### 7. Inbound Controller

Default: implement the inbound controller after outbound `RequestItem` and
`AcceptItem` are proven.

The controller must live inside the NCIP adapter boundary. It should receive
`NCIPMessage`, validate against the XSD, route to the NCIP mapper, call
`InboundLifecycleMessageHandler`, and return the appropriate NCIP response.

Likely early inbound messages include `ItemShipped`, `ItemRequested`,
`ItemReceived`, and response messages.

Accepted controller decisions:

- Route: `/ncip/v2_02`.
- Authentication: out of scope for this spike, but required before production
  use for inbound and outbound NCIP.
- Partner resolution: NCIP `AgencyId` values correlate with DCB Host LMS codes.
- Initial Host LMS use case: ORS appliance integration using NCIP and normal
  Host LMS scoped configuration. A partial placement-only
  `ORSApplianceHostLMS` exists; full lifecycle coverage is a production
  follow-on.
- Response shape: follow NCIP message-specific responses. Start with
  `ItemShippedResponse`.
- Invalid XML: return NCIP `Problem`.
- Valid NCIP that cannot map to DCB state: return NCIP `Problem`.
- Duplicate inbound messages: return success/idempotent OK.
- First inbound messages: `ItemShipped`, `AcceptItemResponse`,
  `RequestItemResponse`.
- Raw message retention: no dedicated raw XML store in this spike; keep
  reference/audit metadata only.
- Controller processing: synchronous. NCIP response messages are substantive,
  not receipt acknowledgements.
- Error visibility: concise external error, detailed internal audit/log.

### 8. Payload Handling

Default: hand-build minimal XML payloads for `RequestItem` and `AcceptItem`,
then validate them against `src/xsd/ncip_v2_02.xsd` in tests.

Do not add generated bindings unless hand-built payloads become brittle.

### 9. Status Mapping

Default: defer status mapping until each specific NCIP message is implemented.

For each inbound message, answer:

- Which lifecycle role is affected: supplier, borrower, or pickup?
- Which lifecycle operation does it provide evidence for?
- Which existing DCB status or supplier-request status should it project?
- Does it advance workflow immediately or only record evidence?
- Which NCIP fields provide host request id, item id, barcode, and correlation?
- Which NCIP `Problem` should be returned if required fields are missing?

## Architecture Rules

1. All NCIP XML, schema validation, generated types, fixtures, and protocol
   constants live under:

   ```text
   org.olf.dcb.request.lifecycle.ncip
   ```

2. Workflow packages must not import NCIP classes.

3. Non-NCIP Host LMS adapters must not import NCIP declarative lifecycle
   classes. An NCIP-backed Host LMS adapter may live inside the NCIP package.

4. Lifecycle abstractions must not depend on NCIP.

5. Removing the NCIP package and NCIP config should leave imperative behaviour
   intact.

6. Architecture tests must enforce the above.

## Dev Slices

### Slice 1: Document Pivot

Status: done.

- Add this backlog item.
- Add a supersession note to the ISO18626 spike.
- Mark NCIP v2.02 as the active protocol target in the non-imperative doc.

Acceptance:

- No code behaviour changes.
- The active spike points to NCIP v2.02.

### Slice 2: Protocol Boundary Tests

Status: done.

- Rename/rework ISO18626 architecture tests into protocol-boundary tests.
- Assert NCIP implementation code stays under the NCIP package.
- Assert workflow/lifecycle abstractions do not import NCIP.

Acceptance:

- Current imperative tests still pass.
- Architecture tests fail if NCIP leaks outside the adapter boundary.

### Slice 3: NCIP Payload Foundation

Status: done.

- Add NCIP package.
- Add minimal payload builder for `NCIPMessage/RequestItem`.
- Add minimal payload builder for `NCIPMessage/AcceptItem`.
- Add schema validation using `src/xsd/ncip_v2_02.xsd`.

Acceptance:

- RequestItem fixture validates against the XSD.
- AcceptItem fixture validates against the XSD.
- No workflow code imports NCIP.

### Slice 4: Supplier RequestItem Strategy

Status: done.

- Replace ISO18626 supplier strategy with NCIP supplier strategy.
- Send `RequestItem` through a transport interface.
- Project response into `SupplyingAgencyRequestResult`.

Acceptance:

- Supplier declarative placement records exactly one NCIP `RequestItem` call.
- Imperative supplier placement remains unchanged.

### Slice 5: Borrower AcceptItem Strategy

Status: done.

- Replace ISO18626 borrower strategy with NCIP borrower strategy.
- Send `AcceptItem` through a transport interface.
- Project response into `BorrowingAgencyRequestResult`.

Acceptance:

- Borrower declarative placement records exactly one NCIP `AcceptItem` call.
- Imperative borrower placement remains unchanged.

### Slice 6: Inbound NCIP Evidence

Status: done.

- Add NCIP inbound mapper.
- Map NCIP response/event evidence to `InboundLifecycleMessage`.
- Preserve idempotency guard behaviour.
- Do not add the HTTP/controller boundary yet.

Acceptance:

- Duplicate inbound NCIP evidence is ignored.
- Supplier and borrower evidence advance only the matching role.

### Slice 7: Inbound NCIP Controller

Status: done.

- Add NCIP inbound controller inside the NCIP adapter package.
- Receive `NCIPMessage`.
- Validate inbound XML against `src/xsd/ncip_v2_02.xsd`.
- Route to the NCIP inbound mapper and `InboundLifecycleMessageHandler`.
- Return message-specific NCIP response messages.
- Start with `ItemShippedResponse`.
- Leave authentication out of scope for the spike.

Acceptance:

- Controller accepts a valid inbound NCIP message.
- Controller returns NCIP `Problem` for invalid XML or unmappable messages.
- Controller treats duplicates as idempotent success.
- Controller returns message-specific NCIP response XML.
- Workflow code remains NCIP-free.

### Slice 8: Dual Declarative Happy Path

Status: done.

- Rewrite the dual declarative spike test around NCIP.
- Prove supplier `RequestItem` precedes borrower `AcceptItem`.
- Prove event-driven roles do not schedule polling.

Acceptance:

- Test demonstrates both agencies in declarative NCIP mode.
- Test proves NCIP endpoints were called directly.

### Slice 9: Remove ISO18626 Leaf

Status: done.

- Delete or disable ISO18626 adapter classes and tests.
- Remove ISO18626 activation examples from the active spike.
- Keep the completed ISO document as historical context only.

Acceptance:

- No production code depends on `iso18626`.
- NCIP is the only active declarative protocol leaf.

### Slice 10: Inbound NCIP Response Messages

Status: done.

- Map inbound `RequestItemResponse` to supplier placement evidence.
- Map inbound `AcceptItemResponse` to borrower placement evidence.
- Reject NCIP `Problem` response payloads instead of projecting success.
- Return HTTP 204 for accepted response messages.
- Keep response parsing and status mapping inside the NCIP adapter.

Acceptance:

- `RequestItemResponse` and `AcceptItemResponse` validate against the XSD.
- Both messages project protocol `ncip-v202` and status `CONFIRMED`.
- Workflow code remains NCIP-free.

### Slice 11: Outbound NCIP HTTP Transport

Status: done.

- Add concrete `DeclarativeRequestTransport` for protocol `ncip-v202`.
- Resolve the target Host LMS by `hostLmsCode`.
- Read peer endpoint from Host LMS config key `ncip-endpoint-url`.
- POST NCIP XML as `application/xml`.
- Accept NCIP XML responses.
- Map `RequestItemResponse` and `AcceptItemResponse` back to
  `DeclarativeTransportResponse`.
- Reject mismatched response message kind, lifecycle role, or correlation id.

Acceptance:

- Supplier `RequestItem` is posted to the configured NCIP endpoint.
- Borrower `AcceptItem` is posted to the configured NCIP endpoint.
- Missing endpoint config fails before any HTTP call.
- Tests prove the HTTP endpoint is called.
- Protocol-specific transport stays inside the NCIP adapter package.

### Slice 12: ORS Appliance NCIP Host LMS Placement Adapter

Status: done.

- Add placement capability interfaces:
  `CanPlaceSupplyingAgencyRequest` and `CanPlaceBorrowingAgencyRequest`.
- Make `HostLmsClient` extend those capabilities without changing existing
  clients.
- Add `AbstractHostLmsClient` with `Mono.empty()` defaults for unsupported
  methods.
- Add partial `ORSApplianceHostLMS` inside the NCIP package.
- Implement supplier `RequestItem` and borrower `AcceptItem` placement.
- Add Host LMS fixture support for `ncip-endpoint-url`.
- Add context-level tests proving `HostLmsService` creates the adapter and real
  Micronaut HTTP POSTs reach `/ncip/v2_02`.

Acceptance:

- Existing Host LMS implementations compile unchanged.
- NCIP adapter exposes only the placement methods needed by this slice.
- Tests prove endpoint calls can be observed through MockServer.
- Full workflow tests remain deferred until wider Host LMS lifecycle coverage
  exists.

## Remaining Production Follow-ons

- Add inbound and outbound authentication.
- Grow `ORSApplianceHostLMS` method coverage by lifecycle slice.
- Consider a longer-term split of `HostLmsClient` into narrower capability
  interfaces so NCIP and other adapters can implement only honest capabilities.
- When extracting capability interfaces, extract only the interface needed by
  the caller being changed. Start with placement capability interfaces; defer
  tracking/cancellation/patron/item/bib capabilities until those paths are in
  scope.
- Move real callers to narrower capability interfaces only when there is a
  concrete lifecycle change needing it.
- Add discovery/API-facing black-box tests once the adapter can cover the
  required Host LMS lifecycle calls.
- Separate remote NCIP request ids from DCB correlation ids if partner profiles
  do not echo DCB `RequestId` values.
- Add partner/profile-specific status mapping once real NCIP examples are
  available.
- Persist raw NCIP XML or external message ids if audit/replay requires it.

## Definition Of Done

- Dual declarative agency flow works with NCIP `RequestItem` and `AcceptItem`.
- Existing imperative-only systems are transparent to the change.
- NCIP-specific code is isolated and removable.
- XSD-backed payload validation exists for the main outbound messages.
- Architecture tests protect the boundary.
- Branch is pushed; main remains untouched.

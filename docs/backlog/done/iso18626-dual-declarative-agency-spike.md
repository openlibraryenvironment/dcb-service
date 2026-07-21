# ISO18626 Dual Declarative Agency Spike

## Status

Done as an architecture spike. The ISO18626 protocol leaf is superseded by the
NCIP v2.02 pivot tracked in
`docs/backlog/current/ncip-v202-dual-declarative-agency-spike.md`.

Branch: `spike/iso18626-declarative-dual-agency`

This spike explores making both the supplying-agency request and the
borrowing-agency request use a declarative ISO18626 path. The work must remain
on the spike branch until explicitly merged.

## Goal

Prove a DCB transaction can place and advance requests where both agencies are
configured for declarative ISO18626 interaction rather than the current
imperative host-LMS choreography.

The target shape is:

```text
DCB request workflow
  -> supplier declarative placement through ISO18626
  -> inbound ISO18626 supplier status message advances supplier evidence
  -> borrower declarative placement through ISO18626
  -> inbound ISO18626 borrower status message advances borrower evidence
  -> DCB workflow progresses without scheduled polling for event-driven roles
```

## Non-Goals

- Do not change behaviour for existing imperative hosts.
- Do not make ISO18626 concepts visible inside workflow transitions.
- Do not require current Sierra, Polaris, FOLIO, or Alma paths to adopt new
  configuration.
- Do not make NCIP part of this spike.
- Do not solve pickup-agency declarative handling unless it becomes necessary to
  demonstrate the two-agency ISO18626 flow.

## Assumed Defaults

These defaults are adopted unless a later design decision explicitly changes
them.

1. Activation is host-and-capability scoped.

   Missing configuration means existing behaviour:

   ```yaml
   capabilities:
     supplying-agency-request:
       strategy: imperative
     borrowing-agency-request:
       strategy: imperative
     supplier-tracking:
       mode: scheduled-poll
     borrower-tracking:
       mode: scheduled-poll
   ```

2. ISO18626 activation is explicit.

   ```yaml
   capabilities:
     supplying-agency-request:
       strategy: declarative
       protocol: iso18626
     borrowing-agency-request:
       strategy: declarative
       protocol: iso18626
     supplier-tracking:
       mode: event-driven
       protocol: iso18626
     borrower-tracking:
       mode: event-driven
       protocol: iso18626
   ```

3. Existing workflow order is preserved.

   The spike keeps the current DCB progression: resolve supplier, place supplier
   request, wait for supplier confirmation evidence, then place borrower request.
   It does not send supplier and borrower ISO18626 requests in parallel.

4. `nextScheduledPoll = null` blocks automatic polling for event-driven roles.

   The scheduled tracker only selects rows with a due `next_scheduled_poll`.
   Event-driven ISO18626 requests should therefore avoid scheduling automatic
   polls once the relevant request role is in event-driven mode.

5. Manual tracking remains available.

   Manual tracking is a diagnostic or recovery action. It is not the normal
   lifecycle driver for ISO18626 event-driven requests.

6. Inbound messages are idempotent.

   The same ISO18626 message may be received more than once. Handling the same
   correlation id, request id, status, and timestamp again must not duplicate
   workflow transitions or recreate artifacts.

7. Protocol-specific DTOs stay below the lifecycle layer.

   Workflow transitions consume canonical placement results and canonical
   lifecycle events. ISO18626 request/response/message objects belong in the
   adapter layer.

8. Existing `PatronRequest` and `SupplierRequest` fields remain compatibility
   projections.

   The spike may initially project ISO18626 evidence into existing fields where
   the meaning is true. It must not pretend virtual bibs, items, or holds exist
   when ISO18626 did not create them.

## Architectural Boundary Questions

These questions should be answered before production implementation. The spike
can proceed using the assumed defaults above where noted.

### 1. ISO18626 Transaction Shape

Question: Is the intended ISO18626 model one end-to-end DCB-brokered transaction
or two independent ISO18626 request conversations, one with the supplier and one
with the borrower?

Default for spike: model this as two role-specific ISO18626 conversations under
one DCB patron request. Each conversation has its own role, host, correlation
id, remote request id, status, and raw message references.

Why it matters: this decides whether lifecycle evidence is stored as one
transaction record or as per-role interaction evidence.

### 2. Correlation Identifier Contract

Question: Which identifier must partners echo in inbound ISO18626 messages?

Default for spike: DCB sends a role-specific correlation id derived from the DCB
patron request id and lifecycle role, for example:

```text
{patronRequestId}:SUPPLIER
{patronRequestId}:BORROWER
```

DCB also stores any remote ISO18626 request id returned by the partner.

Why it matters: inbound messages need deterministic request correlation without
falling back to fragile bibliographic, patron, or barcode matching.

### 3. Evidence Persistence Boundary

Question: Should ISO18626 placement and inbound message evidence be persisted in
a new role-specific interaction table, or projected only into existing
`PatronRequest` and `SupplierRequest` fields?

Default for spike: start with compatibility projections and a small in-code
canonical result model. If the spike needs durable idempotency or audit beyond
existing audit entries, introduce a dedicated evidence table before adding more
fields to `PatronRequest` or `SupplierRequest`.

Production recommendation: add a role-specific evidence table keyed by patron
request id, lifecycle role, host LMS code, protocol, and correlation id.

Why it matters: existing `local*` fields are named around host-local imperative
evidence and are not a good long-term canonical model for declarative messages.

### 4. Failure And Compensation Boundary

Question: If supplier ISO18626 placement succeeds but borrower ISO18626
placement fails, should DCB automatically send a supplier cancellation message?

Default for spike: do not add automatic compensation unless an explicit
ISO18626 cancellation adapter is included in the spike. Move the request to
`ERROR`, audit the supplier evidence, and keep the failure recoverable by manual
operator action.

Production recommendation: add role-specific cancellation capability before
activating declarative placement for live traffic.

Why it matters: automatic compensation changes external agency state and should
not be implicit.

### 5. Supplier Confirmation Semantics

Question: What inbound ISO18626 supplier evidence is sufficient to move DCB from
`REQUEST_PLACED_AT_SUPPLYING_AGENCY` to `CONFIRMED`?

Default for spike: any canonical supplier event that maps to "accepted /
confirmed / item selected" can project onto `SupplierRequest.localStatus =
CONFIRMED` and advance the DCB request to `CONFIRMED`.

The event should include selected item evidence when the partner supplies it,
but the spike should tolerate item evidence arriving later if ISO18626 partner
behaviour requires that.

Why it matters: existing code may fetch selected item id/barcode from the
supplier hold after confirmation.

### 6. Transit And Item Movement Boundary

Question: In a fully declarative supplier path, who declares that the supplier
item is in transit, and does DCB still need to update borrower or pickup items
to `TRANSIT`?

Default for spike: inbound ISO18626 supplier status drives canonical supplier
transit evidence. If no borrower-side virtual item exists, skip borrower item
status updates rather than fabricating item ids. For pickup-anywhere workflows,
leave pickup imperative unless explicitly activated.

Why it matters: `HandleSupplierInTransit` currently updates upstream local
items through imperative host calls.

### 7. Tracking Mode Granularity

Question: Is tracking mode selected per host, per role, or per patron request?

Default for spike: select tracking mode per host and lifecycle role. A request
can therefore be event-driven for supplier and borrower while retaining
scheduled polling for pickup if pickup remains imperative.

Why it matters: mixed workflows are likely during migration.

### 8. Re-Resolution Boundary

Question: How should declarative supplier requests behave when the current
supplier cannot supply and DCB re-resolves to another supplier?

Default for spike: keep the existing re-resolution state model but require a
role-specific cancellation/closure strategy before attempting live production
use. For the spike, audit unresolved supplier cancellation needs and stop short
of automatic cleanup if cancellation is not implemented.

Why it matters: existing re-resolution code cancels local borrowing and pickup
requests and creates a new supplier request.

### 9. Audit And Observability Boundary

Question: Should raw ISO18626 messages be stored in audit entries, a dedicated
message log, or both?

Default for spike: audit canonical summaries and store a raw message reference.
Do not store full raw protocol payloads in ordinary audit entries unless data
protection review allows it.

Why it matters: ISO18626 messages may contain patron or request data that should
not be copied widely.

### 10. Cutover Boundary

Question: Can a host be activated for ISO18626 while it already has in-flight
imperative requests?

Default for spike: activation applies only to newly placed requests. Existing
requests continue with the strategy/evidence shape they were created with.

Production recommendation: persist selected strategy and tracking mode per
role/request at placement time, rather than resolving only from current host
configuration.

Why it matters: host configuration can change while requests are in progress.

## Development Contract

The spike can proceed unsupervised under this contract:

1. Prefer additive changes.

   New strategy, policy, result, and adapter types should be added beside
   existing services. Existing imperative services should be wrapped, not
   rewritten, until tests prove equivalent behaviour.

2. Keep workflow transitions protocol-neutral.

   `PlacePatronRequestAtSupplyingAgencyStateTransition` and
   `PlacePatronRequestAtBorrowingAgencyStateTransition` may depend on lifecycle
   services or strategy services, but they must not import ISO18626 DTOs or
   branch on protocol names.

3. Default to current behaviour.

   If capability configuration is missing, invalid, or not yet implemented for a
   host, current imperative behaviour is the default only when the host did not
   explicitly request declarative behaviour.

4. Fail fast for explicit declarative misconfiguration.

   If a host explicitly requests ISO18626 declarative handling and no compatible
   strategy or adapter exists, fail as configuration error. Do not silently
   place the request imperatively.

5. Preserve existing public state names.

   The spike should not introduce new `PatronRequest.Status` values unless no
   existing status can represent the business milestone. ISO18626 state should
   be mapped to canonical evidence first, then projected into existing statuses.

6. Do not fabricate imperative artifacts.

   If ISO18626 placement does not create DCB-owned virtual bibs, items, or
   holds, leave those artifact fields unset or clearly mark them as protocol
   evidence. Do not put fake local item ids into `localItemId` just to satisfy
   existing tracking code.

7. Keep event-driven polling suppression scoped.

   Suppressing `nextScheduledPoll` must apply only to the request/role strategy
   selected for the current request. It must not globally disable tracking for
   imperative hosts.

8. Commit-worthy slices should be independently revertible.

   Each slice should keep tests passing for current imperative paths. ISO18626
   behaviour can be incomplete behind config as long as defaults remain safe.

9. Keep declarative and ISO18626 work removable.

   The declarative lifecycle layer and ISO18626 adapter must be isolated enough
   that they can be removed later without editing core workflow logic beyond
   deleting strategy wiring and restoring direct calls to the existing
   imperative services.

10. Core code may depend inward only on lifecycle abstractions.

   Workflow, fulfilment, tracking, and model packages may depend on
   `org.olf.dcb.request.lifecycle` abstractions. They must not depend on
   `org.olf.dcb.request.lifecycle.iso18626` implementation classes.

## ISO18626 Payload Source

The repository does not currently contain ISO18626 schemas or generated DTOs.
There is a local conceptual mapping in `ors-appliance-supplying-service-spec.md`,
but that document is not a schema and should not be treated as the payload
contract.

Use the public ISO18626 XML schema as the structural source for spike payloads.
At the time this backlog item was prepared, the reachable schema was:

```text
http://illtransactions.org/schemas/ISO-18626-2021-3.xsd
```

The schema exposes the root `ISO18626Message` element with these top-level
message choices:

- `request`
- `requestConfirmation`
- `supplyingAgencyMessage`
- `supplyingAgencyMessageConfirmation`
- `requestingAgencyMessage`
- `requestingAgencyMessageConfirmation`

For the spike:

1. Vendor the exact XSD version used by the spike into a test/resource location,
   for example `dcb/src/test/resources/iso18626/ISO-18626-2021-3.xsd`.
2. Record the source URL and retrieval date beside the vendored copy.
3. Build minimal XML fixtures from the schema for:
   - supplier `request`
   - borrower `request`
   - `requestConfirmation`
   - `supplyingAgencyMessage`
   - `requestingAgencyMessage`
4. Validate fixture XML against the vendored XSD in tests.
5. Map validated XML into canonical lifecycle messages before touching workflow
   state.

Do not hand-roll payload structure from memory. If JAXB or another XML binding
tool is introduced, keep generated code isolated under the ISO18626 adapter
package or generated-source output so it does not leak into workflow code.

Partner profile details remain separate from base schema validity. The XSD tells
us whether a message is structurally valid; the implementation still needs a
small profile mapping layer for local status codes, reason codes, required
identifiers, and any consortium-specific extensions.

## Suggested Package Shape

Use existing package style, but group new lifecycle capability code so it does
not spread protocol concerns across workflow classes.

```text
org.olf.dcb.request.lifecycle
  LifecycleRole
  LifecycleOperation
  StrategyType
  TrackingMode

org.olf.dcb.request.lifecycle.placement
  BorrowingAgencyRequestStrategy
  SupplyingAgencyRequestStrategy
  BorrowingAgencyRequestStrategyService
  SupplyingAgencyRequestStrategyService
  BorrowingAgencyRequestStrategyResolver
  SupplyingAgencyRequestStrategyResolver
  BorrowingAgencyRequestResult
  SupplyingAgencyRequestResult
  BorrowingAgencyRequestProjector
  SupplyingAgencyRequestProjector

org.olf.dcb.request.lifecycle.tracking
  RequestTrackingPolicy
  RequestTrackingPolicyResolver
  InboundLifecycleMessage
  InboundLifecycleMessageHandler

org.olf.dcb.request.lifecycle.iso18626
  Iso18626BorrowingRequestStrategy
  Iso18626SupplyingRequestStrategy
  Iso18626DeclarativeRequestTransport
  Iso18626InboundMessageMapper
```

If this package shape conflicts with established local conventions during
implementation, keep the same boundaries but adapt names/packages to fit the
codebase.

## Isolation Rules

The declarative work should be isolated by package boundary, dependency
direction, and bean activation.

1. ISO18626 package boundary.

   All ISO18626 DTOs, XML parsing, schema validation, generated bindings,
   transport clients, payload fixtures, profile mapping, and protocol status
   mapping must live under:

   ```text
   org.olf.dcb.request.lifecycle.iso18626
   ```

   No class outside this package should import an ISO18626 DTO, generated class,
   XML binding class, or transport implementation.

2. Declarative lifecycle boundary.

   Protocol-neutral declarative abstractions may live under:

   ```text
   org.olf.dcb.request.lifecycle
   org.olf.dcb.request.lifecycle.placement
   org.olf.dcb.request.lifecycle.tracking
   ```

   Existing workflow classes may depend on these protocol-neutral abstractions.
   They should not know whether the selected implementation is ISO18626.

3. Existing imperative services remain authoritative.

   `SupplyingAgencyService` and `BorrowingAgencyService` remain the current
   imperative implementation. The spike should wrap them from imperative
   strategy classes rather than moving their logic into the lifecycle package.

4. No model pollution for protocol types.

   `PatronRequest`, `SupplierRequest`, and other core model classes should not
   gain ISO18626-specific enum values, DTO fields, or imports. If persistent
   protocol evidence is needed, prefer a separate role-specific evidence model.

5. Bean activation is explicit.

   ISO18626 beans should either be selected only by resolver/config or guarded
   by explicit configuration. Merely placing ISO18626 classes on the classpath
   must not alter current imperative behaviour.

6. Revert path must stay small.

   A rollback should be able to remove:

   - `org.olf.dcb.request.lifecycle.iso18626`
   - ISO18626 fixtures and schemas
   - ISO18626 resolver branches/config
   - inbound ISO18626 handler/controller

   without deleting current imperative services or changing existing host LMS
   adapters.

## Architecture Tests

Add architecture tests as part of the spike. The project does not currently have
an ArchUnit dependency. Start with lightweight JUnit tests that inspect source
files and package names. If those become brittle or insufficient, add ArchUnit
as a deliberate test dependency after reviewing the ADR instructions for
dependency changes.

Suggested test package:

```text
dcb/src/test/java/org/olf/dcb/architecture
```

Minimum architecture tests:

1. Workflow packages do not import ISO18626 implementation classes.

   Fail if files under:

   ```text
   dcb/src/main/java/org/olf/dcb/request/workflow
   dcb/src/main/java/org/olf/dcb/request/fulfilment
   dcb/src/main/java/org/olf/dcb/tracking
   dcb/src/main/java/org/olf/dcb/core/model
   ```

   contain imports matching:

   ```text
   org.olf.dcb.request.lifecycle.iso18626
   ```

2. ISO18626 classes stay inside the ISO18626 package.

   Fail if production files outside:

   ```text
   dcb/src/main/java/org/olf/dcb/request/lifecycle/iso18626
   ```

   have class names, package names, or imports containing `Iso18626`,
   `ISO18626`, or schema-generated ISO18626 package names, except for resolver
   tests/configuration tests where explicitly allowed.

3. Lifecycle abstractions do not depend on ISO18626.

   Fail if files under:

   ```text
   dcb/src/main/java/org/olf/dcb/request/lifecycle
   ```

   outside the `iso18626` child package import the `iso18626` child package.

4. Existing host LMS adapters do not depend on declarative ISO18626.

   Fail if files under:

   ```text
   dcb/src/main/java/org/olf/dcb/core/interaction
   ```

   import `org.olf.dcb.request.lifecycle.iso18626`.

5. Declarative activation is explicit.

   Resolver tests must prove that no capability config selects imperative
   strategies and scheduled-poll tracking. Separate tests must prove explicit
   ISO18626 config is required before ISO18626 strategies are selected.

6. Removability smoke check.

   Add a source-level test that lists allowed references to `iso18626` outside
   the ISO18626 package. The allowed list should be short and intentional:

   - capability resolver branch
   - inbound ISO18626 controller or handler registration
   - test fixtures
   - documentation is ignored

   Any new reference outside that list should fail the test and force an
   explicit decision.

If ArchUnit is added later, replace the source-scanning checks with package
dependency rules. The intended rules are:

```text
..request.workflow.. must not depend on ..request.lifecycle.iso18626..
..request.fulfilment.. must not depend on ..request.lifecycle.iso18626..
..tracking.. must not depend on ..request.lifecycle.iso18626..
..core.model.. must not depend on ..request.lifecycle.iso18626..
..core.interaction.. must not depend on ..request.lifecycle.iso18626..
..request.lifecycle.. excluding ..request.lifecycle.iso18626..
  must not depend on ..request.lifecycle.iso18626..
```

## ISO18626 Endpoint Call Verification

Tests must prove ISO18626 endpoints were called directly. Do not infer endpoint
use only from DCB state changes.

Use a test transport abstraction for unit and workflow tests, and an HTTP mock
server for adapter-level tests.

### Unit And Workflow Tests

The ISO18626 strategies should depend on a transport interface, for example:

```java
interface Iso18626Transport {
  Mono<Iso18626TransportResponse> send(
    Iso18626TransportRequest request);
}
```

For strategy and workflow tests, use a recording fake transport:

```java
class RecordingIso18626Transport implements Iso18626Transport {
  private final List<Iso18626TransportRequest> requests = new ArrayList<>();

  public Mono<Iso18626TransportResponse> send(
    Iso18626TransportRequest request) {
    requests.add(request);
    return Mono.just(successResponseFor(request));
  }

  List<Iso18626TransportRequest> sentRequests() {
    return requests;
  }
}
```

Assertions should verify:

- exactly one supplier ISO18626 request was sent during supplier placement
- exactly one borrower ISO18626 request was sent during borrower placement
- each request has the expected lifecycle role
- each request has the expected host LMS code / agency code
- each request carries the expected DCB correlation id
- each request uses the expected ISO18626 message kind, initially `request`
- no imperative `HostLmsClient.placeHoldRequestAtSupplyingAgency(...)` or
  borrower virtual-record placement method was called when declarative mode is
  active

The dual-declarative happy-path test should assert the call sequence:

```text
1. supplier ISO18626 request sent
2. inbound supplier confirmation handled
3. borrower ISO18626 request sent
4. inbound borrower evidence handled
```

It should also assert that the recording transport has no unexpected pickup or
cleanup calls unless those behaviours are explicitly activated in the test.

### Adapter / HTTP-Level Tests

For tests that exercise the real ISO18626 HTTP transport, use MockServer or the
existing test HTTP infrastructure to verify outbound requests.

Assertions should verify:

- request URL/path matches the configured supplier or borrower endpoint
- HTTP method is the expected method for the chosen binding
- request body is XML with root `ISO18626Message`
- payload validates against the vendored ISO18626 XSD
- payload contains the expected top-level message element, for example
  `request`
- payload contains the expected DCB correlation id
- supplier and borrower endpoints each receive exactly the expected number of
  calls

The tests should fail if:

- no ISO18626 endpoint is called
- the endpoint is called more times than expected
- the imperative host LMS placement method is called in the same declarative
  placement scenario
- the XML cannot be validated against the vendored XSD

### Runtime Observability

Add structured audit or log data for each ISO18626 outbound call:

- patron request id
- lifecycle role
- operation
- host LMS code
- agency code, if available
- protocol: `iso18626`
- correlation id
- remote endpoint identifier or URL with secrets removed
- message kind
- response status
- remote request id, if returned

Do not log full XML payloads by default because they may contain patron data.
For debugging, prefer a payload hash plus a stored raw-message reference when
data protection rules allow raw payload retention.

## Implementation Slices

### Slice 1: Imperative Wrappers With No Behaviour Change

Goal: introduce supplier and borrower strategy interfaces while every request
still uses existing imperative behaviour.

Touchpoints:

- `PlacePatronRequestAtSupplyingAgencyStateTransition`
- `PlacePatronRequestAtBorrowingAgencyStateTransition`
- `SupplyingAgencyService`
- `BorrowingAgencyService`

Work:

1. Add `StrategyType` with `IMPERATIVE` and `DECLARATIVE`.
2. Add `SupplyingAgencyRequestStrategy` and
   `BorrowingAgencyRequestStrategy`.
3. Add imperative implementations that delegate to current services.
4. Add strategy services that choose the imperative strategy unconditionally.
5. Route the two placement transitions through the strategy services.
6. Add tests proving the current services are still called in default mode.
7. Add initial architecture tests proving workflow and fulfilment packages do
   not import ISO18626 implementation packages.

Expected result: no ISO18626 behaviour exists yet, but the workflow no longer
depends directly on the concrete imperative placement services.

### Slice 2: Canonical Placement Results And Projectors

Goal: separate remote placement evidence from mutation of `PatronRequest` and
`SupplierRequest`.

Work:

1. Add `SupplyingAgencyRequestResult`.
2. Add `BorrowingAgencyRequestResult`.
3. Move current field mapping into projectors.
4. Keep compatibility projection onto existing fields:
   `SupplierRequest.localId`, `SupplierRequest.localStatus`,
   `PatronRequest.localRequestId`, `PatronRequest.localRequestStatus`, and only
   genuine artifact fields.
5. Add tests for imperative projection.

Expected result: ISO18626 strategies can later return evidence without needing
to mimic virtual artifacts.

### Slice 3: Capability Configuration And Resolver Defaults

Goal: make activation explicit while keeping missing config imperative.

Work:

1. Add a small capability configuration reader or resolver abstraction.
2. Support at least these keys:

   ```yaml
   capabilities:
     supplying-agency-request:
       strategy: imperative|declarative
       protocol: iso18626
     borrowing-agency-request:
       strategy: imperative|declarative
       protocol: iso18626
     supplier-tracking:
       mode: scheduled-poll|event-driven
       protocol: iso18626
     borrower-tracking:
       mode: scheduled-poll|event-driven
       protocol: iso18626
   ```

3. If no capability is configured, select imperative placement and
   scheduled-poll tracking.
4. If declarative ISO18626 is configured and no strategy exists, throw a clear
   configuration exception.
5. Add tests for missing config, imperative config, declarative config, and bad
   declarative config.

Expected result: new behaviour is impossible to activate accidentally.

### Slice 4: Event-Driven Tracking Policy

Goal: prevent automatic scheduled polling for event-driven ISO18626 roles.

Touchpoints:

- `PatronRequestWorkflowService.scheduleNextCheck(...)`
- `TrackingServiceV3`
- `PatronRequestRepository.findScheduledChecks()`

Work:

1. Add `TrackingMode` with `SCHEDULED_POLL`, `EVENT_DRIVEN`, and `HYBRID`.
2. Add `RequestTrackingPolicy`.
3. Default all requests to `SCHEDULED_POLL`.
4. When selected policy is `EVENT_DRIVEN`, set
   `PatronRequest.nextScheduledPoll` to `null`.
5. Keep manual `forceUpdate(...)` behaviour available.
6. Add tests that event-driven requests are not returned by scheduled tracking
   selection and imperative requests still are.

Expected result: ISO18626 requests can wait for inbound messages without the
polling worker repeatedly asking imperative host questions.

### Slice 5: ISO18626 Strategy Skeletons

Goal: add declarative strategies behind config without requiring a complete
protocol implementation.

Work:

1. Add `Iso18626SupplyingRequestStrategy`.
2. Add `Iso18626BorrowingRequestStrategy`.
3. Add a protocol-neutral transport interface used by those strategies.
4. Add a stub ISO18626 transport for spike tests.
5. Return canonical placement results with role, host, protocol,
   correlation id, remote request id if known, status, and raw status.
6. Do not create virtual bib/item/hold evidence.
7. Extend architecture tests so ISO18626 classes remain under the ISO18626
   package and lifecycle abstractions do not depend back on ISO18626.

Expected result: with config enabled, workflow calls declarative strategies and
receives canonical placement evidence.

### Slice 6: Inbound ISO18626 Lifecycle Message Handling

Goal: allow inbound ISO18626 status evidence to advance DCB workflow.

Work:

1. Add `InboundLifecycleMessage`.
2. Add `InboundLifecycleMessageHandler`.
3. Add mapper from ISO18626 adapter output to canonical lifecycle message.
4. Resolve DCB request by role-specific correlation id first.
5. Project supplier messages onto `SupplierRequest` evidence.
6. Project borrower messages onto `PatronRequest` evidence.
7. Call `PatronRequestWorkflowService.progressUsing(...)` after projection.
8. Add idempotency guard based on correlation id, role, status, and message
   timestamp/reference.
9. Extend the removability architecture test with any intentional inbound
   handler/controller references.

Expected result: supplier confirmation and borrower status updates can be
driven by inbound canonical ISO18626 events.

### Slice 7: Supplier Confirmation And Borrower Placement Happy Path

Goal: prove both agencies can use declarative placement in one DCB transaction.

Work:

1. Configure supplier placement as `declarative/iso18626`.
2. Configure borrower placement as `declarative/iso18626`.
3. Configure supplier and borrower tracking as `event-driven/iso18626`.
4. Place request at supplier using ISO18626 strategy.
5. Simulate inbound supplier confirmation.
6. Place request at borrower using ISO18626 strategy.
7. Simulate inbound borrower evidence.
8. Assert no automatic scheduled poll is queued for the event-driven flow.

Expected result: one spike test demonstrates the two-agency declarative path
without touching imperative hosts.

### Slice 8: Failure, Re-Resolution, And Cleanup Notes

Goal: document and minimally guard unsupported production behaviours.

Work:

1. If borrower declarative placement fails after supplier placement, move to
   `ERROR` and audit supplier evidence.
2. If supplier sends cannot-supply evidence, project that to the existing
   re-resolution model where possible.
3. If cancellation/cleanup is not implemented for ISO18626, audit that fact and
   do not silently run imperative hold cleanup.
4. Add tests for explicit unsupported cleanup/cancellation behaviour if code is
   reached.

Expected result: unsupported production behaviours fail visibly rather than
performing incorrect imperative cleanup.

Spike guard adopted: when an in-flight supplier request carries declarative
protocol evidence, cancellation cleanup audits that declarative supplier
cancellation and verification are not implemented and skips the imperative
supplier hold cancellation/verification calls. Production cancellation or
compensation for ISO18626 remains a follow-up capability, not an implicit
fallback to host-LMS imperative cleanup.

## Minimal Code Touch List

Expected primary production files:

- `dcb/src/main/java/org/olf/dcb/request/workflow/PlacePatronRequestAtSupplyingAgencyStateTransition.java`
- `dcb/src/main/java/org/olf/dcb/request/workflow/PlacePatronRequestAtBorrowingAgencyStateTransition.java`
- `dcb/src/main/java/org/olf/dcb/request/workflow/PatronRequestWorkflowService.java`
- `dcb/src/main/java/org/olf/dcb/request/fulfilment/SupplyingAgencyService.java`
- `dcb/src/main/java/org/olf/dcb/request/fulfilment/BorrowingAgencyService.java`
- `dcb/src/main/java/org/olf/dcb/tracking/TrackingServiceV3.java`
- `dcb/src/main/java/org/olf/dcb/storage/PatronRequestRepository.java`

Expected new tests or extended tests:

- `dcb/src/test/java/org/olf/dcb/request/fulfilment/PlaceRequestAtSupplyingAgencyTests.java`
- `dcb/src/test/java/org/olf/dcb/request/fulfilment/PlaceRequestAtBorrowingAgencyTests.java`
- `dcb/src/test/java/org/olf/dcb/request/workflow/PatronRequestWorkflowServiceTests.java`
- `dcb/src/test/java/org/olf/dcb/PatronRequestTrackingTests.java`
- `dcb/src/test/java/org/olf/dcb/architecture/*`
- New lifecycle strategy resolver tests.
- New inbound ISO18626 message handler tests.
- New dual-declarative happy-path spike test.

## Test Strategy

Run focused tests after each slice. Do not defer all validation until the end.

Suggested focused commands:

```bash
GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew test --tests '*PlaceRequestAtSupplyingAgencyTests' --no-daemon
GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew test --tests '*PlaceRequestAtBorrowingAgencyTests' --no-daemon
GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew test --tests '*PatronRequestWorkflowServiceTests' --no-daemon
GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew test --tests '*PatronRequestTrackingTests' --no-daemon
GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew test --tests '*architecture*' --no-daemon
```

Before considering the spike branch ready for review, run the full suite using
the ADR-defined command:

```bash
GRADLE_USER_HOME="$PWD/.gradle-codex" timeout 30m ./gradlew test --no-daemon --no-build-cache --rerun-tasks
```

## Spike Exit Criteria

The spike is ready to turn into implementation tickets when:

- Strategy interfaces exist for supplier and borrower placement.
- Existing imperative behaviour is still the default.
- ISO18626 placement strategies can be selected only by explicit config.
- Event-driven tracking policy can suppress scheduled polling.
- Inbound canonical ISO18626 supplier evidence can advance to `CONFIRMED`.
- Inbound canonical ISO18626 borrower evidence can be projected without virtual
  artifact assumptions.
- Unsupported cancellation/cleanup behaviours are explicit and audited.
- Architecture tests prove ISO18626 implementation code is isolated and core
  workflow/fulfilment/tracking/model packages do not import it.
- Remaining production questions are documented as follow-up tickets rather than
  hidden in code comments.

## Ready For Development When

The spike is ready to start without further architectural interruption under the
following defaults:

- two role-specific ISO18626 conversations under one DCB patron request
- role-specific correlation ids derived from the patron request id
- current workflow order retained
- event-driven tracking blocks scheduled polling by leaving
  `nextScheduledPoll` null
- missing config means fully imperative behaviour
- activation applies to new requests only
- cancellation/compensation is audited but not automatic unless explicitly
  implemented

There are no blocking architectural questions for the spike. The boundary
questions above remain production-readiness questions and should be converted
into implementation tickets or ADRs if the spike validates the approach.

## Acceptance Criteria For The Spike

- Existing imperative tests continue to pass or failures are understood and
  documented.
- With no ISO18626 capability config, request placement and tracking code paths
  remain behaviourally equivalent to current `main`.
- With supplier and borrower configured for ISO18626, workflow transitions call
  declarative strategy stubs instead of `SupplyingAgencyService` and
  `BorrowingAgencyService` placement methods.
- Tests explicitly verify outbound supplier and borrower ISO18626 transport
  calls, including role, target host/agency, message kind, and DCB correlation
  id.
- Adapter-level tests verify HTTP requests reach configured ISO18626 endpoints
  with XSD-valid `ISO18626Message` payloads.
- Event-driven ISO18626 requests are not selected by scheduled tracking.
- Inbound canonical ISO18626 supplier evidence can advance a request to
  `CONFIRMED`.
- Inbound canonical ISO18626 borrower evidence can populate borrower request
  evidence without requiring virtual bib/item artifacts.
- Repeated inbound messages are idempotent.
- Architecture tests fail if ISO18626 implementation references leak into
  workflow, fulfilment, tracking, core model, or existing host LMS adapter
  packages.

# Non-Imperative Host Interaction Support

## Context

The current DCB request workflow reaches its central borrowing-side milestone in
`PlacePatronRequestAtBorrowingAgencyStateTransition`. Conceptually, that
transition is already operating at the right level: it means DCB has confirmed
that the patron request has been placed at the borrowing agency.

The implementation underneath that transition is more specific. It delegates to
`BorrowingAgencyService`, which currently embodies the fine-grained host LMS
recipe:

- fetch the borrowing patron identity
- fetch the borrowing host LMS client
- fetch the active supplier request
- create a virtual bib
- create a virtual item
- place a hold against those virtual records
- copy the returned local request evidence back onto `PatronRequest`

That is a valid strategy for the currently live integrations, but it is not the
only plausible way to place a borrowing-side request.

Non-imperative host interactions may expose a coarser operation, such as an NCIP
v2.02 request message. In that model, DCB does not need to coordinate a series
of lower-level host operations. Instead, DCB declares the desired request to the
host system and the host performs the internal choreography.

This note was previously pivoted from NCIP to ISO18626. The current direction is
to pivot back to NCIP v2.02 for the concrete protocol leaf, while retaining the
protocol-neutral declarative lifecycle architecture.

The active NCIP v2.02 spike is tracked in
`docs/backlog/current/ncip-v202-dual-declarative-agency-spike.md`.

## Terminology And Scope

`Imperative` means DCB controls the host interaction step-by-step. The current
virtual-record borrowing flow is imperative because DCB creates host artifacts
and then places a hold against those artifacts.

`Non-imperative` means DCB describes the desired lifecycle outcome and lets the
host system choose how to fulfil it. The host may still be procedural internally,
but DCB is not orchestrating each lower-level step.

`Declarative` is used below as the strategy name for this non-imperative
request-placement style. That name is useful at the DCB strategy layer, while
the ISO18626 protocol should remain below it as a transport or adapter choice.

The scope is capability-specific. A host may support non-imperative borrowing
placement while still requiring imperative cleanup, special tracking, or normal
circulation commands for checkout and return.

## Current Modelling Issue

The current model has collapsed three separate concerns:

1. The DCB workflow milestone: "the borrowing-side request has been placed".
2. The placement strategy: "how this kind of LMS achieves that milestone".
3. The host LMS primitives: "which concrete LMS calls are available".

`HostLmsClient` is currently a broad port that includes both primitive host
operations, such as creating bibs and items, and role-oriented operations, such
as placing holds at the borrowing, pickup, or supplying agency.

`BorrowingAgencyService` then assumes the imperative virtual-record strategy and
orchestrates the host calls itself. That makes ISO18626 awkward: it is not just
another primitive call, it is a different strategy for achieving the same DCB
milestone.

The borrowing placement step is only the first visible instance of this issue.
The same pattern appears throughout the request lifecycle. Workflow transitions,
fulfilment services, tracking, cancellation, cleanup, checkout, renewal, and
item-state handling all reach down toward `HostLmsClient` and compose host
operations directly.

That means the system is not only assuming a fine-grained host API when placing
a request. It is also assuming fine-grained host behaviour when tracking a
request, cleaning up records, cancelling holds, receiving items, checking items
out, preventing renewal, and moving items between lifecycle states.

## Recommended Direction

Avoid modelling this as two subclasses of host LMS, such as "fine-grained" and
"coarse-grained". That distinction is too broad. A host may be declarative for
borrowing placement, imperative for cleanup, special for cancellation, and still
need lower-level calls for tracking or diagnostics.

Instead, model this around role-specific lifecycle capabilities or strategies.

The stable model should remain the DCB request workflow. States such as
`REQUEST_PLACED_AT_SUPPLYING_AGENCY`, `CONFIRMED`,
`REQUEST_PLACED_AT_BORROWING_AGENCY`, `REQUEST_PLACED_AT_PICKUP_AGENCY`,
`READY_FOR_PICKUP`, `LOANED`, `RETURN_TRANSIT`, and `FINALISED` are business
states. They should not encode whether a host interaction was performed through
virtual records, a declarative request message, polling, a circulation command,
or some other host-specific mechanism.

Below that state machine, introduce a request lifecycle interaction layer. This
layer should be responsible for resolving and executing capabilities such as:

- supplier request placement
- borrower request placement or revision
- pickup request placement
- request and item tracking
- cancellation
- cleanup
- mark item in transit
- mark item received
- checkout to patron
- renewal
- prevent renewal

The general shape is:

```text
Workflow transition
  -> lifecycle service
    -> capability resolver
      -> selected strategy
        -> HostLmsClient / protocol client / other adapter
    -> canonical result
  -> projector updates PatronRequest/SupplierRequest
```

`HostLmsClient` remains useful, but it becomes an adapter surface used by
strategies. It should not be the model the workflow itself has to reason about.

For the borrowing-side problem, introduce a higher-level borrowing-placement
capability whose responsibility is:

> Given a DCB request context, place or revise the borrowing-side request in the
> patron's host system, and return canonical evidence of what was placed.

Existing integrations would use an imperative virtual-record placement strategy.
Non-imperative integrations would use a declarative request-placement strategy
implemented through an ISO18626 adapter for the current target protocol.

The workflow transition should remain focused on the business milestone. It
should not know whether placement was achieved by creating virtual records and a
hold, or by issuing a single request declaration.

The same rule should apply to later lifecycle steps. A transition should express
"cancel the supplier-side request", "track the borrowing-side request", or "loan
the item to the patron", not "call this exact combination of host LMS methods".

## Lifecycle Result Contract

A result from any lifecycle capability should be more expressive than the
current low-level projections such as `LocalRequest`, local item fields, pickup
item fields, or supplier request fields.

It should be able to describe, as applicable:

- lifecycle role, such as supplier, borrower, or pickup
- lifecycle operation, such as placement, tracking, cancellation, cleanup, or
  checkout
- host LMS code
- host request identifier, if known
- request status and raw status, if known
- selected or requested item identifier and barcode, if known
- bib/item artifacts created by DCB, if any
- whether artifacts are virtual DCB-created records or host-owned records
- cancellation and cleanup expectations
- tracking mode or polling hints
- host correlation identifiers or raw response references where useful

For example:

```java
class HostInteractionResult {
  LifecycleRole role;
  LifecycleOperation operation;
  String hostLmsCode;
  String requestId;
  String itemId;
  String itemBarcode;
  String status;
  String rawStatus;
  List<HostArtifact> artifacts;
  TrackingMode trackingMode;
  CleanupMode cleanupMode;
}
```

The existing `PatronRequest.local*`, `PatronRequest.pickup*`, and
`SupplierRequest.local*` fields can continue to be populated for compatibility,
but they should be treated as projections from richer lifecycle evidence rather
than as the canonical representation of every integration model.

This distinction is important for non-imperative integrations because the host
may return request evidence without creating DCB-owned virtual bib/item records.
It is also important for any future host integration that cannot support the same
polling, cleanup, or cancellation shape as the current implementations.

## Lifecycle Capabilities

The specific interfaces do not need to be designed all at once, but the
direction should be consistent. Capabilities should be scoped to a role and a
business operation.

For example:

```java
interface SupplyingAgencyRequestStrategy {
  Mono<HostInteractionResult> placeSupplyingAgencyRequest(
    SupplyingAgencyRequestCommand command);
}

interface BorrowingAgencyRequestStrategy {
  Mono<HostInteractionResult> placeBorrowingAgencyRequest(
    BorrowingAgencyRequestCommand command);

  Mono<HostInteractionResult> reviseBorrowingAgencyRequest(
    BorrowingAgencyRequestCommand command);
}

interface PickupAgencyRequestStrategy {
  Mono<HostInteractionResult> placePickupAgencyRequest(
    PickupAgencyRequestCommand command);
}

interface RequestTracking {
  Mono<TrackingSnapshot> poll(TrackingCommand command);
}

interface RequestCancellation {
  Mono<CancellationResult> cancel(CancellationCommand command);
}

interface RequestCleanup {
  Mono<CleanupResult> cleanup(CleanupCommand command);
}

interface CirculationAction {
  Mono<CirculationResult> receiveItem(ReceiveItemCommand command);

  Mono<CirculationResult> loanItem(LoanItemCommand command);

  Mono<CirculationResult> returnItem(ReturnItemCommand command);
}
```

The current code can initially implement these capabilities by wrapping the
existing services and host-client calls. New host styles can then be introduced
as additional strategies rather than by adding more branching to workflow
transitions.

## Non-Imperative Lifecycle Implications

Borrowing placement is the useful first pressure point, but it should not be
treated as an isolated special case. Once DCB stops assuming that it created a
virtual bib, virtual item, and host hold, several later operations need clearer
contracts.

### Placement Evidence

An imperative placement result can usually provide:

- local request id
- local bib id
- local item id
- local item status
- virtual artifact flags

A non-imperative placement result may only provide:

- host request id
- host correlation id
- raw request status
- selected supplier or item evidence
- protocol-specific response reference

Both are valid if the result makes the evidence explicit. The projector should
only populate existing `PatronRequest.local*` fields when the returned evidence
really has that meaning.

### Tracking

Current tracking often assumes DCB can poll known local request and item
identifiers. A non-imperative placement may require a different tracking mode:

- poll by host request id
- poll by protocol correlation id
- consume event-style updates
- track only coarse request state until the host exposes item-level evidence

Tracking should therefore depend on placement evidence and host capability, not
on a blanket assumption that local virtual item ids exist.

The live code currently matches the imperative model. `TrackingServiceV3` is a
scheduled service that selects due requests from `PatronRequest.nextScheduledPoll`
and then asks each relevant host system whether the stored request or item state
has changed. Borrowing-system tracking calls `getRequest(...)` and `getItem(...)`
through `HostLmsClient`, compares the returned state with `PatronRequest.local*`
fields, and publishes a tracking event when it detects a difference. Supplier
and pickup tracking follow the same broad pattern against their respective local
request and item evidence.

That is a polling model even though detected changes are then represented as
events inside DCB. The remote-system question is effectively:

```text
Given the request/item evidence DCB already has, has anything changed yet?
```

For declarative protocols, the triggering question can be inverted:

```text
Given this incoming protocol message, which DCB request changed and what
canonical lifecycle evidence does the message carry?
```

For the ISO18626 integration, DCB should treat inbound ISO18626 response/status
messages as lifecycle tracking input rather than as a reason to keep polling for
the same evidence. The ISO18626 adapter should own the exact message vocabulary
and partner profile details; the workflow should only see canonical lifecycle
evidence.

### Blocking Scheduled Polling For Event-Driven Requests

The smallest reliable blocker for scheduled polling is already present:
`PatronRequest.nextScheduledPoll`.

The scheduled tracking run calls `findScheduledChecks()`, whose query selects
only requests where `next_scheduled_poll < now()` and `is_too_long = false`.
The database index for this path is also filtered to rows where
`next_scheduled_poll IS NOT NULL`. Therefore, setting `nextScheduledPoll` to
`null` prevents the automatic polling worker from selecting the request at all.

Use that existing field as the immediate control point, but do not make
transitions know protocol details. Add a small tracking policy/capability
resolver above scheduling:

```java
enum TrackingMode {
  SCHEDULED_POLL,
  EVENT_DRIVEN,
  HYBRID
}

interface RequestTrackingPolicy {
  TrackingMode modeFor(RequestWorkflowContext context);

  default boolean schedulesAutomaticPolls(RequestWorkflowContext context) {
    return modeFor(context) != TrackingMode.EVENT_DRIVEN;
  }
}
```

Then keep `PatronRequestWorkflowService.scheduleNextCheck(...)` as the only
initial integration point:

```java
private Mono<RequestWorkflowContext> scheduleNextCheck(RequestWorkflowContext ctx) {
  final var patronRequest = ctx.getPatronRequest();

  if (!requestTrackingPolicy.schedulesAutomaticPolls(ctx)) {
    patronRequest.setNextScheduledPoll(null);
    return Mono.from(patronRequestRepository.saveOrUpdate(patronRequest))
      .map(ctx::setPatronRequest);
  }

  final var duration = trackingHelpers.getDurationFor(patronRequest.getStatus());
  patronRequest.setNextScheduledPoll(
    duration.map(value -> Instant.now().plus(value)).orElse(null));

  return Mono.from(patronRequestRepository.saveOrUpdate(patronRequest))
    .map(ctx::setPatronRequest);
}
```

The first implementation of `RequestTrackingPolicy` should always return
`SCHEDULED_POLL`. That makes the code path live but behaviourally identical for
all current systems. Declarative hosts can later opt into `EVENT_DRIVEN` per
host, role, and operation.

Manual tracking should be considered separately from automatic scheduled
polling. It can remain available as a diagnostic or recovery action even for
event-driven requests, but it should be explicit that manual tracking is not the
normal lifecycle driver for those requests.

### Inbound Message Handling

Event-driven protocols still need to feed the same DCB workflow engine. The
inbound protocol endpoint should translate protocol-specific messages into a
canonical tracking command or snapshot:

```java
class InboundLifecycleMessage {
  String protocol;
  LifecycleRole role;
  LifecycleOperation operation;
  String hostLmsCode;
  String hostRequestId;
  String correlationId;
  String status;
  String rawStatus;
  String itemId;
  String itemBarcode;
  Instant messageTimestamp;
  Object rawMessageReference;
}
```

The handler shape should be:

```text
protocol controller / listener
  -> protocol parser and verifier
  -> request correlation resolver
  -> canonical lifecycle event projector
  -> PatronRequest/SupplierRequest update
  -> PatronRequestWorkflowService.progressUsing(...)
  -> protocol acknowledgement
```

The correlation resolver should use the strongest available identifier first:
DCB patron request id, DCB-supplied protocol correlation id, host request id, and
then any agreed protocol-specific reference. The handler must be idempotent
because inbound protocol messages may be retried by the sender. A repeated
message should update audit/counters if useful, but it should not duplicate
state transitions or recreate artifacts.

### Cancellation And Cleanup

Cleanup currently has to consider artifacts DCB may have created in the host
system. For non-imperative placement, DCB may not own any host bib or item
artifacts. Cleanup may become:

- cancel the declared request
- close or suppress host-created request artifacts
- do nothing because the host owns the lifecycle
- record that cleanup is unsupported or externally managed

The placement result should therefore carry enough information to decide the
cleanup mode later. Do not infer cleanup obligations from the strategy name
alone.

### Checkout, Renewal, And Return

Non-imperative placement does not automatically imply non-imperative circulation.
A host may accept declarative request placement but still require ordinary
circulation commands for receive, checkout, renewal, return, and prevent-renewal
actions.

Those operations should be extracted only when there is real variation to
support. The immediate goal is to prevent borrowing placement from encoding
assumptions that later lifecycle steps cannot rely on.

## Concrete First Slice

The conceptual model above should not be implemented as a broad rewrite. The
first concrete slice should be narrow: insert a seam around borrowing-side
request handling while keeping the existing imperative behaviour as the only live
implementation.

The aim of the first slice is not to build the whole lifecycle framework. It is
to create a real, mergeable place where declarative borrower request handling
can later live.

### 1. Introduce a narrow borrowing agency request strategy

Start with the specific pressure point:

```java
public interface BorrowingAgencyRequestStrategy {
  StrategyType type();

  boolean supports(BorrowingAgencyRequestStrategyContext context);

  Mono<BorrowingAgencyRequestResult> place(RequestWorkflowContext ctx);

  Mono<BorrowingAgencyRequestResult> revise(RequestWorkflowContext ctx);
}
```

Here, `Strategy` is not just a design-pattern label. It means the approach DCB
is taking when talking to the remote borrowing host.

```java
enum StrategyType {
  IMPERATIVE,
  DECLARATIVE
}
```

An imperative strategy means DCB orchestrates lower-level host operations, such
as creating virtual records and then placing a hold. A declarative strategy
means DCB sends a higher-level request declaration and lets the remote system
handle the internal choreography.

This interface should be deliberately smaller than the final lifecycle model. It
gives the transition an application-level strategy to call without forcing the
whole codebase to adopt a new abstraction at once.

The strategy interface can be added to the live codebase with effectively no
operational blast radius if the only registered implementation is the existing
imperative behaviour and resolver defaults are unconditional.

The important constraint is that activation must be explicit. Missing
configuration should always mean:

```text
placement strategy: imperative
tracking mode: scheduled-poll
protocol adapter: none
```

That lets the new abstraction exist in production while every current host keeps
using the exact same host calls, persisted fields, scheduled polling, audit
flow, and workflow transitions.

### Transparent Activation Shape

Add the new behaviour in layers, with each layer defaulting to the existing
imperative path:

1. Add strategy interfaces and resolvers whose default answer is the current
   imperative implementation.
2. Add tracking policy with default `SCHEDULED_POLL`.
3. Add declarative strategy classes, but do not select them from default config.
4. Add inbound protocol endpoints/listeners, but reject or ignore messages for
   hosts that are not explicitly configured for event-driven handling.
5. Activate by host and capability only after configuration exists.

For example:

```yaml
capabilities:
  borrowing-agency-request:
    strategy: declarative
    protocol: iso18626
  borrower-tracking:
    mode: event-driven
    protocol: iso18626
```

Absent that configuration, the resolver should behave as if the host had:

```yaml
capabilities:
  borrowing-agency-request:
    strategy: imperative
  borrower-tracking:
    mode: scheduled-poll
```

This gives the new code a production runtime path without changing current
system behaviour. It also gives support teams one clear activation lever: host
capability configuration. There should be no protocol sniffing, client-type
inspection, or fallback from an explicitly configured declarative mode to the
imperative path. If a host asks for declarative/event-driven behaviour and the
matching strategy or protocol adapter is unavailable, fail fast as a
misconfiguration.

### 2. Introduce a borrowing agency request result

The first result object can mostly mirror the fields the current system already
needs to write:

```java
public class BorrowingAgencyRequestResult {
  String hostLmsCode;
  String localRequestId;
  String localRequestStatus;
  String rawLocalRequestStatus;
  String localBibId;
  String localItemId;
  String localItemStatus;
  boolean createdVirtualBib;
  boolean createdVirtualItem;
}
```

This can grow later into the more general `HostInteractionResult`, but it is
already enough to separate remote request evidence from transition mechanics.

### 3. Wrap the existing behaviour

Create a legacy/default implementation that delegates to today's
`BorrowingAgencyService` flow:

```java
public class ImperativeBorrowingAgencyRequestStrategy
    implements BorrowingAgencyRequestStrategy {

  public StrategyType type() {
    return StrategyType.IMPERATIVE;
  }

  public Mono<BorrowingAgencyRequestResult> place(RequestWorkflowContext ctx) {
    return borrowingAgencyService.placePatronRequestAtBorrowingAgency(ctx)
      .map(this::toResult);
  }

  public Mono<BorrowingAgencyRequestResult> revise(RequestWorkflowContext ctx) {
    return borrowingAgencyService.updatePatronRequestAtBorrowingAgency(ctx)
      .map(this::toResult);
  }
}
```

At this stage there is no declarative implementation and no behavioural change.
The existing implementation is simply being named as one strategy.

### 4. Add a projector

Move compatibility mapping into one named place:

```java
public class BorrowingAgencyRequestProjector {
  PatronRequest apply(
    PatronRequest patronRequest,
    BorrowingAgencyRequestResult result) {

    return patronRequest
      .setLocalRequestId(result.localRequestId())
      .setLocalRequestStatus(result.localRequestStatus())
      .setRawLocalRequestStatus(result.rawLocalRequestStatus())
      .setLocalBibId(result.localBibId())
      .setLocalItemId(result.localItemId())
      .setLocalItemStatus(result.localItemStatus())
      .setStatus(REQUEST_PLACED_AT_BORROWING_AGENCY);
  }
}
```

This projector is the compatibility layer between canonical remote request
evidence and the existing `PatronRequest.local*` fields. A declarative strategy
can later return different evidence without forcing the transition to understand
that difference.

### 5. Route the transition through the new seam

The transition can then depend on a request strategy service rather than
directly on `BorrowingAgencyService`:

```java
public class BorrowingAgencyRequestStrategyService {
  Mono<BorrowingAgencyRequestResult> placeOrRevise(RequestWorkflowContext ctx) {
    var operation = isReResolution(ctx)
      ? LifecycleOperation.REVISE_REQUEST
      : LifecycleOperation.PLACE_REQUEST;

    var strategy = borrowingAgencyRequestStrategyResolver.resolve(ctx, operation);

    if (isReResolution(ctx)) {
      return strategy.revise(ctx);
    }

    return strategy.place(ctx);
  }
}
```

The transition becomes:

```java
return borrowingAgencyRequestStrategyService.placeOrRevise(ctx)
  .map(result -> borrowingAgencyRequestProjector.apply(
    ctx.getPatronRequest(), result))
  .thenReturn(ctx);
```

The acceptance criterion for this change should be simple: all existing
borrowing request behaviour and tests still pass.

### 6. Add a resolver, initially with one strategy

Only after the seam exists, add the resolver:

```java
public class BorrowingAgencyRequestStrategyResolver {
  BorrowingAgencyRequestStrategy resolve(
    RequestWorkflowContext ctx,
    LifecycleOperation operation) {

    return imperativeBorrowingAgencyRequestStrategy;
  }
}
```

This looks redundant at first. That is intentional. It creates the future policy
point while still selecting the legacy path for every host.

The important separation is that `placeOrRevise` decides the lifecycle operation
for this request, while the resolver decides which host capability implements
that operation.

```text
BorrowingAgencyRequestStrategyService:
  status/context -> PLACE_REQUEST or REVISE_REQUEST

BorrowingAgencyRequestStrategyResolver:
  role + operation + borrowing host capability -> strategy
```

The service should not inspect Java client types or know about ISO18626. It
should only know that the borrower-side request needs to be placed or revised.

### 7. Make resolver selection configurable

Then introduce a small host capability setting. Missing configuration should
default to the current imperative strategy.

```yaml
borrowing-agency-request:
  strategy: imperative
```

A declarative host can later opt into the declarative strategy and specify
ISO18626 as the transport/protocol used by that strategy:

```yaml
borrowing-agency-request:
  strategy: declarative
  protocol: iso18626
```

The resolver then becomes:

```java
BorrowingAgencyRequestStrategy resolve(
  RequestWorkflowContext ctx,
  LifecycleOperation operation) {

  var borrowingHost = ctx.getPatronSystemCode();

  var configuredMode = capabilityConfig
    .forHost(borrowingHost)
    .strategy("borrowing-agency-request")
    .orElse(StrategyType.IMPERATIVE);

  return strategies.stream()
    .filter(strategy -> strategy.type().equals(configuredMode))
    .filter(strategy -> strategy.supports(selectionFrom(ctx, operation)))
    .findFirst()
    .orElseThrow(() -> misconfiguredCapability(
      borrowingHost, configuredMode, operation));
}
```

Existing hosts remain on the legacy path unless explicitly configured
otherwise.

Each strategy should expose its interaction type:

```java
interface BorrowingAgencyRequestStrategy {
  StrategyType type();

  boolean supports(BorrowingAgencyRequestStrategyContext context);

  Mono<BorrowingAgencyRequestResult> place(RequestWorkflowContext ctx);

  Mono<BorrowingAgencyRequestResult> revise(RequestWorkflowContext ctx);
}
```

The configured strategy type is the primary selection mechanism. `supports(...)` is a
validation guard: it can reject a strategy when the operation, workflow, host, or
available request evidence is incompatible. If a host explicitly asks for
`declarative` and no compatible strategy exists, the system should fail fast
rather than silently falling back to the imperative strategy.

### 8. Add declarative implementations

Once the seam, result, projector, and resolver are all live, add a declarative
strategy. ISO18626 should sit below that as a transport or protocol adapter, not
as a top-level lifecycle strategy.

```java
public class DeclarativeBorrowingAgencyRequestStrategy
    implements BorrowingAgencyRequestStrategy {

  public StrategyType type() {
    return StrategyType.DECLARATIVE;
  }

  public Mono<BorrowingAgencyRequestResult> place(RequestWorkflowContext ctx) {
    var transport = declarativeRequestTransportResolver.resolve(ctx);
    var request = declarativeRequestFactory.from(ctx);

    return transport.send(request)
      .map(this::toBorrowingAgencyRequestResult);
  }
}
```

The transport layer then handles protocol-specific mechanics:

```java
interface DeclarativeRequestTransport {
  String protocol();

  Mono<DeclarativeRequestResult> send(DeclarativeRequest request);
}
```

```java
class Iso18626RequestTransport implements DeclarativeRequestTransport {
  public String protocol() {
    return "iso18626";
  }

  public Mono<DeclarativeRequestResult> send(DeclarativeRequest request) {
    // Translate the DCB declarative request into ISO18626.
    // Submit the ISO18626 request.
    // Return protocol-neutral declarative result.
  }
}
```

The transition does not change for this step. The existing imperative strategy
does not change. The declarative strategy is selected only for hosts configured
to use it, and the protocol is selected under that strategy.

The strategy list is intentionally small:

```text
imperative
declarative
```

ISO18626 is a transport/protocol choice inside the declarative strategy. It
should not leak into the workflow transition. The workflow chooses role and
operation; host capability configuration chooses the strategy and protocol; the
selected strategy handles remote-system mechanics.

### Suggested PR sequence

This can be delivered as several small, mergeable changes:

1. Add `BorrowingAgencyRequestResult` and
   `BorrowingAgencyRequestProjector`.
2. Add `BorrowingAgencyRequestStrategy` and the imperative implementation
   wrapping the current borrowing flow.
3. Move `PlacePatronRequestAtBorrowingAgencyStateTransition` to call the new
   strategy service, still legacy-only.
4. Add the resolver, still selecting only the imperative strategy.
5. Add config-based resolver selection with default legacy behaviour.
6. Add the declarative borrowing-agency request strategy behind config.
7. Add ISO18626 as the declarative transport.
8. Extract borrower tracking when declarative integrations need different
   tracking semantics.
9. Extract borrower cleanup/cancellation when declarative integrations need different cleanup or
   cancellation semantics.

The important rule is:

```text
new abstraction first
legacy implementation as default
new implementation opt-in by host/capability config
```

That keeps the live codebase deployable while the new model grows around the
existing behaviour.

## Migration Approach

1. Define lifecycle roles and operations.

   Start with a small vocabulary:

   ```java
   enum LifecycleRole {
     SUPPLIER,
     BORROWER,
     PICKUP
   }

   enum LifecycleOperation {
     PLACE_REQUEST,
     REVISE_REQUEST,
     TRACK_REQUEST,
     TRACK_ITEM,
     CANCEL_REQUEST,
     CLEANUP_ARTIFACTS,
     MARK_TRANSIT,
     MARK_RECEIVED,
     CHECKOUT,
     RENEW,
     PREVENT_RENEWAL
   }
   ```

   This gives the resolver a stable language that is independent of host LMS
   method names.

2. Wrap the current behavior as the default imperative strategies.

   The existing `BorrowingAgencyService` choreography can remain functionally
   unchanged at first. It becomes the implementation of the current live
   imperative borrowing-agency request strategy.

   The same wrapping approach can be used for supplier placement, pickup
   placement, tracking, cancellation, cleanup, checkout, and renewal. The first
   aim should be to create a stable lifecycle interaction layer without changing
   live behaviour.

3. Add strategy resolvers above `HostLmsClient`.

   Selection should be based on role and host capability/configuration, not on a
   broad host LMS inheritance hierarchy. Existing systems select the imperative
   strategy. ISO18626 systems select the declarative strategy, with ISO18626
   chosen beneath that as the protocol adapter.

   "Above `HostLmsClient`" means the workflow should ask for a capability that
   performs the DCB use case, not for a raw host client and then decide which
   host operations to orchestrate.

   A sketch of the shape:

   ```java
   interface BorrowingAgencyRequestStrategy {
     StrategyType type();

     boolean supports(BorrowingAgencyRequestStrategyContext context);

     Mono<BorrowingAgencyRequestResult> place(RequestWorkflowContext ctx);

     Mono<BorrowingAgencyRequestResult> revise(RequestWorkflowContext ctx);
   }
   ```

   The current implementation would be represented by a strategy that internally
   uses `HostLmsClient.createBib`, `HostLmsClient.createItem`, and
   `HostLmsClient.placeHoldRequestAtBorrowingAgency`.

   ```java
   class ImperativeBorrowingAgencyRequestStrategy
       implements BorrowingAgencyRequestStrategy {

     Mono<BorrowingAgencyRequestResult> place(RequestWorkflowContext ctx) {
       // Existing choreography:
       // create virtual bib
       // create virtual item
       // place hold
       // return canonical request evidence
     }
   }
   ```

   A declarative implementation would be another strategy at the same level. It
   would use a protocol transport beneath it, but the workflow would not see the
   ISO18626-specific operation.

   ```java
   class DeclarativeBorrowingAgencyRequestStrategy
       implements BorrowingAgencyRequestStrategy {

     Mono<BorrowingAgencyRequestResult> place(RequestWorkflowContext ctx) {
       var transport = declarativeRequestTransportResolver.resolve(ctx);
       var request = declarativeRequestFactory.from(ctx);

       return transport.send(request)
         .map(this::toBorrowingAgencyRequestResult);
     }
   }
   ```

   A resolver then chooses the strategy for the current role and host. This
   resolver is the policy point where host configuration and capabilities are
   interpreted.

   ```java
   class BorrowingAgencyRequestStrategyResolver {
     BorrowingAgencyRequestStrategy resolve(
       RequestWorkflowContext ctx,
       LifecycleOperation operation) {

       return strategies.stream()
         .filter(strategy -> strategy.supports(selectionFrom(ctx, operation)))
         .findFirst()
         .orElseThrow();
     }
   }
   ```

   The transition remains business-oriented:

   ```java
   var strategy = borrowingAgencyRequestStrategyResolver.resolve(
     ctx, LifecycleOperation.PLACE_REQUEST);

   return strategy.place(ctx)
     .map(result -> borrowingAgencyRequestProjector.apply(ctx, result));
   ```

   This is different from asking `HostLmsClient` whether it supports ISO18626
   request placement and branching inside `BorrowingAgencyService`. The resolver
   is choosing an application strategy. `HostLmsClient` remains the lower-level
   adapter used by strategies that need it.

   Capability selection could initially be simple and configuration-driven:

   ```yaml
   host-lms:
     code: example-declarative-host
     borrowing-agency-request:
       strategy: declarative
       protocol: iso18626
   ```

   Over time, this could evolve into explicit capability metadata, for example:

   ```yaml
   capabilities:
     borrowing-agency-request:
       strategy: declarative
       protocol: iso18626
     borrowing-cleanup:
       mode: cancel-request-only
     tracking:
       mode: request-status-polling
   ```

   The important point is that the capability is scoped to the role and use
   case. The system does not need to decide that an entire host LMS is
   "declarative" or "imperative" in every respect.

   Generalised across the lifecycle, the resolver policy might look more like:

   ```java
   var capability = lifecycleCapabilityResolver.resolve(
     LifecycleRole.BORROWER,
     LifecycleOperation.PLACE_REQUEST,
     context);

   return capability.execute(command)
     .map(result -> borrowingAgencyRequestProjector.apply(ctx, result));
   ```

   Selection can vary by role and operation for the same host:

   ```text
   Role: BORROWER
   Operation: PLACE_REQUEST
   Host: example-iso18626-host
   Strategy: Declarative
   Protocol: ISO18626

   Role: BORROWER
   Operation: CLEANUP_ARTIFACTS
   Host: example-iso18626-host
   Strategy: Cancel request only / no virtual artifacts

   Role: PICKUP
   Operation: PLACE_REQUEST
   Host: existing-polaris-host
   Strategy: Imperative

   Role: SUPPLIER
   Operation: TRACK_REQUEST
   Host: existing-sierra-host
   Strategy: Poll request by local hold id
   ```

4. Introduce canonical lifecycle input and output models.

   These models should express DCB-level facts: patron identity, pickup
   location, supplier item evidence, selected bib/work, transaction note, and
   patron request id. They should not be ISO18626 DTOs and should not be tied to
   `PlaceHoldRequestParameters`.

   Request commands, tracking commands, cleanup commands, and circulation
   commands should all describe what DCB is trying to accomplish. Translation
   into Sierra, Polaris, FOLIO, Alma, ISO18626, or another protocol should happen
   inside the selected strategy.

5. Move workflow transitions up a level.

   A transition should express lifecycle intent only:

   ```java
   class PlacePatronRequestAtBorrowingAgencyStateTransition {
     Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
       return borrowingAgencyRequestStrategyService.placeOrRevise(ctx)
         .map(result -> borrowingAgencyRequestProjector.apply(ctx, result));
     }
   }
   ```

   The transition should not know whether request handling means `createBib`,
   `createItem`, `placeHoldRequestAtBorrowingAgency`, or an ISO18626 request.

6. Make downstream operations capability-aware.

   Cleanup, cancellation, and tracking currently assume that DCB may have
   created virtual bib/item records. A declarative request strategy may require
   cancel-only cleanup, different polling, or different evidence handling.

   Tracking is likely the next major pressure point after placement. Current
   tracking assumes it can poll request and item state through known local
   request and item identifiers. Declarative APIs may instead
   expose request-status polling, event-style updates, or only coarse request
   state.

   Tracking should therefore become capability-based:

   ```java
   interface LifecycleTracker {
     boolean supports(TrackingContext context);

     Mono<TrackingSnapshot> track(TrackingCommand command);
   }
   ```

   Workflow code should react to a `TrackingSnapshot`, not to the fact that a
   particular implementation called `getItem(localItemId, localRequestId)`.

7. Preserve the live database contract during migration.

   Continue writing existing `PatronRequest.localRequestId`, `localItemId`,
   `localBibId`, and related fields where they make sense. Longer term, consider
   a separate host-placement/artifact record keyed by patron request, role, and
   host system.

8. Repeat by lifecycle pressure, not mechanically.

   This should not be a single large abstraction pass across the whole codebase.
   Start where variation is real and costly:

   - borrowing placement
   - pickup placement
   - supplier placement
   - tracking and polling
   - cancellation
   - cleanup
   - checkout, receive, loan, and return actions

   Each extraction should follow the same pattern: command, strategy interface,
   resolver, canonical result, projector.

## Things To Avoid

Avoid adding `RequestItem` directly to `HostLmsClient` and branching inside
`BorrowingAgencyService`. That would keep the application layer coupled to host
implementation details and would make the borrowing workflow harder to reason
about.

Similarly, avoid adding lifecycle-specific branches directly to transitions such
as "if this protocol then do this, else call `HostLmsClient`". That would spread
protocol knowledge across the workflow engine and make every later lifecycle
operation harder to change.

Avoid making non-imperative integrations pretend they created virtual bibs or
items if they did not. That would leak false assumptions into cleanup,
cancellation, tracking, and audit.

Avoid creating one universal "declarative host LMS" abstraction. The distinction
belongs at the capability level. A single host may be declarative for request
placement, imperative for checkout, limited for tracking, and special for
cleanup.

## Summary

The DCB workflow states should remain stable. They are business milestones, not
descriptions of host LMS API style.

The modelling shift should happen below the state transitions. Introduce a
request lifecycle interaction layer that resolves role-specific capabilities for
placement, tracking, cancellation, cleanup, checkout, renewal, and other host
interactions.

Borrowing placement can be the first extraction, choosing between the current
imperative virtual-record choreography and declarative request placement through
ISO18626. Subsequent lifecycle steps can adopt the same pattern as real
variation appears.

This preserves existing live integrations while allowing non-imperative host
styles to fit the model naturally instead of being forced through low-level
virtual-record assumptions across the whole request lifecycle.

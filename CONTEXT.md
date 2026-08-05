# DCB Service Context

## Purpose

`dcb-service` is a Micronaut service for Direct Consortial Borrowing. It
resolves patron requests against shared bibliographic/item data, places holds in
peer library systems, tracks lifecycle state, and exposes operational/admin APIs.

## Repository Shape

- `dcb/`: main Micronaut application.
- `openrs-core/`: shared OpenRS/domain support.
- `src/xsd/`: protocol schemas shared by the build; NCIP v2.02 is
  `src/xsd/ncip_v2_02.xsd`.
- `docs/backlog/`: active and completed implementation notes.
- `docs/ADRs/`: architectural decisions. Read before CI/dependency/test-run
  changes.

## Main Runtime Boundaries

- HTTP controllers live mainly under `org.olf.dcb.core.api`.
- Core domain models live under `org.olf.dcb.core.model`.
- Persistence ports live under `org.olf.dcb.storage`.
- Host LMS integration lives under `org.olf.dcb.core.interaction`.
- Request lifecycle strategy code lives under `org.olf.dcb.request.lifecycle`.
- Patron request workflow transitions live under `org.olf.dcb.request.workflow`.

Most request/workflow code is Reactor based and returns `Mono`/`Flux`.

## Host LMS Boundary

`HostLmsService` resolves configured Host LMS records and creates a
`HostLmsClient` implementation from the configured client class.

`HostLmsClient` is the imperative adapter contract. It includes operations such
as:

- `placeHoldRequestAtSupplyingAgency`
- `placeHoldRequestAtBorrowingAgency`
- `getRequest`
- `cancelHoldRequest`
- patron/item/bib operations

Configured Host LMS codes are important identifiers. In the NCIP spike,
incoming NCIP `AgencyId` values are treated as Host LMS codes.

For declarative NCIP peers, the target Host LMS config must include:

- `ncip-endpoint-url`: absolute NCIP v2.02 endpoint URL, for example
  `https://peer.example.org/ncip/v2_02`.

The spike includes a partial NCIP-backed Host LMS adapter:
`org.olf.dcb.request.lifecycle.ncip.ORSApplianceHostLMS`. It implements
supplier `RequestItem` and borrower `AcceptItem` placement only. Wider
`HostLmsClient` method coverage should grow by lifecycle slice. The current
broad `HostLmsClient` surface is architectural debt, but it is still the natural
DCB plugin point for black-box request workflow tests.

Current placement capabilities are `CanPlaceSupplyingAgencyRequest` and
`CanPlaceBorrowingAgencyRequest`. `HostLmsClient` extends both, so existing
clients compile unchanged. Future capability interfaces should be extracted only
when a real caller is moved to depend on them. Do not pre-create speculative
tracking, cancellation, patron, item, or bib capability interfaces.

Capability detection must be GraalVM-safe: use direct Java `instanceof` checks
against statically referenced interfaces, not reflection or annotation
introspection.

For partial adapters, `AbstractHostLmsClient` provides `Mono.empty()` defaults
for unsupported methods. This reduces boilerplate and avoids accidental `null`
returns.

## Request Workflow

The main request object is `PatronRequest`. Supplier-side work is represented by
`SupplierRequest`.

The normal DCB request path is:

1. Resolve a request to a supplier.
2. Place supplier-side request.
3. Track supplier evidence until confirmed/transit.
4. Place borrower-side request.
5. Continue workflow using projected local statuses.

Existing workflow transitions mostly reason over compatibility fields such as
`SupplierRequest.localStatus`, `SupplierRequest.localId`,
`PatronRequest.localRequestStatus`, and `PatronRequest.localRequestId`.

## Declarative Lifecycle Boundary

The spike adds a protocol-neutral lifecycle layer:

- `LifecycleRole`: `SUPPLIER`, `BORROWER`, `PICKUP`.
- `LifecycleOperation`: currently request placement/revision/cancellation style
  operations.
- `StrategyType`: imperative vs declarative placement.
- `TrackingMode`: polling vs event-driven tracking.
- `LifecycleCapabilitiesConfiguration`: explicit activation configuration.

Missing lifecycle capability config defaults to current imperative behavior.

Declarative placement sends a `DeclarativeTransportRequest` through
`DeclarativeRequestTransport` and projects `DeclarativeTransportResponse` back
onto existing request fields. This is the current spike mechanism; production
black-box testing should grow toward exercising the same behavior through an
NCIP-backed `HostLmsClient`.

Inbound event/response evidence maps to `InboundLifecycleMessage`, then
`InboundLifecycleMessageHandler` projects it onto `PatronRequest` or
`SupplierRequest`, audits it, and asks the workflow to progress.

## NCIP v2.02 Boundary

All NCIP-specific code must remain under:

`org.olf.dcb.request.lifecycle.ncip`

Current NCIP protocol id:

`ncip-v202`

Current inbound endpoint:

`POST /ncip/v2_02`

Content types:

- request: `application/xml` or `text/xml`
- response: `application/xml` for NCIP response XML, or HTTP 204 for accepted
  inbound response messages

Current inbound messages:

- `ItemShipped`: maps to supplier evidence, returns `ItemShippedResponse`.
- `RequestItemResponse`: maps to supplier placement evidence, returns HTTP 204.
- `AcceptItemResponse`: maps to borrower placement evidence, returns HTTP 204.

Invalid XML, unsupported messages, and NCIP `Problem` response payloads return an
NCIP `Problem` XML response.

Current outbound transport:

- Bean: `NcipDeclarativeRequestTransport`.
- Transport contract: `DeclarativeRequestTransport`.
- Host lookup: `DeclarativeTransportRequest.hostLmsCode`.
- Endpoint config: target Host LMS `clientConfig.ncip-endpoint-url`.
- Request: HTTP POST with `Content-Type: application/xml` and
  `Accept: application/xml`.
- Response: NCIP XML body containing `RequestItemResponse` for `RequestItem` or
  `AcceptItemResponse` for `AcceptItem`.

Current outbound Host LMS adapter:

- Bean: `ORSApplianceHostLMS`.
- Package: `org.olf.dcb.request.lifecycle.ncip`.
- Config: Host LMS `clientConfig.ncip-endpoint-url`.
- Supplier placement: builds NCIP `RequestItem`.
- Borrower placement: builds NCIP `AcceptItem`.
- Unsupported Host LMS methods inherit `Mono.empty()` defaults.
- Context test coverage proves `HostLmsService` can instantiate it and that real
  Micronaut HTTP calls reach `/ncip/v2_02`.

## NCIP Mapping Defaults

- Supplier outbound placement uses `RequestItem`.
- Borrower outbound placement/preparation uses `AcceptItem`.
- `RequestId/RequestIdentifierValue` carries role-specific DCB correlation:
  `{patronRequestId}:SUPPLIER` or `{patronRequestId}:BORROWER`.
- Inbound `ResponseHeader/FromAgencyId/AgencyId` is required and maps to
  `hostLmsCode`.
- Outbound NCIP response messages must match the original lifecycle role and
  correlation id.
- Successful `RequestItemResponse` and `AcceptItemResponse` map to local status
  `CONFIRMED`, because existing workflow confirmation guards understand that
  status.
- `ItemShipped` maps to supplier status `SHIPPED`.
- Raw NCIP XML is not persisted in this spike; only derived audit/reference data
  is projected.
- NCIP auth is explicitly out of scope for the spike and required before
  production use.

## Isolation Rules

- Workflow code must not import NCIP classes.
- Non-NCIP Host LMS adapters must not import NCIP declarative lifecycle classes.
- An NCIP-backed Host LMS adapter may use the NCIP package deliberately.
- NCIP XML/schema/constant handling stays in the NCIP package.
- Removing the NCIP package/config should leave imperative behavior intact.

Architecture tests enforce the package boundary.

## Test Notes

Focused NCIP tests:

```bash
GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew :dcb:test --tests 'org.olf.dcb.request.lifecycle.ncip.*' --no-daemon
```

NCIP plus lifecycle boundary regression:

```bash
GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew :dcb:test --tests org.olf.dcb.architecture.ProtocolAdapterArchitectureTests --tests 'org.olf.dcb.request.lifecycle.ncip.*' --tests org.olf.dcb.request.lifecycle.placement.RequestPlacementStrategyTests --tests org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessageHandlerTests --tests org.olf.dcb.request.lifecycle.tracking.RequestTrackingPolicyTests --tests org.olf.dcb.request.workflow.DeclarativeCancellationGuardTests --no-daemon
```

Full discovery/API-facing workflow tests for NCIP are still a follow-on. The
adapter does not yet implement enough patron, bib, item, preflight, and tracking
methods for a black-box patron request test to be meaningful.

Full suite policy is defined in `docs/ADRs/0001-full-suite-test-timeout-policy.md`.

# dcb-service Architecture

This is a high-level starting model for developers. Keep implementation details
in focused docs or module notes.

## Main Modules

- `core.model`: persisted domain records and shared value objects.
- `storage`: repository interfaces and persistence adapters.
- `request.workflow`: DCB request state machine transitions.
- `request.fulfilment`: request orchestration and host-facing fulfilment work.
- `request.lifecycle`: lifecycle strategy and evidence boundaries. See
  `dcb/src/main/java/org/olf/dcb/request/lifecycle/MODULE.md`.
- `request.lifecycle.placement`: imperative/declarative placement strategy
  selection and placement result projection.
- `request.lifecycle.ncip`: NCIP protocol adapter.
- `tracking`: scheduled polling, host-state change detection, and tracking
  evidence adaptation. See `dcb/src/main/java/org/olf/dcb/tracking/MODULE.md`.
- `core.interaction`: host LMS client contracts and implementations.
- `core.api` and `graphql`: external HTTP/GraphQL APIs.
- `ingest`, `indexing`, `availability`: bibliographic and availability data
  pipelines.

Add `MODULE.md` files under significant modules when local ownership rules,
extension points, or constraints need more detail than this overview.

## Ownership

- `request.workflow` owns DCB patron request state transitions.
- `request.lifecycle` owns strategy selection and lifecycle evidence boundaries.
- Protocol adapters own protocol parsing, validation, authentication, and
  acknowledgement.
- Host LMS clients own external system calls and system-specific status mapping.
- Repositories own database access. Application services should not bypass them.
- Admin and GraphQL layers expose state; they should not own workflow decisions.

## Dependency Rules

- Workflow must not depend on protocol packages such as
  `request.lifecycle.ncip`.
- Domain models must not depend on protocol packages or host LMS implementations.
- Protocol adapters must map external messages into lifecycle abstractions; they
  must not directly decide DCB request state.
- Host LMS implementations must not depend on workflow transitions.
- Persistence remains behind repositories in `storage`.
- New cross-module dependencies need an explicit architecture note or backlog
  item.

## Extension Points

- Placement strategy selection:

  ```text
  workflow transition
    -> lifecycle placement strategy service
    -> imperative or declarative adapter
    -> placement result projection
  ```

- Lifecycle evidence ingestion:

  ```text
  polling or protocol inbound message
    -> canonical lifecycle evidence
    -> evidence projection and audit
    -> workflow progression when the caller is reactive
  ```

  `LifecycleEvidenceProjector` owns projection/audit. `LifecycleEvidenceIngestor`
  wraps it for reactive inbound messages and then runs workflow progression.
  Polling may use the projector directly because `TrackingService` already runs
  workflow progression after checking all systems.
  Projection is based on canonical lifecycle fields such as role, resource,
  operation, and status. Polling resource names are compatibility metadata only.

- Host LMS support: implement or extend `HostLmsClient` capability slices.
- Protocol support: add protocol adapters below `request.lifecycle.<protocol>`
  and map to lifecycle abstractions.

## Persistence

Persistence is owned by repositories in `storage` and implementations in
`storage.postgres`.

`PatronRequest.status` records workflow state. `PatronRequest.outcome` separately
records the business result (`SUPPLIED`, `NOT_SUPPLIED`, `CANCELLED`, or
`UNKNOWN`) so finalisation does not erase whether fulfilment succeeded.

No database schema changes are permitted without explicit approval. This includes
new tables, columns, indexes, migrations, and changes to persisted domain model
shape.

## External Integrations

External system details are isolated in:

- `core.interaction.*` for LMS APIs.
- `request.lifecycle.ncip` for NCIP.
- API/controller packages for inbound service APIs.
- ingest/indexing packages for bibliographic data sources and search systems.

Generic OAI-PMH ingest checkpoints from the greatest source datestamp observed and accepts inclusive
boundary replay. FOLIO OAI is the explicit exception: it retains the internal-clock checkpoint because
FOLIO's second-resolution datestamps can place more than a page of records on one boundary.

Do not leak protocol DTOs, XML models, or host-specific response objects into
workflow transitions.

The `indexing` module owns shared-index settings. Deployments configure the
replica count with `dcb.index.number-of-replicas` (default `1`); DCB applies it
when creating an index and reconciles the current version at startup.

## Boundary Rules

- Imperative HTTP routes that synchronously wait on database, HTTP, filesystem or
  equivalent work must dispatch at the controller boundary with
  `@ExecuteOn(TaskExecutors.BLOCKING)`. This keeps Netty event loops available for
  the completions on which that work may depend. Reactive routes remain on the
  event loop only when every reachable operation is non-blocking; reactive return
  types alone are not proof. Do not replace this classification with a global
  `micronaut.server.thread-selection` change.
- DCB request state changes belong in `Handle...` workflow transitions.
- DCB state-model documentation is generated from `PatronRequestStateTransition`
  beans through `StateModelService`. Do not maintain a parallel static state
  model file.
- `request.workflow` transitions decide DCB state movement from projected
  evidence. They must not call `HostLmsService` or `HostLmsClient` directly.
  Host side effects must sit behind lifecycle, placement, or fulfilment ports so
  declarative and imperative host strategies remain interchangeable.
- `Handle...` workflow transitions are the application reaction layer. They
  consume projected peer state and decide what DCB should do next.
- Incoming notifications update peer evidence, not DCB request state directly.
- Polling and reactive inbound messages should converge at the lifecycle
  evidence boundary before projection, audit, and workflow progression.
- `TrackingServiceV3` is the default polling implementation. `TrackingServiceV4`
  is selected only with `dcb.tracking.service=v4` and routes polling state
  changes through lifecycle evidence projection.
- `TrackingScheduler` owns scheduled tracking task registration. Tracking
  service implementations are invoked through the `TrackingService` interface
  and must not register their own scheduled tasks.
- `RequestTrackingPolicy` owns per-role automatic-poll eligibility across the
  request lifecycle. Mixed-mode requests poll only scheduled roles.
- Admin transaction history must explain state changes consistently regardless
  of whether evidence came from polling or an inbound protocol message.
- Event-driven tracking must have explicit idempotency and retry semantics.

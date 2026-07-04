# dcb-service Architecture

This is a high-level starting model for developers. Keep implementation details
in focused docs or module notes.

## Main Modules

- `core.model`: persisted domain records and shared value objects.
- `storage`: repository interfaces and persistence adapters.
- `request.workflow`: DCB request state machine transitions.
- `request.fulfilment`: request orchestration and host-facing fulfilment work.
- `request.lifecycle`: lifecycle strategy and evidence boundaries.
- `request.lifecycle.placement`: imperative/declarative placement strategy
  selection and placement result projection.
- `request.lifecycle.ncip`: NCIP protocol adapter.
- `tracking`: scheduled polling, host-state change detection, and tracking
  event projection.
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
    -> workflow progression
  ```

- Host LMS support: implement or extend `HostLmsClient` capability slices.
- Protocol support: add protocol adapters below `request.lifecycle.<protocol>`
  and map to lifecycle abstractions.

## Persistence

Persistence is owned by repositories in `storage` and implementations in
`storage.postgres`.

No database schema changes are permitted without explicit approval. This includes
new tables, columns, indexes, migrations, and changes to persisted domain model
shape.

## External Integrations

External system details are isolated in:

- `core.interaction.*` for LMS APIs.
- `request.lifecycle.ncip` for NCIP.
- API/controller packages for inbound service APIs.
- ingest/indexing packages for bibliographic data sources and search systems.

Do not leak protocol DTOs, XML models, or host-specific response objects into
workflow transitions.

## Boundary Rules

- DCB request state changes belong in `Handle...` workflow transitions.
- Incoming notifications update peer evidence, not DCB request state directly.
- Polling and reactive inbound messages should converge at the lifecycle
  evidence boundary before projection, audit, and workflow progression.
- Admin transaction history must explain state changes consistently regardless
  of whether evidence came from polling or an inbound protocol message.
- Event-driven tracking must have explicit idempotency and retry semantics.


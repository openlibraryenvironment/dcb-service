# Managed Shared-Index Lifecycle

## Status

Tactical priority. Not scheduled.

## Context

The Elasticsearch/OpenSearch shared index is derived from DCB's PostgreSQL data,
but its lifecycle is only partly managed by the application today. At startup,
DCB creates the configured physical index when it is missing and otherwise sends
the current mapping as an in-place update. Population is separate: an operator
can call `POST /admin/reindex/START`, while the scheduled reconciliation job is
also capable of repopulating the index outside office hours.

This breaks down when a mapping cannot be changed in place. The September 2026
`members.availability` incident demonstrated both sides of the problem:

- long-lived indexes had dynamically inferred `text` fields with `keyword`
  multifields;
- indexes created during the incompatible release window explicitly held plain
  `keyword` fields;
- neither mapping can be updated in place to match the other;
- startup mapping failure prevents the whole DCB application from becoming
  available.

Deleting and recreating the current index is a reasonable recovery for an
individual deployment with an accepted discovery outage. It is not a product
upgrade strategy: it has no automatic population, readiness or rollback
contract, and a new Kubernetes pod must not delete an index still serving old
pods.

DCB must support both common deployment models:

1. EBSCO-style stop/start, where an operator stops the old application and then
   manually starts the new one.
2. Kubernetes rolling deployment, where old instances remain ready while new
   instances start and must not report ready until their required index is
   usable.

## Goal

Make shared-index schema upgrades and missing-index recovery an application-
managed process. Operators configure one stable logical index name; DCB manages
immutable physical generations, population, validation and alias promotion
without requiring an operator to select an index version or issue routine
Elasticsearch/OpenSearch administration commands.

The intended lifecycle is:

```text
stable alias -> active physical generation
                       ^
                       |
new DCB -> create target -> rebuild from PostgreSQL -> catch up -> validate
                                                               -> atomic alias swap
                                                               -> ready
```

## Desired Behaviour

### Unchanged schema

- DCB identifies the schema expected by the running application using an
  explicit schema version and mapping/settings fingerprint.
- When the alias points to a compatible physical index, startup is fast and no
  rebuild occurs.
- Startup does not blindly submit an incompatible mapping update to the active
  index.

### Schema upgrade

- One new instance acquires a distributed migration lease. Other instances
  observe progress and do not start competing builds.
- The coordinator creates a new physical index without changing the live alias.
- It builds documents from PostgreSQL into that physical index directly. The
  existing live alias remains available throughout the initial build.
- The build has its own resumable checkpoint. It must not rely on the single
  `ClusterRecord.lastIndexed` value, which describes the active index and cannot
  independently describe an index under construction.
- Changes made during the initial build are caught up before promotion. The
  consistency model for the final cutover window, including updates and soft
  deletes, is explicit and tested.
- DCB validates the target mapping, settings, document population and required
  discovery fields before promotion.
- Alias removal from the old generation and addition to the new write generation
  occur in one atomic Elasticsearch/OpenSearch aliases operation.
- The previous physical generation is retained for a configured period or until
  explicitly retired. Automatic cleanup must never remove the only viable
  rollback generation.

### Missing active index

- DCB distinguishes a confirmed missing index from an unreachable or unhealthy
  search cluster.
- A confirmed missing index starts the same create, rebuild, validate and promote
  workflow.
- A transient backend failure must not trigger destructive replacement.
- Recovery is idempotent and resumes safely after application or coordinator
  restart.

### Readiness and liveness

- Migration work starts while the new process is alive but not ready.
- Liveness remains healthy while a progressing migration is incomplete;
  readiness reports the shared index as unavailable for that application
  generation.
- Readiness becomes green only when the stable alias selects a validated index
  compatible with the application.
- Failure state and progress are observable without exposing cluster credentials.

## Deployment Journeys

### Stop/start

1. The old DCB process is stopped.
2. The new DCB process starts and identifies whether a new physical generation is
   required.
3. It creates, populates, validates and promotes the generation automatically.
4. It reports ready only after promotion. The operator does not manipulate the
   index or invoke a separate reindex endpoint.

The previous index may continue serving independent discovery consumers during
the build because its alias remains unchanged until promotion.

### Kubernetes rolling deployment

1. Old pods remain ready against the active generation.
2. New pods start but remain unready when their required generation is absent.
3. One new pod builds the target under the distributed lease; the others wait.
4. The coordinator atomically promotes the validated target.
5. New pods become ready, allowing Kubernetes to remove old pods normally.

A schema generation eligible for rolling deployment must accept documents
emitted by the immediately preceding DCB version. If document serialization is
not backward-write-compatible, the release must declare that it requires a
stop/start or maintenance deployment unless a deliberate dual-write mechanism
has been designed.

## Implementation Direction

- Introduce a shared-index lifecycle coordinator above the Elasticsearch and
  OpenSearch implementations. Engine adapters continue to own engine-specific
  requests; the coordinator owns generation state and promotion policy.
- Separate the stable logical alias from physical generation names. Physical
  naming and schema versions are internal implementation details rather than
  deployment configuration normally chosen by operators.
- Add direct-to-physical-index bulk population so a target can be built without
  disturbing writes to the active alias.
- Reuse the existing indexing document conversion and bulk protections rather
  than introduce a second document model.
- Use a durable, resumable build checkpoint and a distributed lease. Reuse an
  existing job/checkpoint facility if it can represent independent generations;
  any database schema change requires separate explicit approval.
- Define generation states such as `CREATING`, `BACKFILLING`, `CATCHING_UP`,
  `VALIDATING`, `PROMOTING`, `READY`, and `FAILED`, and expose safe progress and
  failure information through health/operations surfaces.
- Keep an explicit administrator-triggered rebuild/retry operation for recovery,
  but make normal deployment automatic.
- Treat index retirement as a separate, conservative operation with retention
  and rollback checks.

## Guardrails

- PostgreSQL remains the authoritative source. Elasticsearch `_reindex` alone is
  insufficient when a DCB release adds or changes derived document fields.
- Never delete or modify the active physical index while another application
  generation may still be serving through it.
- Never move the live alias before the target passes validation.
- A failed build leaves the old alias and old application availability intact
  during a rolling deployment.
- Do not treat backend unreachability as evidence that an index is missing.
- Do not make full index population a blocking event-loop operation. Startup
  coordination and HTTP control/status entry points must follow the execution-
  boundary rules in `ARCHITECTURE.md`.
- Elasticsearch and OpenSearch must implement the same externally observable
  lifecycle contract.
- Mapping compatibility with supported discovery consumers, including required
  multifields and facets, is part of promotion validation.
- No database schema changes without explicit approval.

## Acceptance Criteria

- A stop/start deployment upgrades to a new index schema without manual search-
  cluster operations or a separate reindex request.
- A Kubernetes rolling deployment keeps old pods ready while new pods build the
  target, then makes new pods ready only after atomic alias promotion.
- Multiple new instances cannot create competing target generations or perform
  competing promotions.
- Killing the migration coordinator during each lifecycle phase demonstrates
  idempotent resume or safe restart.
- A missing active index is recreated and populated automatically when the
  backend is reachable.
- A temporarily unreachable backend does not cause index deletion, replacement
  or alias movement.
- Concurrent cluster additions, updates and soft deletes made during a build are
  reflected in the promoted index according to a documented consistency
  boundary.
- Promotion validation covers schema fingerprint, required settings, document
  population, alias state, and discovery fields consumed outside dcb-service.
- Failure before promotion leaves the old index selected and exposes an
  actionable readiness/operations reason.
- The previous generation remains available for an explicitly documented
  rollback period.
- Integration tests exercise both Elasticsearch and OpenSearch behavior where
  their lifecycle APIs or responses differ.
- Operational documentation covers progress, retry, rollback, retention and the
  exceptional maintenance-only path.

## Out of Scope

- Making Elasticsearch/OpenSearch the source of truth.
- Automatically deleting arbitrary or unowned indexes in a shared search
  cluster.
- Promising zero-downtime rolling upgrades when adjacent DCB versions emit
  incompatible document shapes.
- Replacing the existing incremental indexing pipeline for normal record and
  availability changes.

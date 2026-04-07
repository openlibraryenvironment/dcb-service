# DCB Codebase Context

This document is the fastest way to build a working mental model of the DCB service as a codebase.
It is intended for new developers who need to understand what the system does, where the important
logic lives, and how the main runtime flows fit together.

DCB stands for Direct Consortial Borrowing. At a high level, the service sits between multiple
library systems and coordinates shared borrowing across a consortium. It ingests and clusters
bibliographic data, exposes a shared discovery and availability surface, resolves a borrowing
request to a supply candidate, places requests into external LMS platforms, and then tracks those
requests until completion.

## What The Service Is

The active application lives in the `dcb` Gradle subproject. The root project mostly provides build,
packaging, and documentation wiring. The runtime is a Micronaut application with reactive flows
implemented using Reactor, PostgreSQL for system state, Flyway for schema migration, optional
OpenSearch/Elasticsearch for shared index support, GraphQL for parts of the admin/configuration
surface, and Hazelcast for distributed coordination.

The main entry point is `org.olf.dcb.Application`. Startup does more than boot Micronaut:

- it computes Reactor thread pool sizing from `scaling.factor` and runtime CPU count
- it enables OpenAPI generation
- it prepares the service to run as a long-lived integration node rather than a simple HTTP API

Startup listeners also perform real system initialization. In particular,
`org.olf.dcb.utils.DCBStartupEventListener` bootstraps status codes, persists configured host LMS
definitions, registers the node, and logs operational settings such as polling intervals.

## Core Mental Model

There are four major concerns in this codebase:

1. Request orchestration
2. Bibliographic ingest, clustering, and indexing
3. Host LMS integrations
4. Configuration, admin, and operational support

If you understand those four areas and how they interact, most of the repository becomes navigable.

## The Most Important Domain Objects

The central business entity is `PatronRequest` in `org.olf.dcb.core.model`. This record carries the
state of a consortial borrowing request as it moves through DCB and external systems.

Important related concepts:

- `PatronRequest.Status`: DCB’s internal lifecycle state machine
- `SupplierRequest`: the request DCB places at a supplying library
- `Patron` and `PatronIdentity`: the requesting user and their local identity data
- `BibRecord`: a bibliographic record ingested from a source system
- `ClusterRecord`: the shared cluster that groups related bibs across systems
- `DataHostLms`, `Agency`, `Library`, `Location`: the configuration model that maps consortium
  structure onto external systems

The main thing to remember is that DCB is not only storing local metadata. It is also storing its
own reconciliation state for work happening across several external LMS platforms.

## Request Lifecycle

The request flow starts at `PatronRequestController` under `/patrons/requests`. The code in
`docs/DCB_workflow.md` is still useful background, but the code has moved on from that earlier
summary and should be treated as the source of truth.

The current happy-path shape is:

1. A client submits a place-request command.
2. Preflight checks validate patron, pickup, duplicate-request, and global-limit constraints.
3. DCB upserts or finds patron data and creates a `PatronRequest`.
4. The request is resolved to an item/supplying agency candidate.
5. Workflow transitions place requests into the appropriate external systems.
6. Tracking and polling continue to advance the request until it is completed or finalised.

The main request code is split across three cooperating packages:

- `org.olf.dcb.request.fulfilment`
  Contains request creation, patron handling, and preflight checks.
- `org.olf.dcb.request.resolution`
  Contains supply-candidate selection, item filters, tie-breaker strategies, and resolution audit.
- `org.olf.dcb.request.workflow`
  Contains the workflow engine and the concrete state transitions.

### Workflow Engine

`PatronRequestWorkflowService` is the heart of request progression. It loads all
`PatronRequestStateTransition` beans, selects the applicable transition for the current request
context, applies it, audits the attempt, and then recursively continues while more automatic
transitions are available.

This is an important design choice in the codebase:

- workflow behavior is extensible through transition beans
- progression is context-driven rather than hardcoded into a single giant switch
- errors are transformed into `PatronRequest` error state and audited centrally

When debugging request behavior, start with:

- `PatronRequest`
- `PatronRequestWorkflowService`
- the relevant transition in `org.olf.dcb.request.workflow`
- tracking code in `org.olf.dcb.tracking`

### Resolution

Resolution is where DCB decides which item and supplier to use. `PatronRequestResolutionService`
builds `ResolutionParameters`, gets live availability, filters candidate items, sorts them, and
selects a requestable item.

Important resolution concepts:

- live availability is queried at decision time
- resolution uses filter chains, not one monolithic algorithm
- tie breaking is configurable through strategy beans such as availability-date and geo-distance
- manual selection is supported as a separate path

If a request is created successfully but chooses the wrong item or wrong supplier, this package is
usually where the defect lives.

## Ingest, Clustering, And Shared Index

Another large part of the service is the shared bibliographic side.

### Ingest

`IngestService` is a scheduled process that pulls records from configured ingest sources, converts
them into internal ingest records, persists bib data, and then passes bibs into clustering.

Important characteristics:

- ingest is scheduled and long-running, not request/response
- providers can expose multiple ingest sources
- ingest uses concurrency groups and federated locking
- ingest can be transformed through publisher hook chains

### Clustering

Clustering groups bibs into shared cluster records so DCB can reason about a title or work across
multiple institutions. The default clustering implementation can be replaced by the feature-flagged
`ImprovedRecordClusteringService`, which is the more sophisticated implementation currently present
in the codebase.

Clustering is important because request resolution depends on cluster-level views of available items,
not only on isolated source bibs.

### Shared Index

The service can maintain a shared search index through `SharedIndexService` implementations for
Elasticsearch and OpenSearch. `SharedIndexLiveUpdater` initializes the index at startup and can
trigger reindex work through admin endpoints.

In practice, this means the discovery side of DCB is tightly coupled to ingest and clustering:

- ingest updates bib records
- clustering updates cluster records
- indexing publishes cluster state to the search backend
- request resolution reads the shared representation when choosing candidate items

## External LMS Integrations

A large amount of code exists to normalize the behavior of different host LMS platforms behind a
common DCB model.

Important integration areas include:

- `org.olf.dcb.core.interaction.folio`
- `org.olf.dcb.core.interaction.sierra`
- `org.olf.dcb.core.interaction.polaris`
- `org.olf.dcb.core.interaction.alma`
- shared mappers and helper services under `org.olf.dcb.core.interaction.shared`

These integrations cover things like:

- patron validation and lookup
- item retrieval and availability
- request placement
- status polling and updates
- mapping native LMS codes onto DCB canonical states

This is one of the trickier parts of the service because business behavior emerges from both DCB’s
workflow and the capabilities or quirks of the target LMS.

## API Surface

DCB exposes several different interfaces.

### REST APIs

Controllers under `org.olf.dcb.core.api` expose the main REST surface. Key areas include:

- `/patrons/requests`: create and inspect patron requests
- `/patrons/requests/resolution`: preview or inspect request resolution
- `/suppliers/requests`: supplier-side request operations
- `/clusters`, `/bibs`, `/sourceRecords`: shared bibliographic data
- `/agencies`, `/locations`, `/hostlmss`, `/symbols`: consortium configuration and lookup
- `/admin`: operational and maintenance endpoints
- `/export`: configuration export/import related endpoints

OpenAPI docs are generated from the application and served from `/openapi/**`.

### GraphQL

GraphQL is enabled and backed by `schema.graphqls` plus data fetchers in `org.olf.dcb.graphql`.
This area is used heavily for configuration and management workflows.

### Scheduled And Background Processing

Not all important behavior is behind HTTP endpoints. Significant work happens through:

- scheduled ingest
- tracking/polling
- housekeeping and reprocessing
- startup-time initialization

For new developers, this is a key point: many production issues are caused by asynchronous
background workflows rather than direct controller logic.

## Configuration And Runtime Dependencies

The main configuration files are:

- `dcb/src/main/resources/application.yml`
- `dcb/src/main/resources/application-development.yml`
- `dcb/src/main/resources/bootstrap.yml`

Operationally important dependencies:

- PostgreSQL
- JWT validation via Keycloak JWKS
- optional Elasticsearch/OpenSearch
- optional secret-manager integration through Micronaut bootstrap config
- Hazelcast for clustered coordination

Some settings that shape system behavior are especially important:

- polling durations per `PatronRequest.Status`
- request workflow delay
- duplicate-request window
- live availability timeout
- item resolver strategy
- active request limit

The `README.md` covers deployment-oriented configuration, while `application.yml` shows the runtime
defaults more accurately.

## Persistence Model

Persistence is mostly organized through repository interfaces under `org.olf.dcb.storage` with
PostgreSQL-specific implementations under `org.olf.dcb.storage.postgres`. Schema evolution lives in
Flyway scripts under `dcb/src/main/resources/db/migration`.

This codebase stores both:

- canonical domain/configuration data such as agencies, host LMS records, mappings, and cluster data
- operational state such as patron requests, supplier requests, audits, alarms, checkpoints, and
  process state

That split matters when reading migrations. Some schema changes support user-visible business
features, while others exist only to support background processing or observability.

## Operational Support Code

The service contains a lot of code that exists to keep a distributed integration service running:

- alarms and notifications
- process audits and event logs
- named SQL queries for support investigations
- housekeeping, reprocessing, and validation tools
- stats and health indicators
- scripts for local/dev/test/admin workflows

The `scripts/` directory is worth browsing early. It reflects the kinds of operational tasks that
matter in real environments and often reveals how the service is expected to be used.

## Testing Shape

The test suite is large and covers the major functional layers:

- API tests
- workflow and fulfilment tests
- resolution tests
- integration tests for Sierra, FOLIO, Polaris, and Alma behavior
- storage and service tests
- ingest and clustering related tests

For a first pass through the code, test classes are often the best executable documentation. Good
starting points include:

- `PatronRequestWorkflowTests`
- `PatronRequestResolutionServiceTests`
- `StandardWorkflowPatronRequestApiTests`
- `PickupAnywhereWorkflowPatronRequestApiTests`
- `SameLibraryWorkflowApiTests`

Before changing test execution or full-suite validation behavior, read the ADRs under `docs/ADRs/`.

## Suggested Reading Order For New Developers

If you are new to DCB, this order usually works well:

1. `README.md` for deployment and top-level configuration expectations
2. this file for the system map
3. `docs/DCB_workflow.md` for the historical request-flow summary
4. `PatronRequest`, `PatronRequestController`, and `PatronRequestWorkflowService`
5. `PatronRequestResolutionService` and related resolution filters/sort strategies
6. one host LMS integration package that is relevant to your current work
7. `IngestService`, clustering services, and shared index services
8. the relevant tests for the area you are changing

## What Usually Matters Most In Practice

For day-to-day development, most changes end up touching one or more of these seams:

- request state transitions
- LMS status mapping and polling behavior
- candidate-item filtering and tie breaking
- configuration data import/export
- clustering and index consistency
- operational tooling for reprocess, validate, or inspect

If you are unsure where to start debugging, begin by identifying which of those seams the issue
belongs to. That usually narrows the search space quickly.

## Related Documents

- `README.md`
- `docs/DCB_workflow.md`
- `docs/Bib_Cleanup.md`
- `docs/sierra_notes.md`
- `docs/ADRs/0001-full-suite-test-timeout-policy.md`

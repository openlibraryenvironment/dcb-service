# The shared index

DCB keeps a search index of every cluster record it holds, built from records harvested from the libraries in the consortium.
We refer to this as the "shared index", or sometimes the "union catalogue".

This document covers how records reach it, what shape they arrive in, how it
behaves at startup and under load, and what to do when a mapping change goes
wrong. That last part is the one with a gate behind it.
It is being written as part of a push to document DCB Service better, and is likely to change in the future. Consider it a WIP.

## Two engines, one interface

`SharedIndexService` in `org.olf.dcb.indexing` has two implementations,
`ElasticsearchSharedIndexService` and `OpenSearchSharedIndexService`, each
activated by the presence of its client bean. Configure `elasticsearch.httpHosts`
or `opensearch.httpHosts` and you get the matching one. This is important functionality: some of our active deployments use ES, others use OS.

Both extend `BulkSharedIndexService`, which owns everything that is not
engine-specific: the queue, the batching, the circuit breaker and the write-back of
`lastIndexed`. The engine-specific subclasses handle index creation, mapping
updates, the bulk call and the refresh interval.

The two clients are not interchangeable at the wire level. An Elasticsearch 9 java
client cannot talk to an 8.x server at all, and Micronaut 5 pins us to that client.
`/info` reports `dcb.index.backend.distribution` and `dcb.index.backend.version`,
recorded at startup, so a deployment's engine and version can be established
without holding credentials for the cluster.

Credentials come from `dcb.index.username` and `dcb.index.password` through
`SharedIndexHttpAsyncClientBuilderFactory`.

## One alias, one versioned index

`dcb.index.name` is an **alias**. The real index is `<name>-<version>`, where the
version is `SharedIndexConfiguration.LATEST_INDEX_VERSION`, currently 2, unless
`dcb.index.version` overrides it.

`createIndex` creates the versioned index and attaches the alias in the same
request, with `is_write_index` set. Nothing moves the alias afterwards. So the
alias is what everything reads and writes through, and the versioned name is what
mapping and settings requests must use, because those belong to an index rather
than to an alias.

The version exists so that a mapping that cannot be amended can be replaced
instead: a new `mappings-3.json`, `settings-3.json` and `LATEST_INDEX_VERSION`
builds a fresh index. That path has a sharp edge. An alias may have at most one
write index, and `createIndex` claims it unconditionally, so bumping the version
against a cluster whose previous index still holds the alias as its write index
fails at create. Detach it first.

## What a document looks like

One document per cluster record, keyed by the cluster id.
`ClusterRecordIndexDoc` is the top level and `NestedBibIndexDoc` is each member
bib. `SharedIndexConverters` builds it, resolving Host LMS ids to codes and
loading the availability counts for that cluster.

Three things are worth knowing about the shape.

**Cluster-level fields come from the selected bib.** Title, author, publisher,
place and date of publication, ISBN and ISSN are all read from whichever bib the
cluster has selected, not merged across members. Change the selected bib and those
fields change.

**`members` carries one entry per bib in the cluster**, with its source system
code, source record id, title, and whether it is the selected one. This is what
lets discovery show a single work while knowing which libraries hold it.

**`members[].availability` is a holding summary, not live availability.** It comes
from `BibAvailabilityCount` rows in Postgres, written by `AvailabilityCheckJob`
and by live lookups that happen during user interaction. `library` is the internal
location code, `location` is the remote one, `combined` is the two joined by a
dot, and `count` is how many items. Live availability for a request goes through
`LiveAvailabilityService` and fans out to the member systems; it does not read
this.

`selectedBib` is present in the source but explicitly not indexed, via a dynamic
template that disables it.

## How a record reaches the index

Three triggers, all of which funnel into the same queue.

**A cluster record is saved.** `DefaultRecordClusteringService` calls `add`,
`update` or `delete` on committal of the transaction that wrote the cluster, so
ingest and reclustering both feed the index without an extra scheduled pass. A
cluster marked deleted becomes a delete operation rather than an update.

**Availability changes.** `AvailabilityCheckJob` calls `update` for each affected
cluster after it writes new counts, and the single-bib path used by live lookups
calls `add` for the cluster the bib contributes to.

**The nightly reconciliation job.** `IndexSynch` runs on a 24-hour fixed delay,
20 seconds after startup, under a federated lock so only one instance runs it, and
only outside office hours. It pages through cluster records whose `lastIndexed`
predates the run's cutoff, 1000 at a time, and pushes them through the same
pipeline. 

## The pipeline between the trigger and the engine

`BulkSharedIndexService.initializeQueue` builds one long-lived Reactor stream on a
dedicated `index-scheduler` thread. Everything above pushes cluster ids into its
sink; nothing writes to the engine directly.

The stream then does four things.

**Batches and deduplicates.** Ids are windowed by count and time, deduplicated,
and buffered into chunks of `dcb.index.max-resource-list-size`, default 1500. A
cluster touched repeatedly in one window is indexed once. `dcb.index.min-update-frequency`,
default 5 seconds, sets the window.

**Loads each chunk from the database and converts it.** `manifestCluster` reads the
clusters with their bibs in a fresh read-only transaction and turns each into an
index operation.

**Sends the chunk as one bulk request**, never as individual writes, and never
with a forced refresh.

**Writes `lastIndexed` back to the cluster record** for everything that succeeded.
That column is what the nightly job pages on, so a chunk that fails is simply
picked up again rather than being tracked separately.

Two protections wrap that.

A **circuit breaker** on the call into the engine: three attempts, backing off to
five seconds, resetting after two minutes. When it opens the pipeline drops
operations rather than queueing them without bound, logs once, and lets the
nightly job recover the backlog. An unavailable search cluster must not become an
unavailable DCB.

A **rate threshold**. When sustained throughput exceeds roughly one item per
second, `rateThresholdOpenHook` fires and the engine implementations set the
index's `refresh_interval` to `-1`; when it subsides, `rateThresholdClosedHook`
restores it to 30 seconds. This makes a bulk load much cheaper at the cost of
newly indexed records not appearing in search until the interval returns.

**If you find `refresh_interval` at `-1` on an idle system, a load was
interrupted between those two hooks.** Nothing will appear in search until
something sets it back, and a restart does.

## Startup

`SharedIndexLiveUpdater` listens for `StartupEvent` and blocks on
`SharedIndexService.initialize`. That method checks whether the versioned index
exists. If it does not, it creates it from `settings-2.json` and
`mappings-2.json` with the alias attached. If it does, it sends `PUT _mapping`
with the current mappings. Either way it then reconciles
`dcb.index.number-of-replicas` and restores the refresh interval.

**The mapping update is not tolerant, and the failure is not contained.**
`onApplicationEvent` lets the error escape, so a mapping the cluster refuses does
not degrade discovery: it stops dcb-service, and fulfilment, tracking and patron
authentication go down with the search index. The rest of this document is about
avoiding that.

## Deletions

Two kinds, and they work differently.

A cluster marked deleted produces a delete operation through the normal pipeline,
by cluster id.

Documents that no longer correspond to any cluster are removed by
`deleteDocsIndexedBefore`, which the nightly job fires 30 seconds after its last
chunk. It deletes anything whose `lastIndexed` predates the run's cutoff, or which
has no `lastIndexed` at all. This is why `lastIndexed` must be written for every
successfully indexed document, and why it is the wrong field to use for anything
that should survive a reindex.

## Who reads it

Three consumers, all addressing the alias, all needing to agree on its name.

- `dcb-locate` searches it through `elasticsearch.indexes.instances`, and builds
  CQL queries and facets against specific fields. `Facet.locationsFacet` and the
  `items.effectiveLocationId` filter both target
  `members.availability.library.keyword`. Any other discovery service backend may decide to do the same thing.
- Nothing in dcb-service itself searches it. The only other code that holds a
  client is `OpenSearchClientHealthIndicator`, which reports cluster reachability
  on the health endpoint.

A field-level change to the mapping is therefore a cross-repo change. It's important to consider the effects on other apps when making such a change.

---

# Changing the mapping

`sharedIndex/mappings-2.json` is not a description of the index. It is a command
sent to every deployment's cluster at every startup, and the cluster is allowed to
refuse it.

Neither Elasticsearch nor OpenSearch changes a field's type, analyzer or
normalizer in place. A refusal is therefore permanent for that index: the only way
forward is to rebuild it. That is why this is checked at build time rather than
discovered on deployment.

## The gate

This change introduces a gate with the intention of reducing the amount of index-related issues that make it into main.
It was introduced because a previous change broke indexing and broke main, but wasn't caught by the tests. 
`OpenSearchSharedIndexIntegrationTests.shouldMergeTheCurrentMappingIntoAnIndexBuiltByTheLastRelease`
rehearses the deployment against a real engine:

1. Build an index from `dcb/src/test/resources/sharedIndex/mappings-2.last-released.json`,
   a copy of the mapping as shipped in a named release.
2. Index the document, so that any field the released mapping left undeclared is
   typed by dynamic mapping exactly as it was in every environment.
3. Apply the current mapping and require the engine to accept it.

Step 2 is what makes it a real check. Without a document the index has nothing to
conflict with and any mapping would be accepted.

Two classes of change fail it, which are the two ways this has gone wrong.
Retyping a field the released mapping declared, because the index was built with
that declaration. And declaring a field the document already emits with a type
other than the one dynamic mapping inferred, because the engine typed it from the
first document it saw. There is no table here of what the engine is believed to
do; it is asked.

It runs against OpenSearch 2.19.1. Development
environments run Elasticsearch, but the merge rules the check depends on are the
same in both.

## The two incidents these come from

**`members.availability.library`, September 2026.** `ab5c1f1fb` started emitting
`library` and `combined` in May 2025. `ef48eb823` deleted the whole `availability`
block from the mapping that June, leaving all four fields undeclared. `774540162`
declared them as `keyword` fifteen months later. Every index held `text`, so
dcb-service crash-looped on `intcon-dev` for six days, and the same failure was
waiting for MOBIUS Production, MOBIUS Staging and EBSCO Integration on the next
release.

That change also declared `library` as a plain `keyword`, which would have deleted
the `members.availability.library.keyword` subfield `dcb-locate` aggregates on.
That half would have broken a working facet even on a brand new index.

**The analyzers, February 2026.** `e11d469d5` moved `icu_folding_nopunc` off
`metadata.bibNotes`, `metadata.contents` and `metadata.series` and onto
`primaryAuthor`, `publisher` and `metadata.subjects.label`. Six fields. The
environments that had already built an index were recovered in March by setting a
new `DCB_INDEX_NAME` so a clean index was built from source.

## When the test fails

The engine has told you the change cannot be deployed to an existing index, and
the exception names the field. That is a fact about the indices, not about the
test, so do not edit the baseline to make it pass.

Either declare the field the way the engine already types it, which is almost
always right, or bump the index version. Note that the engine reports one
conflict at a time, in an order you cannot predict, so a second field in the same
state appears only after the first is resolved.

## Refreshing the baseline

Replace `mappings-2.last-released.json` with the mapping from the release, and
update `BASELINE_RELEASE` in the test to match:

```bash
git show v<release>:dcb/src/main/resources/sharedIndex/mappings-2.json \
  > dcb/src/test/resources/sharedIndex/mappings-2.last-released.json
```

Letting it age makes the check stricter rather than weaker, because long-lived
indices were built by older releases still. Refresh it when an older baseline has
become pointless, never to clear a failure.

## Rebuilding an environment

Two ways, and they are not interchangeable.

**Rename the index.** Setting a new `DCB_INDEX_NAME` makes dcb-service create a
clean index from the source mappings at startup, alias included, and the nightly
job or `POST /admin/reindex` fills it. This is what recovered staging and dcb-int
in March 2026. It is simple and it leaves the old index untouched as a fallback,
but discovery is empty against the new name until the rebuild finishes.

Note what it does not do: a clean index still gets dynamic types for any field the
mapping leaves undeclared. Renaming standardises everything except the fields
nobody has written down.

**Reindex and swap the alias.** Create the new index with the new mapping, run
`_reindex` into it, then move the alias in a single atomic `_aliases` call.
Discovery keeps serving the old index throughout. This is the production
procedure. `_reindex` copies documents between indices and does not re-read
Postgres, so it cannot populate a field the document model gained later; for that
you need dcb-service's own reindex, which rebuilds each document from the cluster
record.

Either way, keep the old index until counts, samples and a discovery check pass.

## Checking a real environment

The build check reasons about one baseline. Only the cluster knows what a given
index actually holds, and indices differ by the release that created them. Before a
release that touches the mapping, developers should send the `05 Shared index` requests in the
`openrs-bruno` collection at each environment:

- `01 Inspect / Field types` shows the types an index really holds.
- `02 Check a mapping change / Apply the mapping to the live index` sends exactly
  what the application sends at startup, so a deployment can be proved to start
  without restarting anything.
- `01 Inspect / Aliases` answers the write-index question a version bump depends
  on.

## Configuration reference

| Property | Default | What it does |
| --- | --- | --- |
| `dcb.index.name` | none | The alias. Its presence activates the whole shared-index stack |
| `dcb.index.version` | 2 | Selects `mappings-N.json` and the `<name>-N` index |
| `dcb.index.username` / `.password` | none | Credentials for the cluster |
| `dcb.index.number-of-replicas` | 1 | Reconciled at every startup |
| `dcb.index.max-resource-list-size` | 1500 | Documents per bulk request |
| `dcb.index.min-update-frequency` | 5s | Batching window |
| `elasticsearch.httpHosts` / `opensearch.httpHosts` | none | Which engine, and where |

Absent `dcb.index.name`, no bean in the stack is created and dcb-service runs
without an index at all. That is a supported state.


## Contributing

If you extend this document (and please do), or if you spot anything incorrect, please feel free to amend this document. It is intended to be a living document that grows as we document our understanding.
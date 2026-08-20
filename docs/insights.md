# Insights

Reporting and analytics for DCB: 36 read-only endpoints under `/insights/**`, served by
`InsightsController`, that answer questions about request flow, supply reliability, demand and
collections, scoped to the library the caller actually belongs to.

They lived on `PatronRequestController` at `/patrons/requests/stats/**` until that reached 35
Insights endpoints against 10 of its own. The two have nothing in common at runtime — Insights
never mutates, never touches the state machine and never talks to a Host LMS — and the split
gives the generated OpenAPI a tag of its own, which is how anyone is meant to find these. The
old prefix was accurate for ten endpoints hanging off patron requests and misleading for
thirty-five: half of these never read `patron_request` at all, and the collection queries
describe the catalogue before a single request exists.

### The two paths that did not move

`/patrons/requests/stats/top-requestors` and `/patrons/requests/stats/top-requested-titles`
are still served, by `LegacyStatsController`, because **dcb-admin-for-libraries calls them from
its main branch today**. Renaming without them would break a deployed frontend the moment
dcb-service released — the same failure that took dcb-admin-ui's consortium page down for
several hours when the branding schema changed without a paired release.

They delegate to `InsightsController` rather than re-querying, so the scoping guard cannot be
bypassed by coming in through the old door, and they log at WARN so there is evidence about
whether anything still calls them. **Delete the class once a dcb-admin-for-libraries release
using `/insights` is out.**

The other 33 get no alias: they have never been deployed, so nothing can be calling them.
Verified by OpenAPI path diff — 45 routes before, 47 after (35 moved, 2 aliased, 10 patron
request routes untouched), and exactly those 33 old paths gone.

Insights is consumed by **dcb-admin-for-libraries** (a librarian's view of their own library)
and by **dcb-admin-ui** (a consortium administrator's view of everyone). The Audit Explorer's
incidence chart is a separate feature on its own branch and is documented separately; it
shares only `TimeBucket` with Insights.

---

## Part 1 — What Insights is for

DCB knows a great deal that nobody could previously ask it. Every patron request carries its
borrower, its supplier, its pickup location, its cluster, its patron group and a full audit
trail of every state transition. Before Insights that was queryable only by someone with
psql access and a willingness to write the join.

Insights turns that into a question-shaped API. The questions it exists to answer are the
ones a library director asks at a consortium meeting and a systems librarian asks at 4pm on a
Friday:

- Are we lending more than we borrow, and to whom?
- Which of our suppliers actually deliver, and how long do they take?
- Where are requests dying, and is it us or them?
- What do our patrons want that we do not hold?
- How do we compare to our peers?
- What do we hold that nobody else does?

### 1.1 The surface

Every endpoint is `@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})` and takes an
optional `startDate` / `endDate` window.

| Group | Endpoints |
|---|---|
| **Request flow** | `dashboard`, `dashboard-metrics`, `timeseries`, `turnaround`, `time-in-status`, `net-flow`, `fulfillment/borrower`, `fulfillment/supplier`, `saved-by-re-resolution` |
| **Supply reliability** | `supplier-reliability`, `supplier-response-sla`, `failure-taxonomy`, `unfillable-demand` |
| **Demand** | `top-requestors`, `top-requested-titles`, `demand-heatmap`, `demand-by-format`, `demand-by-dimension`, `demand-by-patron-group`, `demand-by-pickup-location` (± code), `checkout-rate` |
| **Collections (from requests)** | `collection-summary`, `collection-balance`, `unique-contributions`, `acquisition-opportunities`, `new-acquisitions-performance`, `unmet-local-demand`, `consortial-lifeline` |
| **Collections (from the catalogue)** | `collection-totals`, `collection-profile`, `cluster-size-distribution`, `format-profile`, `collection-overlap` |
| **Comparison** | `peer-benchmarks` |
| **Partners** | `top-partners` (both directions, paged, ranked on the total — see §3.2a) |

29 serde records carry the responses. All are `@Serdeable @Introspected` Java records — no
`Map<String, Object>` payloads, so the contract is in the type system and a consumer's
codegen sees it.

---

## Part 2 — The security decision, which came first

### 2.1 The hole

Every one of these endpoints took `libraryCode` as a **query parameter that nothing checked**.
A `LIBRARY_ADMIN` at library A read library B's turnaround, failure taxonomy, patron-group
demand and unique holdings by editing one parameter in the URL bar. This is the exact failure
the doctrine names: *identity comes from verified claims, never from client input*.

It is worst in dcb-admin-for-libraries, whose entire premise is that a librarian sees their
own library and nothing else.

### 2.2 The guard

`StatsScopeGuard.resolve(authentication, requestedLibraryCode)` sits in front of every
endpoint and returns the filter to apply:

- **Consortium-level callers** (`CONSORTIUM_ADMIN`, `ADMINISTRATOR`) keep whatever they asked
  for, including nothing — a consortium-wide view is their job.
- **Library-level callers** always get their **own** library, derived from the token, whatever
  they asked for. If they asked for one of their own, that is honoured, so somebody who
  administers three libraries can still look at one at a time.

### 2.3 Choice: read `code`, not a new claim

The first draft read a bespoke `dcb_agency_code` claim. That was wrong twice over: it does not
exist in any realm, so the feature could not ship without a Keycloak change; and it created a
**second source of truth** for which library somebody belongs to, alongside the `code` claim
the GraphQL fetchers already read in production.

Insights reads `code`, through a single `AgencyClaims` class shared with
`GraphQLSecurityContextCustomizer`. The same token cannot now scope one way in GraphQL and
another way in REST, because there is one implementation. No realm change is needed.

### 2.4 Choice: multi-valued claims are supported

A person can administer more than one library. Whoever runs a shared Koha on behalf of
several of its tenants is not a consortium administrator and must not be given consortium-wide
access — but neither do they belong to exactly one agency.

`AgencyClaims` therefore accepts a string **or** a list, and every one of the underlying
queries filters with `= ANY(string_to_array(:libraryCode, ','))` rather than `=`. The identity
provider decides which shape to issue; `agencyCodes` is accepted alongside `code` for providers
that cannot make an existing scalar claim multi-valued.

### 2.5 Choice: refuse, do not narrow

An earlier draft had a `WARN` / `ENFORCE` mode so that a rollout could log violations before
blocking them. That switch was removed, and its removal is the more interesting decision.

It existed only because the draft read a claim no realm issued, so there had to be a phase in
which nobody was locked out. Reading `code` removes the phase. What remained was a switch over
a single case — a caller asking for a library that is not theirs — and that case cannot use
one: **dcb-admin-for-libraries has no library picker.** It derives the code it sends from the
same claim the guard reads. A mismatch is therefore a client bug or somebody trying it on,
never configuration lag.

Narrowing silently would also have been worse than refusing: the page would render one
library's figures under another library's name, and nothing would say so. Both failure modes —
no agency in the token, and an agency that is not the one requested — now return 403.

The refusal is logged with the reason, because a refusal nobody can attribute to a caller is a
refusal nobody can investigate. The response says only that the account is not associated with
a library; which library it *is* associated with is not the caller's business to have
confirmed.

### 2.6 The gate

`StatsScopeArchitectureTests` reads the compiled controller and fails if any of these stop
being true:

| Test | Catches |
|---|---|
| `everyStatsEndpointTakesTheAuthenticationItMustScopeOn` | A new endpoint that forgot to accept `Authentication` |
| `everyStatsEndpointRoutesItsLibraryFilterThroughTheGuard` | A new endpoint that queries without calling the guard |
| `noStatsEndpointStillBindsTheRawLibraryCodeParameter` | A regression back to the trusted parameter |
| `theSurfaceIsNotEmpty` | The test silently matching nothing and passing vacuously |

The last one matters as much as the other three. A structural test whose selector stops
matching goes green forever and nobody notices — so it now asserts a floor of 30 endpoints
rather than merely "not empty", and a floor rather than an exact count so that adding an
endpoint does not train everyone to bump a number without reading why.

Since the controller split, the guard scans **every** `@Get` in `InsightsController` rather than
only paths beginning `/stats/`. The class boundary is now the surface definition, which makes it
stricter: an endpoint added there under any path is in scope, where the old marker would have
missed it.

---

## Part 3 — Design choices in the queries

### 3.1 Peer benchmarking names the peers

`peer-benchmarks` is a league table a librarian reads to place themselves. A Host LMS code
means nothing to most of them, so `PeerBenchmarkStat` carries `libraryName` alongside
`libraryCode`.

The name is resolved **in the query**, not joined in the client, because the figures are
grouped by Host LMS and only the service knows which libraries sit on one. It uses
`string_agg(DISTINCT …)` rather than picking the first, because a shared Host LMS genuinely
serves several libraries and naming one of them would be a lie. The subquery is correlated on
the grouping column, so it runs once per library over three small configuration tables.

`libraryName` is **nullable**: a Host LMS can have ingested requests without being onboarded
as a library. `StatsQueriesTests` caught that null before it shipped, which is why the record
component is annotated rather than assumed.

### 3.2 Peer benchmarking is not pseudonymised

An earlier revision hashed peer identity. It did not work — the label was
`"peer-" + Integer.toHexString(code.hashCode())`: unsalted, deterministic, over a short code
space every member knows. Any peer could re-identify every row by hashing the list.

Withholding peer identity, if a consortium ever wants that, means **hiding the panel** — not
publishing a reversible pseudonym. A pseudonym that can be reversed is worse than no
pseudonym, because it advertises a protection that is not there.

It was also inconsistent: `dashboard-metrics` already returns `topSuppliers` and `topBorrowers`
to the same caller, named, and those are the partners they actually trade with. And the figures
are close to useless without names — "you are below the median" is not something a librarian can
act on without knowing who is above it and who to ask.

If peer identity is ever withheld properly, it must be an HMAC under a secret with no default.

### 3.2a Trading partners

Three views, all windowed:

| Where | Question | Bound |
|---|---|---|
| `dashboard-metrics.topSuppliers` | Who do we borrow from most? | fixed top 10 |
| `dashboard-metrics.topBorrowers` | Who borrows from us most? | fixed top 10 |
| `/insights/top-partners` | Who do we trade with most, either direction? | **paged**, 10 per page by default |

The two on `dashboard-metrics` stay fixed because that endpoint's whole purpose is one
round-trip for the above-the-fold figures; paging inside a combined payload would defeat it.
`/insights/top-partners` is where the full ranking lives.

The combined view exists because **it cannot be derived from the other two**. A partner sitting
sixth in each list can out-total one ranked third in one, and would appear in neither. It keeps
the borrow/supply split alongside the total, because a partner we borrow from constantly and
never supply is a different relationship from an even one and the total alone cannot say which
you are looking at.

`UNION ALL` rather than two aggregates joined: the arms are disjoint by construction, since a
request has one borrower and one supplier, so there is nothing to deduplicate and a `FULL OUTER
JOIN` would only add a way to lose a partner present on one side. Each arm is served by its own
composite index, `pr_stats_borrower_idx` and `pr_stats_supplier_idx`.

Four rules, shared by all three and worth keeping in step:

- **`RET-LOCAL` and `ERROR` are excluded.** A local fulfilment is not a partnership and a failed
  request is not traffic.
- **`libraryCode` is a comma-separated SET**, matched with `= ANY(string_to_array(…))` like every
  other scoped query. This was a live bug: scalar equality matched none of what
  `StatsScopeGuard` hands a multi-library caller, so a shared-Koha administrator got an empty
  partner panel that read as "no activity" — while turnaround, in the same response, worked
  because it already used the set form.
- **A partner is never one of the caller's own codes.** Without that, traffic wholly inside a
  multi-library caller's own group is counted once under each end, inventing a partner out of
  the caller itself.
- **`partnerName` is resolved in the query** (§3.1), nullable for a Host LMS with traffic that
  is not onboarded as a library. Staff read these lists to decide who to talk to, and a Host LMS
  code does not tell them who that is.

`requestedLibraryCode` is mandatory on `/insights/top-partners`, even for a consortium
administrator: "who do *we* trade with" needs a "we".

**Paging.** The query carries neither `ORDER BY` nor `LIMIT` — Micronaut Data appends both from
the `Pageable`, and a literal one would collide with what it appends. The controller supplies
`total_count DESC` when the caller names no sort, so the default is still *top* partners rather
than whatever order the aggregate happens to emit; a caller who wants to rank by one direction
can sort on `borrowed_from_count` or `supplied_to_count` instead.

`totalSize` counts **partners, not requests** — the count query groups the same union the page
does. Six requests across three partners is a `totalSize` of 3, and
`tradingPartnersPageThroughTheWholeRankingRatherThanACappedTop` pins it, because a count query
that forgets to group is the easy mistake here and would report request volume as a page count.

`net-flow` is the related but different question — how much a library borrowed against how much
it supplied, in aggregate rather than per partner. It returns both raw counts and leaves
`suppliedCount - borrowedCount` to the caller, so the UI can show net givers and net takers
without losing the components.

### 3.3 One window contract, in one place

`TimeBucket` owns the bucket widths (`HOUR`, `DAY`, `WEEK`, `MONTH`) and the contract that
goes with them:

1. **`start` inclusive, `end` exclusive.** Half-open intervals tile without double counting.
2. **Capped at `MAX_BUCKETS` (1000), and exceeding it is rejected, never truncated.** A
   silently clipped chart is indistinguishable from a genuine fall in activity.
3. **Gap filled server side.** A bucket with no rows is emitted as zero. Omitting it
   compresses the axis and makes a quiet weekend look like it did not happen.
4. **Unknown bucket names are rejected, not defaulted.** Collapsing an unrecognised value to
   `DAY` renders a plausible but wrong chart forever, and no error ever surfaces to say so.

The Audit Explorer's incidence chart obeys the same enum. Two charts on one dashboard driven
by the same date picker must not differ by a bucket with nobody able to say which is right.

### 3.4 Timestamps are UTC, and `date_trunc` is not given a zone

`audit_date` and `date_created` are `timestamp` **without** time zone holding UTC instants.
`date_trunc` is therefore already UTC and must **not** be given `AT TIME ZONE 'UTC'`, which
would reinterpret the value against the session zone and move every bucket boundary.

### 3.5 Percentiles, not averages

Turnaround reports **p50 and p95, never `AVG`**. Turnaround is heavily skewed and one stuck
request poisons a mean; a library whose typical request completes in two days should not read as
nine because three requests sat in `ERROR` for a month. `COALESCE` so an empty window returns
`(0, 0)` rather than a single `(null, null)` row the client has to special-case.

### 3.6 `StatsScope` is a record, not a `String`

Reactor cannot carry `null` through a `Mono`, and an empty `Mono` is far too easy to read as
"denied" at a call site — which would fail open. Wrapping the filter in a record means "no
narrowing" is a value that arrives, not an absence that has to be interpreted.

### 3.7 The repoint does not touch `date_updated`

Where an Insights query is adjacent to a write (the cluster-merge work), the write deliberately
leaves `date_updated` alone. That column drives the tracking sweeps; bumping it would drag
long-completed requests back into the polling window and generate LMS traffic for requests that
finished months ago.

---

## Part 4 — Scale

The scale constants that matter here are **100,000 patron requests/year** (~1M rows in
`patron_request`, growing), a `patron_request_audit` table several times larger, and **500
member libraries**. The 20M-record bibliographic corpus is *not* touched by the exposed
endpoints — see §5.2 for the queries that would touch it.

### 4.1 Indexes

`V9_0_005__analytics_indexes.sql`:

| Index | Serves |
|---|---|
| `pr_stats_borrower_idx (patron_hostlms_code, status_code, date_created)` | Borrower-side rollups — composite order matches predicate order |
| `pr_stats_supplier_idx (local_item_hostlms_code, status_code, date_created)` | Supplier-side rollups |
| `pr_stats_cluster_idx (bib_cluster_id)` | Collection analysis joins requests to clusters |
| `pr_stats_pickup_idx (pickup_location_code)` | Demand by pickup location |
| `pra_to_status_idx (to_status, patron_request_id)` | The flow time series counts transitions *into* a status; makes the `LOANED` subquery index-only |
| `pra_audit_date_idx (audit_date)` | The flow time series range scan — **and a defect already on `main`**: the Audit Explorer grid's `ORDER BY audit_date DESC LIMIT 50` had no index at all, 245ms → 0.61ms at 5M rows |

Two indexes are deliberately **not** here. `pg_trgm` and the trigram index on
`brief_description` live in the Audit Explorer's own migration, because `CREATE EXTENSION`
needs a privilege the deploy role may not hold, and Flyway fails the whole migration — and
therefore startup — if any statement in it fails. One missing `GRANT` must not be able to take
the service down over a feature it is unrelated to.

`pra_audit_date_idx` is declared **once**, here. `IF NOT EXISTS` matches on **name**, not
definition, so a differently-named copy would be built and maintained alongside it at 107 MB
apiece on a 5M-row table with nothing to notice.
`AnalyticsIndexTests.shouldHaveExactlyOneIndexOnAuditDate` is the guard, and the Audit
Explorer branch carries the mirror-image assertion.

### 4.2 Bounds

- **Row counts are bounded by construction.** Most of the underlying queries carry an explicit
  `LIMIT`; `top-requestors`, `top-requested-titles` and `top-partners` are `Pageable`;
  `peer-benchmarks` returns one row per library, so ≤500.
- **The dashboard fan-out is cached.** An Insights page load hits ~20 endpoints. Resolving the
  caller's agency from the database on each would turn one page view into 20 extra round
  trips, so `StatsScopeGuard` holds a Caffeine cache — **max 2,000 entries, 10-minute TTL**,
  sized to the member-library scale constant with headroom for several consortia in one
  deployment. The key is an agency code: a fixed vocabulary we control, never user input, so
  a caller cannot drive its cardinality.
- **Agency resolution uses `concatMap`, not `flatMap`.** Each cache miss is a database read
  and the bound is the number of agencies one person administers, which is small by
  construction. An unthrottled `flatMap` here would be an unstated concurrency argument
  against the R2DBC pool.

### 4.3 The one unbounded query, stated plainly

`DEFAULT_STATS_WINDOW` is 90 days, and it is applied at **exactly one** call site — the flow
time series. Every other endpoint, `peer-benchmarks` included, is unbounded when the client
omits both dates:

```sql
AND (:startDate IS NULL OR pr.date_created >= :startDate)
AND (:endDate   IS NULL OR pr.date_created <= :endDate)
```

With no dates, `peer-benchmarks` aggregates the entire `patron_request` table and joins a
`SELECT DISTINCT patron_request_id FROM patron_request_audit WHERE to_status = 'LOANED'`.
`pra_to_status_idx` makes that side index-only, but both grow with request volume forever.

The UI always sends a range, so this does not appear in normal use. It appears the first time
somebody calls the endpoint without one. **The bound should be the API's property, not the
client's** — applying `DEFAULT_STATS_WINDOW` at every endpoint that takes dates is the fix, and
it is not yet done.

---

## Part 5 — What Insights can and cannot answer

### 5.1 Answerable today

Everything in the §1.1 table, per library or consortium-wide, over any window. In particular:

- **"What do we hold that nobody else does, that people actually asked us for?"** —
  `unique-contributions` returns the top 50 titles this library supplied where exactly one
  source system in the consortium contributes a bib to that cluster.
- **"What is the network asking for that we do not hold?"** — `acquisition-opportunities`
  returns the top 20 clusters in consortium-wide demand to which this library contributes no
  bib. Deliberately consortium-wide, not this library's own patrons: it is a collection
  development signal, not a traffic report.
- **"How many distinct titles were requested?"** — `collection-summary` gives
  `COUNT(DISTINCT bib_cluster_id)` alongside total volume.

### 5.2 Not answerable today: a list of every unique title in the consortium

This is worth stating precisely, because there are two questions behind it and the answer
differs.

**Reading A — the deduplicated title list of the whole consortium.** That is one row per
non-deleted `cluster_record`. It is already *browsable*: the `instanceClusters` GraphQL query
has been on `main` for some time and is paged. What does not exist, and should not, is an
endpoint that returns all of them in one response — at 20M bibs the clustered set is millions
of rows, which is an **export job, not an HTTP request**. The doctrine's ≤100 rows per UI
interaction applies.

**Reading B — the consortium's deduplicated title count.** `getCollectionTotals()` returns it
directly, as `distinctTitles`, alongside `singlyHeldTitles`, `holdings` and
`contributingSources`.

It is a separate query rather than something the caller derives, because the obvious derivation
is wrong: **summing `cluster_count` across the per-source profile double-counts**. A title held
by three libraries appears in three of those rows, so that sum is `holdings` — a plausible
number that is simply too big, with nothing to say so.

Nor is it `SELECT count(*) FROM cluster_record`. A cluster whose bibs have all gone still exists
as a row until `HouseKeepingService.PURGE_EMPTY_CLUSTERS` marks it deleted, and that sweep runs
only when somebody calls `POST /admin/validateClusters` — **it is not scheduled**. Counting live
contributions is both the more meaningful number and the more stable one.

`holdings / distinctTitles` is the consortium's average duplication factor, which is the single
most useful figure on the panel: how much of the combined catalogue is the same works over
again.

**Reading C — a *list* of the titles only one member holds.** Still counts only.
`getCollectionProfile().unique_title_count` says how many; nothing enumerates them.

**The five catalogue-wide queries are now exposed**, behind the controls in §5.4.

All five share one intermediate — the DISTINCT `(cluster, source system)` pairs — so every
figure on the panel is counted the same way and they reconcile against each other. That
consistency is the reason `getFormatProfile` counts **works, not `bib_record` rows**: a source
cataloguing one work four times would otherwise report four times the format, against a cluster
count of one, on the same screen. It filters `is_deleted` for the same reason.

Unlike everything else in §1.1, these are **full aggregates over `bib_record`** — the 20M row
table. Each single pass is affordable; several at once is not, because the shared intermediate
is up to 20M `(uuid, uuid)` pairs, which exceeds any sane `work_mem` and spills to temp disk,
and the R2DBC pool they run against is the one request tracking uses. That is what §5.4
addresses.

`getClusterSizeDistribution` deserves particular note: it is the **honesty check** on the
other three. If the consortium's clusters are overwhelmingly `holder_count = 1`, the matching
is under-clustering and every unique-title count is fiction. It is exposed alongside the unique
counts, not after them, for that reason.

### 5.3 So: can we count every unique title, and can we list them?

**Count: yes** — `getCollectionTotals().distinctTitles`, once the query is reachable.

**List: not yet.** The change is small and well-understood — the same `cluster_source` CTE,
filtered to `holder_count = 1`, paged, joined back to `cluster_record.title`. What it needs is
the access decision in §5.4, not new SQL.

### 5.4 The access decision, and what was built

These queries cost the same whoever asks, so the question was never *whether* to expose them but
*how often the database is made to answer*.

**Decision: endpoints, on demand, with two controls — not a scheduled rollup.** A rollup buys
predictable read latency and costs a summary table, a migration, a scheduler and a staleness
contract every panel then has to explain. That is real machinery for a screen a handful of
people open occasionally, and we have no timings on a real corpus to justify it. `CollectionAnalysisService` gets most of the benefit for none of it, and is the same shape to
hang a rollup on later if measurement says so.

**Control 1 — one permit for the whole group.** Not one per endpoint: concurrency is a property
of the group, since five different collection queries cost the same as five copies of one. A
caller that cannot get the permit **waits up to 30 seconds** and only then gets `429` with a
message saying the figures are about to be cached. Waiting rather than refusing matters because
a cold panel opening five of these at once is the normal case, and failing four of them is
indistinguishable from a broken page.

**Control 2 — a 15-minute result cache** (Caffeine, bounded at 1,000 entries, keyed on a fixed
vocabulary). These figures move on ingest timescales, so a repeat view, a page refresh and a
second administrator all read the same answer for free. This is what turns the nine-o'clock
pile-up into one query. The cache is re-checked **after** the permit is acquired — without that,
everyone who queued behind a cold computation would run it again on the way in.

**Overlap was reshaped rather than controlled.** The full matrix self-joins the intermediate, so
a work held by *k* sources emits *k(k−1)/2* pairs — quadratic in holders per work, summed over
every work in the consortium. At 500 members a single widely-held title produces over 100,000
rows, and popular titles are precisely the widely-held ones. An outer `LIMIT` cannot save it,
because the pairs are built before they are ordered. `collection-overlap` is therefore **one
library against all others**: linear in the caller's own works, at most one row per peer, and
the question a librarian actually asks. `requestedLibraryCode` is mandatory even for a
consortium administrator, so the matrix cannot return by the side door.

### 5.5 Who may see whose collection

The four consortium-wide endpoints resolve the caller through `StatsScopeGuard` and then
**deliberately discard the scope**. A `LIBRARY_ADMIN` sees every library's collection profile,
including which unique holdings belong to whom.

That is a decision, not an oversight, and it matches the call already taken for peer
benchmarking, where naming peers beat publishing a reversible pseudonym. `resolve()` still earns
its place: a caller holding a library role whose token carries no agency claim is refused rather
than silently served, exactly as everywhere else.

Revisit this first if a consortium ever asks for collection figures to be private between
members. `collection-overlap` is already narrowed to the caller's own libraries and would not
need to change.

---

## Part 6 — Deployment

### 6.0 Turning it off

`dcb.insights.enabled=false` (`DCB_INSIGHTS_ENABLED`) removes the whole surface. The controller
bean is not created, so the routes 404 and there is nothing behind them left to reach — not a
403, not an empty body. It defaults to **on**: unlike discovery, Insights confers no authority
and returns nothing a consortium administrator could not already read, so failing closed would
cost more than it protects.

This exists because Insights is the newest and least-exercised surface in the service, and the
only one that puts aggregates over the two largest tables behind an HTTP request. A consortium
that hits trouble should be able to drop it with a property rather than reverting a release.
`InsightsToggleTests` and `InsightsDisabledTests` assert both halves — the switch is the kind of
thing that goes stale silently, and nothing else in the suite would notice a typo in the
property name.

The three resource limits are tunable for the same reason, and because nobody has yet measured
a cold pass against a real corpus:

| Property | Default | Raise it when |
|---|---|---|
| `dcb.insights.collection-analysis.concurrency` | `1` | Measurement shows a cold pass is cheap enough to overlap |
| `dcb.insights.collection-analysis.cache-ttl` | `15m` | Figures may be staler; lower it while watching an ingest |
| `dcb.insights.collection-analysis.max-wait` | `30s` | A cold pass legitimately takes longer than the queue allows |

Each cold miss logs at INFO with its key and duration — that is the measurement, and the cache
keeps it to a handful of lines an hour rather than one per page view.

### 6.0.1 What a rollback costs

Nothing is irreversible. The migration adds **indexes only**: no column, no row rewritten, no
data transformed. Dropping the six indexes returns the database exactly to where it was, and
the service runs without them — slower on the flow time series and the Audit Explorer grid,
correct throughout. There is no down migration to write because there is no state to unwind.

### 6.1 Migration

One forward-only migration, `V9_0_005__analytics_indexes.sql`, above the current high-water
mark. Index creation only — no column added, no row rewritten.

`CREATE INDEX` takes a `SHARE` lock that blocks writes for the build. That is acceptable
because production is Fargate and deploys inside a maintenance window, so there is no
patron-facing writer to block. `CONCURRENTLY` was implemented, benchmarked and removed: it
protects a writer that is not there, builds ~3.4× slower so it lengthens the very window it
cannot help, cannot run in a transaction, needs a global
`flyway.postgresql.transactional.lock=false` to avoid deadlocking Flyway's own lock connection,
gives up atomic rollback, and on failure leaves an `INVALID` index that `IF NOT EXISTS` then
silently keeps forever.

**If DCB ever moves to rolling deploys under live traffic, revisit that decision first.**

### 6.2 Cross-repo

Insights is a **cross-repo pair** with dcb-admin-for-libraries `feat/library-insights`. The
`libraryName` field on `PeerBenchmarkStat` is consumed there — name first, code as secondary
text, falling back to the code when unnamed. Deploy them together, or the peer panel renders
blank names.

### 6.3 Order

Insights merges ahead of both the Audit Explorer incidence chart (`V9_0_006`) and the cluster
merge lineage work (`V9_0_007` / `V9_0_008`). The lineage branch depends on
`pr_stats_cluster_idx` from `V9_0_005` and deliberately does not create its own.

---

## Part 7 — Where the reasoning lives

The code carries short comments only, and they earn their place by preventing a specific
mistake at the point of editing — "`IF NOT EXISTS` matches on name, not definition", "p50/p95,
NOT `AVG`", "bind UTC local date times", "never restore the full matrix", "the property is on
the CLASS, not the method". Everything longer than that is here.

If you are about to change something in this list, read the section first:

| Code | Section |
|---|---|
| `StatsScopeGuard`, `CallerScope`, `AgencyClaims`, `StatsScope` | Part 2 |
| `TimeBucket`, `PeerBenchmarkStat`, `PartnerStat`, the turnaround percentiles | Part 3 |
| `V9_0_005__analytics_indexes.sql`, `DEFAULT_STATS_WINDOW` | Part 4 |
| `BibRepository` collection queries, `CollectionAnalysisService`, `CollectionTotalsStat` | Part 5 |
| `InsightsController`, `LegacyStatsController`, `dcb.insights.*` | Parts 1 and 6 |

## Part 8 — Evidence

| Claim | Artefact |
|---|---|
| Every endpoint is scoped to the caller's own library | `StatsScopeArchitectureTests`, 4 tests |
| Claim reading is correct for single- and multi-valued tokens, and empty is not "no restriction" | `CallerScopeTests`, 15 tests |
| The queries return what they say | `StatsQueriesTests`, 18 tests |
| The collection analysis queries return what they say | `CollectionAnalysisQueriesTests`, 12 tests |
| Only one catalogue aggregate runs at a time, results are cached, and an exhausted wait budget returns 429 | `CollectionAnalysisServiceTests`, 4 tests |
| The migration applied and the indexes exist | `AnalyticsIndexTests`, 3 tests |
| There is exactly one index on `audit_date` | `AnalyticsIndexTests.shouldHaveExactlyOneIndexOnAuditDate` |
| Route security is explicit | `ApiSecurityArchitectureTests`, 9 tests |
| The surface can be turned off, and is on by default | `InsightsDisabledTests`, `InsightsToggleTests` |
| Splitting the controller changed no URI | OpenAPI path diff, 45 routes before and after |
| `libraryName` tolerates a Host LMS with no library row | `StatsQueriesTests` — it failed on the non-null constructor before the annotation |
| Trading partners are directional, named, exclude local fulfilment, and find a multi-library caller | `StatsQueriesTests.topPartners*`, 4 tests |
| The combined ranking is on the total, keeps the split, excludes the caller's own group, and pages the whole ranking | `StatsQueriesTests.tradingPartners*`, 3 tests |

Full suite not yet run against this branch; the figures above are the branch's own tests.

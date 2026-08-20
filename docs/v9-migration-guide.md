# DCB v9.0.0 — migration guide for hosting providers

For anyone running DCB from the published image on Kubernetes or AWS ECS/Fargate.
Covers everything between **v8.71.0** (23 July 2026) and **v9.0.0**.

Companion documents, all in this repository:

| Topic | Document |
|---|---|
| Micronaut 5 / JDK 25, image bases, Elasticsearch | `docs/micronaut-5-hosting-notes.md` |
| Branding, the asset store, capacity | `docs/branding.md` |
| Discovery services and patron assertions | `docs/discovery-service-approach.md` |
| NCIP peer authentication | `docs/ncip-peer-authentication.md` |
| Insights endpoints | `docs/insights.md` |

---

## The short version

Five things need a decision before you upgrade. Everything else is either automatic
or opt-in.

1. **Database migrations are not free this time.** Seven migrations, one of which
   rewrites every row in `patron_request`, and one of which builds six indexes
   including a ~107 MB index on `patron_request_audit`. They run at startup. Budget
   for it. **[§2](#2-database-migrations--the-headline)**
2. **If improved clustering was not already switched on for you, this release starts a
   full background re-cluster of your entire catalogue, ten seconds after startup, and
   keeps going until it is done.** One SQL query tells you whether that applies.
   **[§3](#3-the-clustering-sweep--check-this-before-you-upgrade)**
3. **This release is not cleanly reversible.** `V9_0_004` drops six columns from
   `consortium`. Rolling back to 8.x will not bring them back. **[§8](#8-rollback)**
4. **`/patrons/requests` is no longer open to any authenticated principal.** It defaulted
   to `IS_AUTHENTICATED` at v8.71.0; every method now demands an explicit role. Any
   client credential calling it needs auditing.
   **[§5](#5-security-changes-that-need-coordination)**
5. **If you run Elasticsearch, you must already be on 9.x.** Unchanged from the
   Micronaut 5 notes, restated here because it is still the most common upgrade
   failure. **[§1](#1-hard-requirements)**

**EBSCO Locate keeps working, and needs nothing done to it.** It uses a service account
with the `ADMIN` role, and both routes it calls are retained with that role deliberately.
[§5](#ebsco-locate--deliberately-unaffected-nothing-to-do) has the detail. The new
`DISCOVERY_SERVICE` model applies to discovery services onboarded from here on, not to
Locate.

Ignore the `PATRON` role in the changelog — it was added and removed inside this release
window and never reached a tag, so there is nothing in your realm to change.
[§5](#about-the-patron-role--it-does-not-affect-you) has the detail.

If you run OpenSearch, are already on improved clustering, and can absorb a
longer-than-usual first startup, this is a pull-the-new-tag release.

---

## 1. Hard requirements

### Elasticsearch must be 9.x

**Only relevant if you run Elasticsearch rather than OpenSearch.** Micronaut 5 brings
`elasticsearch-java` 9.4.0, and Elastic's clients send a major-version compatibility
header. A 9.x client cannot talk to an 8.x server:

```
Accept version must be either version 8 or 7, but found 9
```

surfacing at startup as a confusing 400 from `[es/indices.exists]`. Upgrade the cluster
to 9.x first. It also needs the `analysis-icu` plugin, as before.

**OpenSearch needs no change.** Verified against 2.14.0 and 2.19.1. OpenSearch clients
do not send that header, so the 3.x client is fine against a 2.x server. `analysis-icu`
is required, as it always has been.

### JDK 25 and Gradle 9 — only if you build from source

The image ships JDK 25; you do not need it on the host. If you build DCB yourself
rather than pulling the published image, you now need **JDK 25** and **Gradle 9**. JDK
17 and 21 will not compile it.

Image building goes through the Gradle `dockerBuild` / `dockerBuildNativeBinary` tasks,
which generate the Dockerfile — as it did at v8.71.0. There is no committed `Dockerfile`
in either release. See `docs/local-development.md`.

### Image bases

| Image | Base |
|---|---|
| JVM (`<repo>:<tag>`) | `eclipse-temurin:25-jre-alpine` (was `21-jre-alpine`) |
| Native (`<repo>:native-<tag>`) | `cgr.dev/chainguard/wolfi-base` |

The native binary is dynamically linked against glibc, so a musl/Alpine base will not
run it. This matters if you mirror or allowlist base images, or scan against pinned
base distributions. **Tag naming is unchanged.**

---

## 2. Database migrations — the headline

Seven migrations apply on first startup of v9.0.0.

| Migration | What it does | Cost |
|---|---|---|
| `V8_71_001__patron_request_outcome` | Adds `outcome_code` to `patron_request`, then **backfills every historical row** with four UPDATE statements, three of which run correlated subqueries against `patron_request_audit` | **The expensive one.** Scales with your request and audit history |
| `V8_72_001__dcb_profile_membership` | New `dcb_profile_membership` table + four indexes | Trivial — empty table |
| `V9_0_001__consortium_branding` | Six nullable columns on `consortium` | Trivial — metadata only, no row rewrite |
| `V9_0_002__library_branding` | Three nullable columns on `library`, plus `idx_library_agency_id` | Small |
| `V9_0_003__brand_asset` | New `brand_asset` table (`bytea`, `STORAGE EXTERNAL`) | Trivial — empty table |
| `V9_0_004__consortium_brand_merge` | Copies two URLs into the new brand columns, writes provenance into `data_change_log`, then **drops six columns** from `consortium` | Small, but **irreversible** |
| `V9_0_005__analytics_indexes` | Six indexes on `patron_request` and `patron_request_audit` | **Significant.** See below |

### What `V9_0_005` builds

Five indexes on `patron_request`, and one on `patron_request_audit`:

```sql
CREATE INDEX IF NOT EXISTS pra_audit_date_idx ON patron_request_audit (audit_date);
```

The migration's own note records the measurement taken during development: at 5M audit
rows this index builds in about a second and occupies **107 MB**. There was previously
no index on `audit_date` at all, so this is new disk, not a replacement.

These are plain `CREATE INDEX`, **not `CREATE INDEX CONCURRENTLY`** — Flyway runs each
migration in a transaction and `CONCURRENTLY` cannot run inside one. They take an
`ACCESS EXCLUSIVE`-blocking share lock on the table for the duration of the build.
Writes to `patron_request_audit` wait.

### What this means operationally

Flyway runs **during application startup**, against the JDBC datasource, before the
service takes traffic. Three consequences:

- **Startup will take longer than you are used to** on the first v9.0.0 boot, and only
  the first. How much longer depends entirely on the size of your `patron_request` and
  `patron_request_audit` tables. We have not measured it at production scale, and you
  should not take a number on trust — **restore a copy of your production database and
  time the upgrade against it.** That is the only honest answer.
- **Flyway takes a lock**, so if you roll out multiple replicas at once the first one
  migrates and the others block until it finishes. That is correct behaviour, but it
  looks like a hung deployment. Roll out **one replica first**, let it go ready, then
  scale up.
- **Your probes must allow for it.** See [§6](#6-kubernetes-specifics) and
  [§7](#7-aws-ecsfargate-specifics).

### Before you start

Take a backup or a snapshot you can actually restore from. `V9_0_004` drops columns;
there is no migration that puts them back.

### If you have been running snapshots from `main`

**This applies only to non-production fleets that deploy `main` rather than tags.**

The branding migrations were renamed during development: `V8_73_001`–`V8_73_004` became
`V9_0_001`–`V9_0_004`. If a database applied the old names, `flyway_schema_history`
records them, Flyway will treat the `V9_0_*` files as new, and `V9_0_001` will fail:

```
ERROR: column "brand_logo_url" of relation "consortium" already exists
```

because the `alter table ... add` statements are not idempotent. Deployments that
upgraded from the **v8.71.0 tag** are unaffected — the old names never touched them.

If you hit this, the fix is to reconcile `flyway_schema_history` against the shipped
migrations on that specific database. Ask us rather than improvising; the right repair
depends on exactly which of the four were applied.

---

## 3. The clustering sweep — check this before you upgrade

This is the change most likely to surprise you, because nothing about it appears in the
migration list. It is a **background workload that starts on its own**, and on a large
catalogue it is not short.

### What changed

Improved clustering used to sit behind a feature flag. In v9.0.0 it is the only
implementation — the old path is deleted — and the ingest process version is now
hard-coded:

```java
public static int getProcessVersion() {
    return 5;          // was: featureIsEnabled(IMPROVED_CLUSTERING) ? 5 : 3
}
```

`ClusterHousekeepingService` reprocesses any cluster containing a bib below the current
process version. It was previously gated behind the same feature flag; that gate is now
removed, so it runs on every deployment.

### Why that matters

If improved clustering was **not** enabled on your deployment, every bib record in your
database is at process version 3. On first startup of v9.0.0 they all become outdated,
and the housekeeping task will work through the entire catalogue:

- it starts **10 seconds after startup** (`@Scheduled(initialDelay = "10s")`),
- takes batches of **2000 clusters**, with a 500 ms pause between batches,
- and repeats until nothing outdated remains, logging its own rate as it goes:

```
Processed 2000 clusters with oudated bibs. Total time 0 hours, 3 minute and 12 seconds (rate of 10.42 per second)
```

At the 20M-record scale DCB is designed for, that is a sustained load on Postgres **and**
on your search backend — re-clustering changes cluster membership, which flows through to
index updates.

### Find out whether it applies to you

Run this against your production database **before** you upgrade:

```sql
SELECT process_version, count(*) FROM bib_record GROUP BY process_version ORDER BY 1;
```

| Result | What happens on upgrade |
|---|---|
| Everything already at `5` | Nothing. The sweep finds no work and stops |
| Anything at `3` or `NULL` | Those records will be reprocessed, in batches, until done |

The count in the second row is the size of the job. Take it to your own timings rather
than to an estimate from us.

### If the sweep does apply

- **Expect elevated database and search load** for as long as it runs, on top of normal
  traffic. Plan the upgrade window accordingly, and prefer a quiet period.
- **Watch the rate line** in the logs. It reports clusters per second, which combined
  with the count from the query above gives you a genuine estimate within the first few
  minutes.
- **Kubernetes:** the task takes a federated lock, so with working Hazelcast discovery
  only one pod runs it at a time. That is what you want.
- **Fargate: this is the one to watch.** Hazelcast's default multicast discovery does not
  work on AWS, so each task is an isolated single-member cluster and the lock is
  per-task. **Every running task will run its own sweep concurrently.** If you are on
  Fargate and the query above shows a large version-3 population, run a **single task**
  until the sweep completes, then scale out.

There is no configuration switch to defer this. The lever you have is how many instances
are running when it starts.

---

## 4. New configuration

**Nothing in this release is a required new setting.** Every value below has a default
and DCB starts without any of them. The table marks the ones whose *default* changes
behaviour, which are the ones to read.

### Branding and uploads

| Variable | Default | Notes |
|---|---|---|
| `DCB_BRANDING_ASSETS_STORE` | `database` | **Default changes behaviour.** Brand images upload into Postgres. Set to `none` to remove the upload routes entirely — brand fields then accept absolute CDN URLs only |
| `DCB_BRANDING_ASSETS_MAX_BYTES` | `2097152` (2 MB) | **Also raises `micronaut.server.multipart.max-file-size` from the framework default of 1 MB**, deliberately, so the two limits governing one upload cannot drift. This applies to location and mapping import too |
| `DCB_BRANDING_ASSETS_MAX_DIMENSION` | `4096` | Decompression-bomb guard, read from the image header |
| `DCB_BRANDING_ASSETS_ORPHAN_GRACE` | `24h` | How long an unreferenced upload survives the daily sweep |
| `DCB_BRANDING_ASSETS_PATH_PREFIX` | `/discovery/brand-assets/` | Changing this after assets exist orphans every stored URL. Read it; do not tune it |

**Capacity:** with the default store, brand images live in your database and therefore in
your backups. The ceiling is small and bounded — a handful of images per consortium and
per library, 2 MB each — but it is database growth where previously there was none. If
you would rather not carry images in Postgres, `DCB_BRANDING_ASSETS_STORE=none` and use
a CDN.

`/info` now reports `dcb.branding.assets.store` so DCB Admin can tell "uploads are off
here" from "that upload failed".

### Insights (the reporting surface)

| Variable | Default | Notes |
|---|---|---|
| `DCB_INSIGHTS_ENABLED` | `true` | **On by default.** Endpoints under `/insights`, restricted to `CONSORTIUM_ADMIN` / `LIBRARY_ADMIN` / `ADMINISTRATOR` |
| `DCB_INSIGHTS_COLLECTION_CONCURRENCY` | `1` | Catalogue-wide aggregation over `bib_record`. Raise only with timings in hand |
| `DCB_INSIGHTS_COLLECTION_CACHE_TTL` | `15m` | Every collection figure is stale by at most this |
| `DCB_INSIGHTS_COLLECTION_MAX_WAIT` | `30s` | A queued caller waits this long, then gets **429** |

If administrators report a 429 opening the dashboard cold, that is this working as
designed — several panels queueing behind a concurrency of 1 — not a fault. Raise
concurrency only after measuring the aggregation on your own corpus.

### Discovery services

| Variable | Default | Notes |
|---|---|---|
| `DCB_DISCOVERY_ENABLED` | `false` | **Fails closed.** An unconfigured deployment behaves as though discovery does not exist |
| `DCB_DISCOVERY_AUDIENCE` | `dcb` | The audience a patron assertion must name |
| `DCB_DISCOVERY_MAX_ASSERTION_LIFETIME` | `PT2M` | DCB's own cap, enforced regardless of the issuer's `exp` |
| `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` | — | Trust anchors. **Malformed JSON fails startup deliberately** |

A list of objects **cannot** be expressed as indexed environment variables — Micronaut
resolves `DCB_DISCOVERY_TRUSTED_SERVICES_0_SERVICE_ID` to
`dcb.discovery.trusted-services.0.service-id`, and list binding needs the
`trusted-services[0].service-id` form. Hence the single JSON variable. On Kubernetes you
can mount YAML in a ConfigMap instead:

```yaml
dcb:
  discovery:
    trusted-services:
      - service-id: your-discovery-service
        issuer: https://discovery.example.org
        jwks-uri: https://discovery.example.org/.well-known/jwks.json
```

On Fargate, where there is no file to mount, use the JSON form:

```
DCB_DISCOVERY_TRUSTED_SERVICES_JSON=[{"service-id":"your-discovery-service","issuer":"https://discovery.example.org","jwks-uri":"https://discovery.example.org/.well-known/jwks.json"}]
```

### Shared index replicas

| Variable | Default | Notes |
|---|---|---|
| `DCB_INDEX_NUMBER_OF_REPLICAS` | `1` | **Reconciled at every startup.** DCB now applies this to the existing index, not only at creation |

Read that twice if you manage replica counts yourself. Previously the shipped index
settings applied at creation and DCB left them alone; now the configured value is
asserted on every boot, so a count you set by hand will be reset. On a single-node
cluster the default of `1` leaves the index **yellow** — set
`DCB_INDEX_NUMBER_OF_REPLICAS=0` there.

### NCIP peer authentication

Off by default (`dcb.peer-auth.enabled`), and only relevant if you run NCIP peer
traffic between DCB instances or to an ORS Appliance. It carries a **private signing
key** (`dcb.peer-auth.local-identity.private-jwk`), which is a secret and belongs in
your secret store, not a ConfigMap or a task definition environment variable. The
configuration is a nested structure — mount it as YAML rather than trying to express it
in environment variables. See `docs/ncip-peer-authentication.md`.

### Tracking

`AWAITING_RETURN_TO_SUPPLIER: 1h` has been added to `dcb.polling.durations`, supporting
requests cancelled by the patron while the item is out. No action needed; noted because
it is a new state you will see in tracking logs and dashboards.

---

## 5. Security changes that need coordination

### `/patrons/requests` no longer accepts any authenticated principal

This is the change most likely to break an integration, and it is a tightening rather
than a removal.

At v8.71.0, `PatronRequestController` carried `@Secured(IS_AUTHENTICATED)` as its class
default. That is not a permission — it is a claim about the whole Keycloak realm — and
five methods had no override, so they were reachable by **any** principal the realm would
authenticate:

```
POST /patrons/requests/place
POST /patrons/requests/place/expeditedCheckout
POST /patrons/requests/place/walkup
POST /patrons/requests/{id}/rollback
POST /patrons/requests/{id}/update
```

In v9.0.0 every method carries an explicit role set:

| Endpoint | Roles |
|---|---|
| Class default | `CONSORTIUM_ADMIN`, `ADMINISTRATOR`, `LIBRARY_ADMIN`, `INTERNAL_API` |
| `/place`, `/place/expeditedCheckout`, `/place/walkup`, `/{id}/update` | the above plus `LIBRARY_READ_ONLY` |
| `/{id}/rollback` | `CONSORTIUM_ADMIN`, `ADMINISTRATOR` only |
| `/{id}/transition/cleanup` | `CONSORTIUM_ADMIN`, `ADMINISTRATOR`, `LIBRARY_ADMIN` |

**What you need to do:** audit every client credential that calls `/patrons/requests/**`
and confirm it holds one of the roles above. Service-to-service callers want
`INTERNAL_API`. A credential that previously worked purely by being a valid realm token
will now get a 403.

**Nothing was removed.** `GET /patrons/requests` still exists and is still
`@Secured(ADMINISTRATOR)`, as it was at v8.71.0.

**One narrowing to know about.** `GET /patrons/requests/patrons/{hostLmsCode}/requests`
was `{CONSORTIUM_ADMIN, ADMINISTRATOR}` at v8.71.0 and is now `ADMINISTRATOR` only. It is
kept solely as a compatibility alias (see below); the current path is
`GET /patrons/requests/{hostLmsCode}`, which does admit `CONSORTIUM_ADMIN`. If something
of yours called the old path with a `CONSORTIUM_ADMIN` credential, point it at the
current one.

### EBSCO Locate — deliberately unaffected, nothing to do

**If your deployment serves EBSCO Locate, it keeps working across this upgrade and needs
no change.** That was designed in, not luck.

Locate authenticates with a **service account holding the `ADMIN` role** — the
pre-existing arrangement, not the new `DISCOVERY_SERVICE` model. It is closed-source and
outside this workspace, so the compatibility audit that cleared the admin UIs could not
inspect it, and both routes it calls in production are retained explicitly and marked
`LEGACY` in the source:

| Route Locate calls | Status in v9.0.0 |
|---|---|
| `GET /patrons/requests/patrons/{hostLmsCode}/requests` | Retained as a deprecated alias, `@Secured(ADMINISTRATOR)`. The rename to `GET /patrons/requests/{hostLmsCode}` was cosmetic; aliasing it stops a live integration 404ing |
| `GET /patrons/requests/{?pageable*}` | Retained, `@Secured(ADMINISTRATOR)`, unchanged from v8.71.0. Self-scopes off `localSystemCode` / `localSystemPatronId` claims on the caller's own token |

Both admit `ADMINISTRATOR` and nothing else, which is what Locate's credential already
holds. No Keycloak change, no endpoint change, no coordination needed.

This is recorded in the code and in `docs/discovery-service-approach.md` §8 as **the one
exception, not the rule**. Both routes are marked for deletion once EBSCO has moved to
the model below, so treat this as a reprieve with a shelf life rather than a supported
pattern — and do not build anything new on either route.

### Future discovery services — `DISCOVERY_SERVICE` and patron assertions

Any discovery service onboarded from now on uses the new model, and Locate will move to
it eventually.

`/discovery/**` is new in v9.0.0 and **off by default** (`DCB_DISCOVERY_ENABLED=false`),
so it costs you nothing until you onboard someone. When you do:

- the discovery **backend** holds a confidential `DISCOVERY_SERVICE` client credential
  that reaches `/discovery/**` and nothing else — it authenticates the *caller*, never a
  patron, and must never appear in a `@Secured` on any other controller;
- the **patron** travels separately as a short-lived signed assertion in the
  `X-OpenRS-Patron-Assertion` header, which DCB verifies (signature, trusted issuer,
  audience, subject, expiry, and DCB's own lifetime cap) before enforcing per-patron
  ownership in SQL on every call;
- you configure the trust anchors per environment — [§4](#discovery-services) has the
  ConfigMap and Fargate-JSON forms.

The difference from the Locate arrangement is who decides which patron's data comes back.
Under `ADMIN`-plus-token-claims the *caller* decides; under an assertion DCB verifies it.
That is the whole point of the change, and why the Locate routes carry a deletion note.

Vendor-facing integration detail is in `docs/discovery-service-approach.md` §6, which you
can hand to a discovery supplier directly.

### About the `PATRON` role — it does not affect you

The v9.0.0 changelog and commit log describe a breaking change,
*"Replace the PATRON role with DISCOVERY_SERVICE"*, and list
`GET /patrons/requests` and `POST /patrons/requests/{id}/cancel` as removed.

**That is written relative to the development branch, not to v8.71.0.** Checked against
the tags:

- `RoleNames` at v8.71.0 contains `ADMIN`, `INTERNAL_API`, `CONSORTIUM_ADMIN`,
  `LIBRARY_ADMIN`, `LIBRARY_READ_ONLY` and `INTEROP_TESTER`. There is no `PATRON`.
  The role was added and removed inside this release window and never reached a tag.
- `POST /patrons/requests/{id}/cancel` did not exist at v8.71.0 either.

So if you are upgrading from v8.71.0: there is no `PATRON` role in your realm to retire,
no integration forwarding patron bearer tokens to migrate, and no endpoint of yours that
disappeared. The real security change for you is the role tightening above — and Locate,
which never used `PATRON` either, is unaffected.

### A new anonymous route

`/discovery/brand-assets/{key}` is anonymous by design — patron-facing brand images have
to render before anyone logs in. Responses carry
`Cache-Control: public, max-age=31536000, immutable`, which is safe because the key is
the SHA-256 of the content: a replaced image is a different URL.

Check your ingress, WAF and CDN rules admit it, and let the CDN cache it — every read
you cache is a `bytea` read you do not make against Postgres.

### Logging

Two leaks were closed: patron credentials are no longer logged during authentication,
and application event payloads are no longer logged. If your log pipeline had
redaction rules for either, they are now belt-and-braces rather than load-bearing.

---

## 6. Kubernetes specifics

**Startup budget.** The v9.0.0 first boot runs the migrations in [§2](#2-database-migrations--the-headline).
Use a `startupProbe` with a generous `failureThreshold` so a slow migration does not
get the pod killed and restarted mid-migration:

```yaml
startupProbe:
  httpGet: { path: /health, port: 8080 }
  periodSeconds: 10
  failureThreshold: 180   # 30 minutes; tune from your own timed dry run
livenessProbe:
  httpGet: { path: /health, port: 8080 }
  periodSeconds: 30
```

A liveness probe that fires during a migration is the worst outcome available: the pod
restarts, Flyway retries, and you loop. The startup probe exists precisely to prevent
that — make sure one is defined, not just liveness and readiness.

**Roll one replica first.** Scale to 1, take the upgrade, let it go ready, then scale
back up. Flyway's lock will serialise the replicas anyway; doing it deliberately means
you can see what is happening.

**Memory.** JDK 25 and Micronaut 5 have somewhat different baseline heap behaviour,
though we have not observed a material increase. The image still sizes itself from the
container limit via `-XX:MaxRAMPercentage=80.0`. Review the limit if you are close to it.

**Disk.** `V9_0_005` adds new indexes — over 100 MB on a 5M-row audit table — and the
brand asset store adds database growth. Check headroom on the volume and on your backup
target before, not during.

**ConfigMap over environment variables** for `dcb.discovery.trusted-services` and
`dcb.peer-auth`, both of which are nested structures that do not express cleanly as
environment variables.

## 7. AWS ECS/Fargate specifics

**Health check grace period.** The task-level equivalent of the startup probe:

```
healthCheckGracePeriodSeconds: 1800
```

If the ALB target group starts failing the task before migrations finish, ECS will kill
and reschedule it — same restart loop as above. Raise the grace period for the upgrade
deployment; you can lower it again afterwards.

**Deploy one task first.** Set `desiredCount: 1` with
`minimumHealthyPercent: 0` for the upgrade, then scale back. Rolling several tasks at
once means the others sit blocked on Flyway's lock while their health checks tick.

**JSON in one variable.** Fargate task definitions cannot mount a config file, so use
`DCB_DISCOVERY_TRUSTED_SERVICES_JSON` rather than indexed variables — see
[§4](#discovery-services) for why indexed variables cannot work here.

**Secrets.** The NCIP peer-auth private JWK must come from Secrets Manager or SSM
Parameter Store via `secrets` in the task definition, never `environment`.

**Hazelcast and multicast — unchanged, but worth knowing.** DCB's default
`hazelcast.yaml` uses multicast discovery, which does not work on AWS. Each task runs as
an isolated single-member cluster, so federated locks are per-task rather than
cluster-wide unless you supply your own Hazelcast configuration. This is pre-existing
behaviour, not new in v9.0.0.

**Task memory.** Same note as Kubernetes: review if you are close to a limit.

---

## 8. Rollback

**This release is not cleanly reversible, and that is a change from the Micronaut 5
release, which was.**

`V9_0_004__consortium_brand_merge` drops six columns from `consortium`:
`header_image_url`, `header_image_uploader`, `header_image_uploader_email`,
`about_image_url`, `about_image_uploader`, `about_image_uploader_email`. The two URLs
are copied into the new brand columns first, and the uploader names and email addresses
are preserved into `data_change_log` before they go — deliberately, because a member of
staff's name and email address belongs in a role-checked audit record, not in a column
any authenticated principal can read.

Rolling the image back to 8.x leaves those columns absent. An 8.x DCB expecting them
will not work against a v9 schema.

Your rollback path is therefore **restore the database from the backup you took before
the upgrade, and roll the image back together with it**. Take that backup.

The Elasticsearch caveat from the previous release still applies: if you upgraded ES to
9.x in order to take this, rolling DCB back to an 8.x-era client will not work against
the upgraded cluster.

---

## 9. New operator surfaces

Worth knowing about; none require configuration.

**Stalled harvest detection.** A source import that produced no chunk used to end
without saving a checkpoint, indistinguishable from success in the logs, and the only
recorded remedy was deleting a `job_checkpoint` row by hand. There is now a watchdog on
a fifteen-minute delay, and three endpoints on `AdminController`:

| Endpoint | Purpose |
|---|---|
| `GET /admin/sourceImport/status` | Whether each source is progressing |
| `POST /admin/sourceImport/{hostLmsCode}/reconcile` | Reconcile records DCB missed |
| `POST /admin/sourceImport/{hostLmsCode}/resetCheckpoint` | Rewind the cursor |

Escalation stays manual on purpose: auto-firing a full re-harvest across a fleet of
library systems on a false positive is a self-inflicted denial of service.

**Search backend reporting.** Introduced in the Micronaut 5 release and still the
quickest way to answer "what are we running against":

```bash
curl -s http://<dcb-host>:8080/info | jq '.dcb.index.backend'
# { "distribution": "opensearch", "version": "2.14.0" }
```

**Clustering.** Now the only implementation, and it re-clusters existing data by itself
after upgrade — see [§3](#3-the-clustering-sweep--check-this-before-you-upgrade), which
is the one section not to skim. `POST /admin/reindex`, `POST /admin/dedupe/matchpoints`
and `POST /admin/validateClusters` remain available for operator-driven rebuilds.

**Metrics — two new meters, and one heap leak closed.** Event counts used to accumulate
in an in-heap `stat_counters` map that grew without bound and had no reader; on a
long-running pod that was a slow leak. It is gone, replaced by two Micrometer meters
tagged by `event` and `context`:

```
dcb.stats.events   (counter)
dcb.stats.timed    (timer)
```

These are **additions** to `/prometheus`, not renames — the old map was never scraped, so
no existing dashboard or alert breaks. There is a new panel's worth of data to pick up if
you want it.

**Request statistics endpoints are preserved.** The two `/patrons/requests/stats` routes
that existed at v8.71.0 — `GET /stats/top-requestors` and `GET /stats/top-requested-titles`
— still answer on the same paths with the same role set
(`CONSORTIUM_ADMIN`, `LIBRARY_ADMIN`, `ADMINISTRATOR`), now served by a deprecated shim
that delegates to the new Insights implementation. They are marked `@Deprecated` and
documented as such in the OpenAPI, and log at WARN when called, so you can see whether
anything still uses them. The new home for reporting is `/insights`.

---

## 10. What has not changed

| | Status |
|---|---|
| Ports | **Unchanged** (8080) |
| Health, info and Prometheus endpoints | **Unchanged** paths and behaviour |
| Image tag scheme | **Unchanged** — `<repo>:<tag>`, native as `<repo>:native-<tag>` |
| Existing environment variables | **Unchanged.** No renames |
| Host LMS configuration in the database | **Unchanged** |
| Hazelcast | **Unchanged** — pinned at 5.4.0 |
| OpenSearch server requirements | **Unchanged** — `analysis-icu` still required |
| Keycloak realm, roles and JWKS wiring | **Unchanged.** No role is added or removed for an existing deployment; what changed is which roles DCB *requires* on `/patrons/requests/**` — see [§5](#5-security-changes-that-need-coordination) |
| The committed `Dockerfile` | **Unchanged** — there was none at v8.71.0 and there is none at v9.0.0. Images are built by the Gradle tasks in both |
| `GET /patrons/requests` | **Still present**, still `@Secured(ADMINISTRATOR)` |
| EBSCO Locate | **Unaffected.** Both routes it calls are retained for its `ADMIN` service account — see [§5](#ebsco-locate--deliberately-unaffected-nothing-to-do) |
| `/patrons/requests/stats/top-requestors` and `/top-requested-titles` | **Still answer** on the same paths with the same roles, now via a deprecated shim over Insights |
| Prometheus metric names | **Unchanged.** Two meters are added; none renamed |

---

## 11. Upgrade procedure

1. **Audit the client credentials that call `/patrons/requests/**`** against the role
   table in [§5](#5-security-changes-that-need-coordination). Anything relying on
   `IS_AUTHENTICATED` alone will start getting 403s.
2. **Run the clustering query from [§3](#find-out-whether-it-applies-to-you).** The
   answer decides whether steps 7 and 10 are a formality or the main event.
3. **If you run Elasticsearch, confirm the cluster is 9.x.** If you run OpenSearch,
   confirm `analysis-icu` is installed.
4. **Time the migration on a restored copy of production.** This is the step people skip
   and regret. It gives you the number for step 6.
5. **Take a backup you have tested restoring.** [§8](#8-rollback) explains why.
6. **Raise the startup probe budget / health check grace period** to comfortably exceed
   the time measured in step 4.
7. **Scale to a single replica or task.** Mandatory if step 2 found version-3 records
   and you are on Fargate, where every task would otherwise run its own sweep.
8. **Deploy v9.0.0.** Watch the logs for Flyway applying `V8_71_001` through `V9_0_005`.
9. **Confirm it went ready**, then check `/info` reports the expected
   `dcb.index.backend` and `dcb.branding.assets.store`.
10. **If a clustering sweep is running, let it finish before scaling out.** The rate line
    in the logs tells you where it is up to.
11. **Scale back up.**
12. **Lower the probe budget** back to your normal value if you raised it temporarily.
13. **Set `DCB_INDEX_NUMBER_OF_REPLICAS=0`** if you run a single-node search cluster and
    the index has gone yellow.

---

## 12. Troubleshooting

| Symptom | Cause |
|---|---|
| `status: 400 ... Accept version must be ... found 9` | Elasticsearch 8.x behind a 9.x client — upgrade ES to 9.x |
| `status: 401 ... [es/indices.exists]` | Index credentials not reaching the client — check `DCB_INDEX_USERNAME` / `DCB_INDEX_PASSWORD` |
| Pod restarts repeatedly, logs stop mid-migration | Liveness probe firing during migration — no `startupProbe`, or too small a `failureThreshold`. See [§6](#6-kubernetes-specifics) |
| Sustained database and search load after upgrade, `Processed N clusters with oudated bibs` in the logs | The clustering sweep. Expected if your bibs were at process version 3. See [§3](#3-the-clustering-sweep--check-this-before-you-upgrade) |
| Several tasks all logging the clustering sweep at once on Fargate | Hazelcast multicast does not work on AWS, so the federated lock is per-task. Run one task until the sweep completes |
| Second and subsequent replicas hang at startup | Flyway lock, held by the replica actually migrating. Expected. Roll one at a time |
| `column "brand_logo_url" of relation "consortium" already exists` | This database applied the pre-release `V8_73_*` migration names. See [§2](#if-you-have-been-running-snapshots-from-main) |
| An integration that worked before now gets 403 on `/patrons/requests/**` | Its credential relied on `IS_AUTHENTICATED`. Grant it a role from the table in [§5](#patronsrequests-no-longer-accepts-any-authenticated-principal) — `INTERNAL_API` for service-to-service |
| Calls to `/discovery/**` return 401/403 | `DCB_DISCOVERY_ENABLED` still `false`, no trust anchor configured, or the caller holds no `DISCOVERY_SERVICE` credential. Only relevant if you are onboarding a discovery service |
| Startup fails parsing discovery configuration | Malformed `DCB_DISCOVERY_TRUSTED_SERVICES_JSON`. Deliberate: a trust anchor that binds nothing silently is indistinguishable from one nobody configured |
| Uploads rejected at just over 1 MB | Should no longer happen — the multipart limit now tracks `DCB_BRANDING_ASSETS_MAX_BYTES` (2 MB). If it does, something is overriding `micronaut.server.multipart.max-file-size` |
| `/insights` panels return 429 | Collection analysis queueing behind `DCB_INSIGHTS_COLLECTION_CONCURRENCY=1` past `DCB_INSIGHTS_COLLECTION_MAX_WAIT`. Measure before raising |
| Search index went yellow after upgrade | `DCB_INDEX_NUMBER_OF_REPLICAS` defaults to `1` and is now reconciled at every startup. Set `0` on a single-node cluster |
| `CP subsystem is a licensed feature` | Hazelcast newer than 5.4.0 on the classpath — should not happen from our image; report it |

---

## Questions we would rather you asked than guessed

- **How long will the migration take on my data?** We do not know, and neither does
  anyone who has not run it against your data. Step 4 of
  [§11](#11-upgrade-procedure) is the answer.
- **How long will the clustering sweep take?** Same answer, with a better instrument: the
  count from the query in [§3](#find-out-whether-it-applies-to-you) divided by the rate
  DCB logs within the first few minutes of it running.
- **Can I skip the backup because branding is empty here?** `V9_0_004` still drops the
  columns. The backup is about the schema, not the data.
- **My `flyway_schema_history` looks wrong after running snapshots.** Ask. The right
  repair depends on which migrations were applied under which names, and improvising
  there is how a database ends up half-migrated.

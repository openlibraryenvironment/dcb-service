# DCB v9.2.0: upgrade guide for hosting providers

For anyone running DCB from the published image on Kubernetes or AWS ECS/Fargate.
It covers everything between **v8.71.0** (23 July 2026) and **v9.2.0**, so it includes v9.0.0 and
v9.1.0. If you are already on v9.1.0, the sections marked **New in 9.2.0** are the ones that change
for you, and only migrations `V9_0_009` to `V9_0_017` are new.

This guide describes v9.2.0: `main` as of 20 October 2026 (`8f78ce7ec`) with this release's
configuration and logging changes. Every behaviour stated here names the file that implements it,
under **Source**, so you can check it against the tag you deploy. Paths are relative to the
repository root.

Companion documents, all in this repository:

| Topic | Document |
|---|---|
| Micronaut 5 / JDK 25, image bases, Elasticsearch | `docs/micronaut-5-hosting-notes.md` |
| Branding, the asset store, capacity | `docs/branding.md` |
| Discovery services and patron assertions | `docs/discovery-service-approach.md` |
| NCIP peer authentication | `docs/ncip-peer-authentication.md` |
| Insights endpoints | `docs/insights.md` |
| The supplier preflight | `docs/operational/supplier-preflight.md` |
| The duplicate-request check | `docs/operational/duplicate-request-check.md` |
| Notes and tags on requests | `docs/operational/patron-request-notes.md` |
| Request ids in logs | `docs/request-id.md` |
| Cancelled while the item is out | `docs/cancellation-handling.md` |
| The shared index | `docs/shared-index.md` |

---

## Summary

These need a decision before you upgrade. Everything else is automatic or opt-in.

1. **It is not a rolling upgrade.** Stop every v8.71.0 task before the first v9.2.0 task starts.
   `V9_0_004` drops six `consortium` columns v8.71.0 reads, and two migrations hold an exclusive
   lock on `patron_request` for their whole backfill. **[§2](#2-database-migrations)**
2. **Nineteen migrations run on first start.** 
3. **It is not reversible without a restore.** **[§9](#9-rollback)**
4. **More routes need a role.** Placement and the rest of `/patrons/requests/**` (since v9.0.0),
   and now `POST /v2/patron/auth/lookup`, which v8.71.0 left anonymous.**[§6](#legacy-route-ebsco-locate)**
5. **New in 9.2.0: a setting that is present but blank stops startup** when the feature it belongs
   to is on, and so does any `DCB_IDENTITY_PROVIDER_*` variable other than
   `DCB_IDENTITY_PROVIDER_KEYCLOAK_*`. A feature that is not configured is not checked at all.
   Review your task definitions. **[§5](#5-configuration)**
6. **If improved clustering was not already on, a full background re-cluster starts ten seconds
   after startup** (since v9.0.0). One SQL query tells you. **[§3](#3-the-clustering-sweep)**
7. **The shared-index mapping is purely additive, and it is applied at startup.** An index that
   refuses it stops DCB starting. **[§4](#4-the-shared-index)**
8. **If you run Elasticsearch, it must be 9.x** (since v9.0.0). If you run OpenSearch, this does not apply **[§1](#1-hard-requirements)**

---

## Upgrading a consortium in production: the hosting provider's part

For a consortium live on v8.71.0 whose discovery layer is EBSCO Locate, some of whose libraries'
patrons sign in through SAML at their own identity provider. Each row links to the detail, and
[§12](#12-upgrade-procedure) is the full procedure.

| When          | The hosting provider | With | Detail |
|---------------|---|---|---|
| Before deploy | Remove blank and retired settings from the task definitions, and set `DCB_REQUESTS_STAFF_CLIENT_IDS` to your two admin clients | | [§5](#every-setting-new-since-v8710) |
|               | Export the index mapping and compare it with `mappings-2.json`; plan a new `DCB_INDEX_NAME` if it conflicts | | [§4](#4-the-shared-index) |
|               | Configure the ingress: rate limits, NCIP blocked if there are no peers, a body limit of at least 2 MB, an idle timeout above the placement limit | | [§6](#anonymous-routes-rate-limit-them-at-your-ingress) |
| Before deploy | Back up, stop every v8.71.0 task (this is not a rolling upgrade), and deploy one v9.2.0 task with `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT=PT5M` and the supplier preflight off | | [§12](#12-upgrade-procedure), steps 11 to 14 |
|               | Deploy DCB Admin and DCB Admin for Libraries after DCB, with `VITE_LOCALE=en-US` for a consortium in the US | | [§12](#12-upgrade-procedure), step 16; each app's deployment guide |
| After         | Correct Host LMS records whose ping reads REFUSED or MISCONFIGURED; turn the supplier preflight on weeks later | consortium staff | [§12](#12-upgrade-procedure), steps 17 and 21 |
|               | Expect a burst of alarm webhook posts a few minutes after the first start | | [§5](#tracking-scheduling-and-alarms) |

---

## 1. Hard requirements

### Elasticsearch must be 9.x

**Only if you run Elasticsearch rather than OpenSearch.** Micronaut 5 brings `elasticsearch-java`
9.x, and Elastic's clients send a major-version compatibility header, so a 9.x client cannot talk
to an 8.x server:

```
Accept version must be either version 8 or 7, but found 9
```

This surfaces at startup as a 400 from `[es/indices.exists]`. Upgrade the cluster to 9.x first.
It also needs the `analysis-icu` plugin, as before.

**OpenSearch needs no change.** OpenSearch clients do not send that header. `analysis-icu` is
required, as it always has been.

Source: `docs/micronaut-5-hosting-notes.md`.

### JDK 25 and Gradle 9: only if you build from source

The image ships JDK 25; you do not need it on the host. To build DCB yourself you need **JDK 25**
and **Gradle 9**. Images are built by the Gradle `dockerBuild` / `dockerBuildNativeBinary` tasks;
there is no committed `Dockerfile`. See `docs/local-development.md`.

### Image bases

| Image | Base |
|---|---|
| JVM (`<repo>:<tag>`) | `eclipse-temurin:25-jre-alpine` (was `21-jre-alpine`) |
| Native (`<repo>:native-<tag>`) | `cgr.dev/chainguard/wolfi-base` |

The native binary is dynamically linked against glibc, so a musl/Alpine base will not run it.
Tag naming is unchanged.

Source: `dcb/build.gradle` (the docker tasks).

---

## 2. Database migrations

From v8.71.0, nineteen migrations apply on first start, one transaction per file, so each lock is
held until that file commits. None touches `bib_record`, `cluster_record`, `source_record` or
`match_point`.

Source: `dcb/src/main/resources/db/migration/`, one file per row below; Flyway settings under
`flyway:` in `dcb/src/main/resources/application.yml`.

| Migration | What it does | Cost and lock |
|---|---|---|
| `V8_71_001__patron_request_outcome` | Adds `outcome_code` to `patron_request`, then backfills every historical row: four `UPDATE`s, three with correlated subqueries against `patron_request_audit` | **The expensive one.** `ACCESS EXCLUSIVE` on `patron_request` for the whole file |
| `V8_72_001__dcb_profile_membership` | New table, four indexes | Trivial |
| `V9_0_001__consortium_branding` | Six nullable columns on `consortium` | Trivial |
| `V9_0_002__library_branding` | Three nullable columns on `library`, plus `idx_library_agency_id` | Small |
| `V9_0_003__brand_asset` | New `brand_asset` table | Trivial |
| `V9_0_004__consortium_brand_merge` | Copies two URLs into the brand columns, writes provenance to `data_change_log`, then **drops six columns** from `consortium` | Small, **irreversible** |
| `V9_0_005__analytics_indexes` | Six indexes on `patron_request` and `patron_request_audit`, built without `CONCURRENTLY` | **Significant.** Writes to both tables wait for each build. The file's own note: about 107 MB for `pra_audit_date_idx` at 5M audit rows |
| `V9_0_006__library_user_account` | New table, index and audit triggers | Trivial |
| `V9_0_007__agency_max_local_holds` | Nullable `max_local_holds` on `agency` | Trivial |
| `V9_0_008__pr_tracking_resumed_at` | Nullable timestamp on `patron_request` | Metadata only |
| `V9_0_009__location_creation_source` | `creation_source` on `location`, `NOT NULL DEFAULT 'UNKNOWN'` | Metadata only on Postgres 11+ |
| `V9_0_010__alarm_code_length` | Widens `alarm.code` to `varchar(255)` | Trivial |
| `V9_0_011__host_lms_last_ping` | Nullable `jsonb` on `host_lms` | Trivial |
| `V9_0_012__supplier_request_item_index` | `idx_supplier_request_item` on `supplier_request (host_lms_code, local_item_id) WHERE is_active`, not `CONCURRENTLY` | Writes to `supplier_request` wait for the build |
| `V9_0_013__support_url` | Nullable columns on `library` and `consortium` | Trivial |
| `V9_0_014__announcement` | New table, index and audit trigger | Trivial |
| `V9_0_015__library_classification_scheme` | Nullable column on `library` | Trivial |
| `V9_0_016__patron_request_placed_by_staff` | Adds `placed_by_staff` to `patron_request`, then sets it for earlier staff requests (expedited checkout, or a note or description starting `Staff Request:`) | `ACCESS EXCLUSIVE` on `patron_request` for the whole file; one table scan |
| `V9_0_017__patron_request_notes_and_tags` | Three new tables for request notes and tags, and five seed tags | Trivial; no foreign key to `patron_request` |

### What this means for you

- **Stop every v8.71.0 task first.** After `V9_0_004`, v8.71.0's `Consortium` entity selects
  columns that no longer exist, and placement fails on any old task still running. While
  `V8_71_001` or `V9_0_016` runs, every read and write of `patron_request` waits.
- **The first start takes longer,** by an amount that depends on the size of `patron_request` and
  `patron_request_audit`. If you need an exact management, **restore a copy
  of your production database and time the upgrade against it.**
- **Flyway runs at application startup,** before the service takes traffic. Your probes must
  allow for it: [§7](#7-kubernetes-specifics), [§8](#8-aws-ecsfargate-specifics).
- **Take a backup you can restore,** immediately before. There is no migration that puts the
  `consortium` columns back.


## 3. The clustering sweep

**Since v9.0.0.** Improved clustering is the only implementation, and the ingest process version is
fixed at 5. `ClusterHousekeepingService` reprocesses any cluster containing a bib below the current
process version, on every deployment. **If you already have improved-clustering switched on, you don't need to do anything**

Source: `dcb/src/main/java/org/olf/dcb/ingest/IngestService.java` (`getProcessVersion`);
`dcb/src/main/java/org/olf/dcb/core/clustering/ClusterHousekeepingService.java`.

If improved clustering was **not** on for you, every bib is at process version 3, and on first
start the housekeeping task works through the whole catalogue: it starts 10 seconds after startup,
in batches of 2,000 clusters with a 500 ms pause, and logs its rate as it goes:

```
Processed 2000 clusters with oudated bibs. Total time 0 hours, 3 minute and 12 seconds (rate of 10.42 per second)
```

If you're not sure, run this against production **before** you upgrade:

```sql
SELECT process_version, count(*) FROM bib_record GROUP BY process_version ORDER BY 1;
```

| Result | What happens on upgrade |
|---|---|
| Everything already at `5` | Nothing. The sweep finds no work |
| Anything at `3` or `NULL` | Those records are reprocessed in batches until none remain |

While it runs, expect raised load on Postgres and on the search backend. On Kubernetes with working
Hazelcast discovery, one pod runs it. **On Fargate, Hazelcast's multicast discovery does not work,
so each task takes its own lock and runs its own sweep:** run a single task until it completes.
There is no switch to defer it.

---

## 4. The shared index

### The mapping is purely additive

Between v8.71.0 and v9.2.0, `mappings-2.json` only adds field paths: about thirty, for discovery
metadata such as `classification`, `shelfSort`, `alternateScripts`, `relationships`,
`carrier-type`, member `availability` and `firstSeen`. No field is removed, renamed or retyped.
`settings-2.json` is unchanged. The one new field that names an analyzer (`subjects.fullLabel`,
`icu_folding_nopunc`) uses an analyzer v8.71.0's settings already define.

`fullSettings-2.json` is new. It is used only when DCB creates an index, never against an
existing one.

Source: `dcb/src/main/resources/sharedIndex/mappings-2.json`, `settings-2.json`,
`fullSettings-2.json`.

### The mapping is applied at every start, and a refusal stops DCB

At startup DCB sends the mapping to the live index and waits for the answer. If the index refuses
it, DCB does not start: fulfilment, tracking and patron authentication go down with search.

Source: `dcb/src/main/java/org/olf/dcb/indexing/SharedIndexLiveUpdater.java`
(`sharedIndexService.initialize()`, blocking).


### New fields fill on the nightly sync

`IndexSynch` re-sends every cluster 20 seconds after start and then every 24 hours, so documents
gain `firstSeen` and the other Postgres-derived fields from the first pass.

The MARC-derived discovery metadata does not backfill on existing bibs: it is written when a record
is processed, and the process version did not change for it. `shelfSort` also needs a library's
`classification_scheme`, which `V9_0_015` adds as null for every library.

Source: `dcb/src/main/java/org/olf/dcb/indexing/job/IndexSynch.java` (`@Scheduled(initialDelay =
"20s", fixedDelay = "24h")`).

### Replicas are applied at every start

`DCB_INDEX_NUMBER_OF_REPLICAS` (default `1`) is now set on the existing index at every start, not
only when DCB creates it. A count you set by hand is reset. On a single-node cluster the default
leaves the index yellow: set `0` there.

Source: `reconcileNumberOfReplicas()` in
`dcb/src/main/java/org/olf/dcb/indexing/opensearch/OpenSearchSharedIndexService.java` and
`.../elasticsearch/ElasticsearchSharedIndexService.java`.

---

## 5. Configuration

### Every setting new since v8.71.0

Each variable is the environment form of the `dcb.…` key its source reads, and any of them can be
set in a mounted configuration file instead. **Since** is the first release that read it. Source
paths are under `dcb/src/main/java/org/olf/dcb/` unless given in full. A setting you leave out
takes the default shown, and what a blank one does is in the next table.

| Variable                                                                                                                                     | Default | What it does | Since | Source |
|----------------------------------------------------------------------------------------------------------------------------------------------|---|---|---|---|
| **Placement**                                                                                                                                | | | | |
| `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT`                                                                                                      | `PT55S`, logged at startup when you leave it out | One limit on all of a placement's checks together. A placement still checking when it runs out is refused with `PREFLIGHT_CHECKS_TIMED_OUT`, and nothing is saved. v8.71.0 had no limit | 9.2.0 | `request/fulfilment/PreflightChecksTimeout.java` |
| `DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED`                                                                                                    | `false` | On, resolution skips the copies of a supplier whose setup or last ping says it cannot supply, and the request goes to another library. See `docs/operational/supplier-preflight.md` | 9.2.0 | `request/fulfilment/SupplierPreflightService.java` |
| `DCB_REQUESTS_PREFLIGHT_CHECKS_CHOSEN_COPY_SUPPLIER_ENABLED`                                                                                 | `true` | With the supplier preflight on, a request for a copy staff chose is refused before it exists, with `SUPPLIER_CANNOT_SUPPLY` and the fix, when that copy's supplier cannot supply. Does nothing while the supplier preflight is off. `false` turns this check off alone | 9.2.0 | `request/fulfilment/ChosenCopySupplierPreflightCheck.java` |
| `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ACTIVE_WINDOW`                                                                             | unset: the earlier request's whole active life | With the duplicate-request check on, only active requests placed within this duration count as duplicates. Must be positive | 9.2.0 | `request/fulfilment/PreventDuplicateRequestsPreFlightCheck.java` |
| `DCB_REQUESTS_STAFF_CLIENT_IDS`                                                                                                              | `dcb-admin,dcb-admin-for-libraries` | The OIDC clients DCB Admin and DCB Admin for Libraries sign in through. A request placed with a token issued to one is recorded as placed by staff (`placedByStaff`) and is exempt from the duplicate-request check. Set your own two if they differ | 9.2.0 | `security/StaffClients.java` |
| **Access and accounts**                                                                                                                      | | | | |
| `DCB_SECURITY_ADMIN_UI_CLIENT_ID`                                                                                                            | unset: the access bar is off | DCB Admin's OIDC client. Set, a token issued to it needs a consortium role | 9.1.0 | `security/AdminUiAccessConfig.java` |
| `DCB_SECURITY_ADMIN_UI_MODE`                                                                                                                 | `WARN` | `WARN` logs what the bar would refuse and refuses nothing; `ENFORCE` refuses. Read only when the client id is set | 9.1.0 | `security/AdminUiAccessConfig.java` |
| `DCB_IDENTITY_PROVIDER_KEYCLOAK_BASE_URL`                                                                                                    | none | The Keycloak in which DCB Admin provisions DCB Admin for Libraries accounts | 9.2.0 | `security/provisioning/IdentityProviderConfig.java` |
| `DCB_IDENTITY_PROVIDER_KEYCLOAK_REALM`                                                                                                       | none | Its realm | 9.2.0 | `security/provisioning/keycloak/KeycloakConfig.java` |
| `DCB_IDENTITY_PROVIDER_KEYCLOAK_CLIENT_ID`                                                                                                   | none | The confidential client whose service account provisions the accounts | 9.2.0 | `security/provisioning/IdentityProviderConfig.java` |
| `DCB_IDENTITY_PROVIDER_KEYCLOAK_CLIENT_SECRET`                                                                                               | none, deliberately | That client's secret | 9.2.0 | `security/provisioning/IdentityProviderConfig.java` |
| **Discovery: the new route - does not apply to environments with EBSCO Locate ([§6](#new-route-discovery-services-with-patron-assertions))** | | | | |
| `DCB_DISCOVERY_ENABLED`                                                                                                                      | `false` | Turns on the patron-scoped `/discovery/` routes. Off, no other `DCB_DISCOVERY_*` setting is read | 9.0.0 | `security/discovery/DiscoveryConfig.java` |
| `DCB_DISCOVERY_AUDIENCE`                                                                                                                     | `dcb` | The audience a patron assertion must name. Give each deployment its own | 9.0.0 | `security/discovery/DiscoveryConfig.java` |
| `DCB_DISCOVERY_MAX_ASSERTION_LIFETIME`                                                                                                       | `PT2M` | DCB's cap on an assertion's validity, whatever its `exp` says | 9.0.0 | `security/discovery/DiscoveryConfig.java` |
| `DCB_DISCOVERY_TRUSTED_SERVICES_JSON`                                                                                                        | none: no service is trusted | The trusted discovery services, as one JSON array | 9.0.0 | `security/discovery/DiscoveryTrustedServices.java` |
| `DCB_DISCOVERY_MAX_LOANS`                                                                                                                    | `25` | The most loans one patron is told about. Each is one live read at the patron's library | 9.2.0 | `security/discovery/DiscoveryConfig.java` |
| `DCB_DISCOVERY_LOAN_LOOKUP_CONCURRENCY`                                                                                                      | `2` | How many of those reads run at once, all against the patron's library | 9.2.0 | `security/discovery/DiscoveryConfig.java` |
| `DCB_DISCOVERY_LOAN_LOOKUP_TIMEOUT`                                                                                                          | `8s` | The limit on those reads | 9.2.0 | `security/discovery/DiscoveryConfig.java` |
| `DCB_DISCOVERY_MAX_ACCOUNT_ROWS`                                                                                                             | `25` | The most loans, and the most charges, one account call returns | 9.2.0 | `security/discovery/DiscoveryConfig.java` |
| `DCB_DISCOVERY_ACCOUNT_LOOKUP_TIMEOUT`                                                                                                       | `8s` | The limit on an account call's read at the patron's library | 9.2.0 | `security/discovery/DiscoveryConfig.java` |
| `DCB_DISCOVERY_PATRON_RESOLVE_TIMEOUT`                                                                                                       | `8s` | The limit on one `POST /discovery/patrons/resolve` | 9.2.0 | `security/discovery/DiscoveryConfig.java` |
| `DCB_DISCOVERY_PATRON_RESOLVES_PER_MINUTE`                                                                                                   | `600` | How many institution sign-ins one trusted service may resolve in a minute | 9.2.0 | `security/discovery/DiscoveryConfig.java` |
| **Branding**                                                                                                                                 | | | | |
| `DCB_BRANDING_ASSETS_STORE`                                                                                                                  | `database` | Where uploaded brand images live: `database` (Postgres), or `none`, which removes the upload and serve routes so that brand fields accept absolute URLs only | 9.0.0 | `core/branding/BrandAssetProperties.java` |
| `DCB_BRANDING_ASSETS_MAX_BYTES`                                                                                                              | `2097152` (2 MB) | The largest upload. It also sets `micronaut.server.multipart.max-file-size`, up from the framework's 1 MB, so your ingress must accept bodies this size | 9.0.0 | `core/branding/BrandAssetProperties.java`; `dcb/src/main/resources/application.yml` |
| `DCB_BRANDING_ASSETS_MAX_DIMENSION`                                                                                                          | `4096` | The largest width or height, read from the image header before any pixel is decoded | 9.0.0 | `core/branding/BrandAssetProperties.java` |
| `DCB_BRANDING_ASSETS_ORPHAN_GRACE_PERIOD`                                                                                                    | `24h` | How long an uploaded image that no brand uses survives the sweep | 9.0.0 | `core/branding/BrandAssetProperties.java` |
| `DCB_BRANDING_ASSETS_PUBLIC_PATH_PREFIX`                                                                                                     | `/discovery/brand-assets/` | The path every stored image URL starts with. Changing it after images exist orphans every stored URL | 9.0.0 | `core/branding/BrandAssetProperties.java` |
| `DCB_BRANDING_ASSETS_SWEEP_INTERVAL`                                                                                                         | `24h` | How often the orphan sweep runs | 9.0.0 | `core/branding/BrandAssetSweep.java` |
| `DCB_BRANDING_THEME_NAMES`                                                                                                                   | `openRS,kInt` | The theme names a brand's default theme may be set to. Widen it only for a discovery front end that ships other themes | 9.0.0 | `core/branding/BrandingProperties.java` |
| **Insights**                                                                                                                                 | | | | |
| `DCB_INSIGHTS_ENABLED`                                                                                                                       | `true` | The endpoints under `/insights`. `false` removes `/patrons/requests/stats/*` as well | 9.0.0 | `dcb.insights` in `dcb/src/main/resources/application.yml`; `core/api/LegacyStatsController.java` |
| `DCB_INSIGHTS_COLLECTION_ANALYSIS_CACHE_TTL`                                                                                                 | `15m` | Every collection figure is stale by at most this | 9.0.0 | `dcb/src/main/resources/application.yml` |
| `DCB_INSIGHTS_COLLECTION_ANALYSIS_STATEMENT_TIMEOUT`                                                                                         | `10m` | Postgres cancels a whole-catalogue pass that runs longer | 9.2.0 | `dcb/src/main/resources/application.yml` |
| **Availability and the index**                                                                                                               | | | | |
| `DCB_INDEX_NUMBER_OF_REPLICAS`                                                                                                               | `1` | Replicas of the shared index, applied at every start ([§4](#replicas-are-applied-at-every-start)). Zero or more | 9.0.0 | `indexing/SharedIndexConfiguration.java` |
| `DCB_LIVE_AVAILABILITY_BATCH_MAX_CLUSTERS`                                                                                                   | `25` | The most clusters one anonymous `GET /items/availability/batch` may ask about | 9.2.0 | `core/api/LiveAvailabilityController.java` |
| `DCB_LIVE_AVAILABILITY_BATCH_CONCURRENCY`                                                                                                    | `4` | How many of those clusters are checked at once | 9.2.0 | `core/api/LiveAvailabilityController.java` |
| `DCB_JOBS_AVAILABILITY_CONCURRENCY_MAPPING_WRITES`                                                                                           | `3` | How many location-mapping writes the availability job runs at once | 9.2.0 | `availability/job/AvailabilityCheckJobConfig.java` |
| `DCB_JOBS_AVAILABILITY_MAPPED_RECHECK_GRACE_PERIOD`                                                                                          | `P60D`; was `P30D` | How long before the job rechecks a location code it mapped | default changed in 9.2.0 | `availability/job/AvailabilityCheckJobConfig.java` |
| `DCB_JOBS_AVAILABILITY_RECHECK_GRACE_PERIOD`                                                                                                 | `P14D`; was `P7D` | How long before it rechecks a location code it could not map | default changed in 9.2.0 | `availability/job/AvailabilityCheckJobConfig.java` |
| **Tracking**                                                                                                                                 | | | | |
| `DCB_TRACKING_TOO_LONG`                                                                                                                      | `56d` | How long a request may sit in one non-terminal status before automatic polling parks it. A manual "check for updates" resumes it and restarts the clock | 9.1.0 | `tracking/TooLongPolicy.java` |
| `DCB_TRACKING_SERVICE`                                                                                                                       | unset: the v3 tracking service | `v4` selects the lifecycle-evidence tracking service, which is opt-in and not yet the default | 9.0.0 | `tracking/TrackingServiceV4.java`; `tracking/MODULE.md` |
| **Platform**                                                                                                                                 | | | | |
| `DCB_R2DBC_LEGACY_TRANSACTION_OPERATIONS_ENABLED`                                                                                            | `false` | `true` and a restart restore the reactive transaction implementation from before Micronaut 5, while you investigate a regression. It is scheduled for removal | 9.2.0 | `dcb.r2dbc` in `dcb/src/main/resources/application.yml` |
| `DCB_FEDERATION_LOCK_PROVIDER`                                                                                                               | unset: Hazelcast | `local` holds DCB's named locks in one process's memory, so it suits a single instance only | 9.0.0 | `dcb/src/main/java/services/k_int/federation/local/LocalFederatedLockService.java` |
| `DCB_SOURCE_IMPORT_VANISHED_MAX_SHARE`                                                                                                       | `0.10` | A manual "vanished records" sweep that would remove more than this share of a Host LMS's bibs reports it and deletes nothing. Between 0 and 1 | 9.2.0 | `dataimport/SourceImportProperties.java`; `docs/polaris_notes.md` |
| `CAPABILITIES_*` (no `DCB_` prefix)                                                                                                          | placement `IMPERATIVE`, tracking `SCHEDULED_POLL` | Instance-wide defaults for how requests are placed and tracked. The defaults are how v8.71.0 worked: leave them unset | 9.0.0 | `request/lifecycle/LifecycleCapabilitiesConfiguration.java` |
| **DCB-to-DCB NCIP peering: leave unset unless your environment has OpenRS Appliance libraries**                                              | | | | |
| `DCB_PEER_AUTH_ENABLED`, `DCB_PEER_AUTH_NCIP_ENABLED`                                                                                        | `false`, `false` | Both `true` turn on token authentication between NCIP peers. See `docs/ncip-peer-authentication.md` | 9.0.0 | `request/lifecycle/ncip/peerauth/DcbPeerAuthProperties.java` |
| `DCB_PEER_AUTH_LOCAL_IDENTITY_*`                                                                                                             | `ID` `dcb`, `TOKEN_LIFETIME` `PT5M`; `ISSUER`, `SUBJECT`, `AUDIENCES`, `JWKS_URI`, `KEY_ID`, `PUBLIC_JWK` and `PRIVATE_JWK` none | DCB's own signing identity towards its peers. `PRIVATE_JWK` is a secret | 9.0.0 | `request/lifecycle/ncip/peerauth/DcbPeerAuthProperties.java` |
| `DCB_PEER_AUTH_REMOTE_JWKS_CONNECT_TIMEOUT`, `DCB_PEER_AUTH_REMOTE_JWKS_READ_TIMEOUT`                                                        | `PT2S`, `PT5S` | The limits on fetching a peer's keys | 9.0.0 | `request/lifecycle/ncip/peerauth/DcbPeerAuthProperties.java` |
| `dcb.peer-auth.trusted-peers`                                                                                                                | none | The peers, as a list: mount it as a file ([§7](#7-kubernetes-specifics)) | 9.0.0 | `request/lifecycle/ncip/peerauth/DcbPeerAuthProperties.java` |
| `DCB_NCIP_SYSTEM_ID`, `DCB_NCIP_AGENCY_ID`                                                                                                   | none | DCB's own NCIP identity. A Host LMS that places or tracks through NCIP fails without them, on first use | 9.0.0 | `request/lifecycle/ncip/NcipIdentityConfiguration.java` |
| `DCB_PROFILE_REGISTRATION_*`                                                                                                                 | `NODE_NAME` `DCB`, `INVITATION_TTL` `30m`, `SYNC_INTERVAL` `15m`, `SYNC_POLL_INTERVAL` `1m`, `ALLOW_HTTP` and `ALLOW_PRIVATE_ADDRESSES` `false`; `PUBLIC_BASE_URL` none | Registering this DCB with a peer, and keeping that membership in step | 9.0.0 | `request/lifecycle/ncip/profile/application/DcbProfileRegistrationProperties.java` |

Not variables, but new: the `AWAITING_RETURN_TO_SUPPLIER` polling interval (`1h`, under
`dcb.polling.durations` in `application.yml`), and the multipart limit that
`DCB_BRANDING_ASSETS_MAX_BYTES` sets.

### Settings no longer read

| Setting | Last read by | Now |
|---|---|---|
| `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_REQUEST_WINDOW` | v8.71.0 | Ignored, with a warning at startup. Remove it; `..._ACTIVE_WINDOW` is the bound if you need one |
| `DCB_IDENTITY_PROVIDER_TYPE`, `_BASE_URL`, `_REALM`, `_CLIENT_ID`, `_CLIENT_SECRET`, `_PROJECT_ID` | v9.1.0 | **Stops startup**, even empty. Use the `DCB_IDENTITY_PROVIDER_KEYCLOAK_*` names |
| `DCB_INSIGHTS_COLLECTION_ANALYSIS_CONCURRENCY`, `..._MAX_WAIT` | v9.0.0 | Ignored. Collection analysis runs as shared, bounded passes |
| A YAML `dcb.discovery.trusted-services` list | never: it was documented and bound nothing | **Stops startup** with a message naming `trusted-services-json` |

### New in 9.2.0: absent means off, blank stops startup

A setting you leave out takes its default, or leaves its feature off. A setting that is present with
an empty value, the usual shape of a templated value nobody filled in, stops DCB at startup with a
message naming it, where it used to mean "off". A feature that is off is not checked at all, so a
blank setting it owns does not stop startup. **Remove any of these you set to an empty value.**

| Setting | Left out | Blank stops startup | Source |
|---|---|---|---|
| `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT` | 55 seconds, and DCB logs `Using default preflight check timeout value of PT55S` | always; so does an unreadable or non-positive duration | `dcb/src/main/java/org/olf/dcb/request/fulfilment/PreflightChecksTimeout.java` |
| `DCB_REQUESTS_STAFF_CLIENT_IDS` | `dcb-admin,dcb-admin-for-libraries` | always; so does a list that names no client | `dcb/src/main/java/org/olf/dcb/security/StaffClients.java` |
| `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ACTIVE_WINDOW` | the earlier request's whole active life | when the duplicate-request check is on | `dcb/src/main/java/org/olf/dcb/request/fulfilment/PreventDuplicateRequestsPreFlightCheck.java` |
| `DCB_SECURITY_ADMIN_UI_CLIENT_ID` | the DCB Admin access bar is off | always: set, it turns the bar on | `dcb/src/main/java/org/olf/dcb/security/AdminUiAccessConfig.java` |
| `DCB_DISCOVERY_AUDIENCE` | `dcb` | when discovery is on | `dcb/src/main/java/org/olf/dcb/security/discovery/DiscoveryConfig.java` |
| `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` | no trusted service | when discovery is on | `dcb/src/main/java/org/olf/dcb/security/discovery/DiscoveryTrustedServices.java` |

The message depends on the setting: `is set but blank`, `must not be blank`, `which is not a
duration` or `names no client`.

### New in 9.2.0: the identity-provider settings

DCB Admin for Libraries accounts can be provisioned from DCB Admin (since v9.1.0) through an
identity provider. Only Keycloak is supported, and it is configured as one block: the four
`DCB_IDENTITY_PROVIDER_KEYCLOAK_*` settings in the table above. Once any of them is present, all
four are required and none may be blank; the secret has no default.

Leave all four out and provisioning is off; the GraphQL mutations answer that it is not configured.
**Any other key under `dcb.identity-provider` stops startup** with `dcb.identity-provider "<key>"
is not supported`. That includes `DCB_IDENTITY_PROVIDER_TYPE` and the other names v9.1.0 read,
even when they are empty: remove them.

**We recommend leaving this off until the UX is in place.**

Source: `dcb/src/main/java/org/olf/dcb/security/provisioning/IdentityProviderConfig.java`,
`.../keycloak/KeycloakConfig.java`, `.../IdentityProviderConfigCheck.java`.

### Placement

**The preflight timeout, before you upgrade:** check that every caller that places requests, and
your ingress, waits longer than the limit. A caller that gives up first shows its own error, and
DCB may still finish and place the request. If you have not measured how long placements take, set
it well above the default for the first deploy (for example `PT5M`), then lower it once the
`dcb.preflight.duration` metric shows the slowest placements. Each placement also logs one line,
`Preflight checks <outcome> in <n> ms`.

**The supplier preflight, after you upgrade:** leave it off for the first start. Within a few
minutes every Host LMS has a recorded ping (`HostLms.lastPing` in GraphQL). Correct and save any
that read REFUSED or MISCONFIGURED, then turn the preflight on and restart. In v8.71.0 the FOLIO
ping did not send a key, so this is the first credential check some of your systems have had.

**The duplicate-request check** (`DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ENABLED`, off by
default) now refuses a repeat for as long as the earlier request is active, not for a fixed time.

### The DCB Admin access bar (since v9.1.0, off unless configured)

Set `DCB_SECURITY_ADMIN_UI_CLIENT_ID` and DCB refuses a token issued to DCB Admin's client unless
it carries a consortium role. DCB logs at startup whether the bar is off, or which mode it is in for
which client. Run `WARN` first and read the log before switching to `ENFORCE`.

**Changed in 9.2.0:** neither setting has a value in `application.yml` any more; the defaults are in
code.

Source: `dcb/src/main/java/org/olf/dcb/security/AdminUiAccessConfig.java`,
`.../AdminUiAccessPolicy.java`.

### Discovery

Discovery systems reach DCB by two routes, configured differently: see
[§6](#discovery-systems-two-routes).

### Tracking, scheduling and alarms

| Change | Notes | Source |
|---|---|---|
| **Scheduled-task skip names** | `DCB_SCHEDULED_TASKS_SKIPPED` matches the declaring class's simple name. Tracking is now `TrackingScheduler`, so `TrackingServiceV3` no longer skips it | `dcb/src/main/java/io/micronaut/scheduling/processor/AppTaskAwareScheduledMethodProcessor.java`; `dcb/src/main/java/org/olf/dcb/tracking/TrackingScheduler.java` |
| **`AWAITING_RETURN_TO_SUPPLIER`** | A new status, polled hourly: a request cancelled after its item shipped waits there until the supplier reports the item back | `dcb.polling.durations` in `application.yml`; `docs/cancellation-handling.md` |
| **Alarm webhooks** | `DCB_GLOBAL_NOTIFICATIONS_*` targets are now posted to; in v8.71.0 they never were. Expect a burst of "deactivated" posts a few minutes after first start, as old ping failures clear. The target, URL included, is logged at INFO: treat the logs as holding the webhook secret | `dcb/src/main/java/org/olf/dcb/core/svc/AlarmsService.java` |

### Host LMS records

Host LMS configuration is validated when it is saved, and this release refuses some values earlier
releases stored. A stored record the validator refuses cannot be edited until those values are
corrected, because an edit replaces the whole configuration. Check, before you upgrade:

- **Polaris:** each required item and service value is present and a whole number;
  `borrower-lending-flow` is not set (it is no longer supported, and a stored one fails placement);
  `shelfLocationPolicyMap` values are ones the validator accepts (`REFERENCE` is not one;
  `REFERENCE_ONLY` is).
- **Polaris:** a null or unknown `item-agency-resolution-method` now resolves items by shelf
  location.
- **FOLIO:** with `record-syntax` or `metadata-prefix` unset, harvesting uses the published defaults.

Source: `dcb/src/main/java/org/olf/dcb/graphql/validation/HostLmsConfigValidator.java`;
`dcb/src/main/java/org/olf/dcb/core/interaction/polaris/PolarisConfig.java`;
`dcb/src/main/java/org/olf/dcb/graphql/UpdateHostLmsDataFetcher.java` (`clientConfig`, when sent,
replaces the stored configuration, as `docs/polaris_notes.md` notes).

---

## 6. Security changes that need coordination

### `/patrons/requests/**` needs an explicit role (since v9.0.0)

At v8.71.0, `PatronRequestController` defaulted to `IS_AUTHENTICATED`, so several methods were open
to any principal the realm would authenticate. Every method now has an explicit role set:

| Endpoint | Roles |
|---|---|
| Class default, which covers `/{id}/update` (and an ownership check) | `CONSORTIUM_ADMIN`, `ADMIN`, `LIBRARY_ADMIN`, `INTERNAL_API` |
| `/place`, `/place/expeditedCheckout`, `/place/walkup` | the above plus `LIBRARY_READ_ONLY` |
| `/{id}/transition/cleanup`, `/{id}/transition/confirm-returned` (new in 9.2.0) | `CONSORTIUM_ADMIN`, `ADMIN`, `LIBRARY_ADMIN`, with an ownership check |
| `/{id}/rollback` | `CONSORTIUM_ADMIN`, `ADMIN` |
| `GET /{hostLmsCode}` | `CONSORTIUM_ADMIN`, `ADMIN` |
| `GET /patrons/{hostLmsCode}/requests` (legacy) and `GET /{?pageable*}` (legacy) | `ADMIN` only |

A missing token gets 400, and a token without the role gets 401. Service-to-service callers want
`INTERNAL_API`.

Source: `@Secured` on each method of `dcb/src/main/java/org/olf/dcb/core/api/PatronRequestController.java`;
`dcb/src/main/java/org/olf/dcb/security/RoleNames.java` (`ADMINISTRATOR = "ADMIN"`);
`dcb/src/main/java/org/olf/dcb/security/DcbAuthorizationExceptionHandler.java`. Every route's
security is checked by `ApiSecurityArchitectureTests`.

### New in 9.2.0: the patron lookup needs a role

`POST /v2/patron/auth/lookup` now needs one of `ADMIN`, `INTERNAL_API`, `CONSORTIUM_ADMIN`,
`LIBRARY_ADMIN` or `LIBRARY_READ_ONLY`. At v8.71.0 it was anonymous. Discovery layers use it to look
up a patron already authenticated elsewhere, for example through SAML. The credential check,
`POST /v2/patron/auth`, stays anonymous.

Source: `@Secured` on the class and on the lookup method of
`dcb/src/main/java/org/olf/dcb/core/api/PatronAuthV2Controller.java`.

### Discovery systems: two routes

A discovery system reaches DCB by one of two routes. A deployment can serve both at once: they use
different credentials and different routes, and nothing configured for one affects the other.

| | Legacy route | New route |
|---|---|---|
| Used by | EBSCO Locate | any other discovery service |
| Credential | a Keycloak service account holding `ADMIN` | a confidential client holding `DISCOVERY_SERVICE` |
| Which patron | named by the caller in the request | a signed patron assertion, which DCB verifies |
| Routes | the staff API: `/patrons/requests/**`, `/v2/patron/auth/lookup` and others | `/discovery/**` |
| DCB settings | none | `DCB_DISCOVERY_*` ([§5](#every-setting-new-since-v8710)) |

Source: `docs/discovery-service-approach.md` §2 (the new route) and §8 (the legacy route).

#### Legacy route: EBSCO Locate

Locate calls dcb-service directly with a Keycloak service account holding `ADMIN`, and names the
patron in the request body. The Locate team confirmed on 20 October 2026 that its account holds
`ADMIN`. No DCB setting is involved, and `DCB_DISCOVERY_ENABLED` has no effect on this route. The
routes Locate uses are listed in `docs/discovery-service-approach.md` §8.3. Two of them changed
after v8.71.0:

| Route | Change | Since |
|---|---|---|
| `POST /v2/patron/auth/lookup` | Needs one of `ADMIN`, `INTERNAL_API`, `CONSORTIUM_ADMIN`, `LIBRARY_ADMIN` or `LIBRARY_READ_ONLY`; it was anonymous. `ADMIN` qualifies, so Locate must send its token on this call | 9.2.0 |
| `GET /patrons/requests/patrons/{hostLmsCode}/requests?barcode=` | `ADMIN` only, and marked `LEGACY` in the source; v8.71.0 also let `CONSORTIUM_ADMIN` in. The barcode is matched exactly: v8.71.0 matched it as a substring, so a partial or normalised barcode that used to find a patron's requests now finds none | 9.0.0 |

`GET /patrons/requests`, the request browse, was `ADMIN` only at v8.71.0 and is now marked `LEGACY`.
`POST /patrons/requests/place` keeps `ADMIN` in its now explicit role set, and the credential
checks, `POST /patron/auth` and `POST /v2/patron/auth`, stay anonymous.

Before you upgrade:

1. In your access logs, count `POST /v2/patron/auth/lookup` requests that carry no `Authorization`
   header. It must be 0.
2. On a test environment, sign in through Locate, place one request and open "my requests".

Source: `@Secured` in `dcb/src/main/java/org/olf/dcb/core/api/PatronAuthV2Controller.java` and
`.../PatronRequestController.java`; `findActiveRequestsForPatronByBarcode` and
`findAllRequestsForPatronByBarcode` in `dcb/src/main/java/org/olf/dcb/storage/PatronRequestRepository.java`.

#### New route: discovery services with patron assertions

The discovery service holds a confidential Keycloak client with the `DISCOVERY_SERVICE` role. For
each call it also sends a short-lived patron assertion, `X-OpenRS-Patron-Assertion`, a JWT it
signs for the patron who signed in, and DCB acts only as that patron. The service signs a patron in
first, through the anonymous `POST /patron/auth` or `POST /v2/patron/auth` for a library card, or
`POST /discovery/patrons/resolve` for an institution sign-in. The contract for the vendor is
`docs/discovery-service-approach.md` §6, and the onboarding mechanics are §7.

To turn it on:

1. Create the service's Keycloak client, confidential and holding `DISCOVERY_SERVICE`, and give its
   credential to the vendor.
2. Set `DCB_DISCOVERY_ENABLED=true`, set `DCB_DISCOVERY_AUDIENCE` to a value of this deployment's
   own, and give `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` one entry per service:

   ```
   DCB_DISCOVERY_TRUSTED_SERVICES_JSON=[{"service-id":"your-discovery-service","issuer":"https://discovery.example.org","jwks-uri":"https://discovery.example.org/.well-known/jwks.json"}]
   ```

   DCB refuses a call whose bearer was not issued to the entry's client. That client is the
   `service-id` unless the entry sets `client-id`, so set `client-id` where the two differ.
3. Restart, and **check the startup log** for `Discovery patron assertions accepted from <n>
   trusted service(s): [...]`. If `n` is 0, every patron assertion is refused with "JWT issuer is
   not trusted".

The other `DCB_DISCOVERY_*` settings bound what one patron call may cost at the patron's library,
and their defaults suit most deployments ([§5](#every-setting-new-since-v8710)).

With discovery off there is no discovery configuration: no `DCB_DISCOVERY_*` setting is read or
checked, and the patron-scoped routes, `/discovery/patrons/resolve` included, do not exist. A call to
one is refused like any route without a rule, `401` with a token and `400` without. The anonymous
`/discovery/` routes, such as `/discovery/libraries` and `/discovery/brand-assets/{key}`, are there
either way.

**Changed in 9.2.0:** the defaults are in code rather than `application.yml`, and with discovery on
every discovery setting is checked at startup: a blank audience, a non-positive duration or a count
below 1 stops DCB, where earlier releases replaced most such values with the default without a
word. The trusted services come only from the JSON form, in an environment variable or as
`dcb.discovery.trusted-services-json` in a mounted file, because a list of objects cannot be
expressed as indexed environment variables. A YAML `dcb.discovery.trusted-services` list stops
startup.

Source: `dcb/src/main/java/org/olf/dcb/security/discovery/DiscoveryConfig.java`,
`.../DiscoveryTrustedServices.java`, `.../DiscoveryTrustedServiceStore.java`,
`.../PatronAssertionVerifier.java`.

### Library staff need their agency in the access token (since v9.0.0)

A `LIBRARY_ADMIN` or `LIBRARY_READ_ONLY` token must carry the library's DCB agency code, in `code`
or `agencyCodes`, in the **access** token, not only the ID token. Without it, DCB Admin for
Libraries shows empty grids and gets 403s. If a library staff reports issues like this, **check their Keycloak account first to see if `code` is set to a valid DCB agency code**.


Source: `dcb/src/main/java/org/olf/dcb/security/AgencyClaims.java`, `.../CallerScope.java`.

### Anonymous routes: rate-limit them at your ingress

These routes are anonymous by design, and their bounds in DCB are their only protection inside the
application:

| Route | Why anonymous | Bound in DCB |
|---|---|---|
| `GET /items/availability`, `/items/availability-v2` | discovery layers ask before anyone signs in | one cluster per call; a Host LMS that times out is reported in `errors[]` |
| `GET /items/availability/batch` | the same, for a page of results | 25 clusters, 4 at a time |
| `/discovery/brand-assets/{key}` | brand images render before sign-in. Responses are `Cache-Control: public, max-age=31536000, immutable`: let your CDN cache them | none needed |
| `POST /v2/patron/auth` | the credential check | none |
| `POST /ncip/v2_02` | NCIP peers | peer authentication, whose default mode is `INSECURE` |

**At your ingress:** If you encounter issues, consider rate-limiting `/items/availability*` (count a batch as its number of clusters),
`/discovery/*` and `/v2/patron/auth` per client IP. 

Source: `dcb/src/main/java/org/olf/dcb/core/api/LiveAvailabilityController.java`;
`dcb/src/main/java/org/olf/dcb/request/lifecycle/ncip/NcipController.java`;
`dcb/src/main/java/org/olf/dcb/request/lifecycle/ncip/peerauth/NcipPeerAuthProfile.java`.

### Logging

- **New in 9.2.0: patron barcodes are out of the shipped logging.** v9.0.0 and v9.1.0 set DEBUG on
  five loggers that wrote them (`org.olf.dcb.request.workflow`, `ConsortialFolioHostLmsClient`,
  `MarcIngestSource`, `SourceRecordImportJob` and `OaiPmhIngestSource`). This release's
  `logback.xml` leaves them at the default, and the placement parameters, the FOLIO virtual-patron
  lookup and the borrower-loan error lines no longer put a barcode in a log line. If you replaced the
  shipped file to avoid this, you can go back to it. Other DEBUG lines still carry patron data (the
  Sierra client logs a whole patron record, barcodes and names included, when it creates a virtual
  patron), so a logger you turn up to DEBUG while troubleshooting writes it again: turn it back down
  afterwards. Source: `dcb/src/main/resources/logback.xml`;
  `dcb/src/main/java/org/olf/dcb/core/interaction/PlaceHoldRequestParameters.java`.
- Patron credentials (since v9.0.0) and Host LMS credentials (since v9.1.0) are not logged.
- **New in 9.2.0:** every inbound HTTP request gets a request id. Each log line carries it in
  brackets after the level, empty outside a request, and it is returned as `X-Request-Id`. Update
  your log parsers. See `docs/request-id.md`.
- **A file appender writes inside the container** as well as to the console:
  `logs/dcb-service-logs-<start>.log`, rolling at 250 MB. Make sure the working directory is
  ephemeral and size-limited.

Source: `dcb/src/main/resources/logback.xml`.

---

## 7. Kubernetes specifics

**Stop the old pods first.** Use `strategy: type: Recreate` for the upgrade, or scale to 0 before
you deploy. A `RollingUpdate` keeps v8.71.0 pods serving while the migrations drop columns they read
([§2](#2-database-migrations)).

**Startup budget.** Use a `startupProbe` with enough `failureThreshold` that a slow migration does
not get the pod killed and restarted mid-migration:

```yaml
startupProbe:
  httpGet: { path: /health, port: 8080 }
  periodSeconds: 10
  failureThreshold: 180   # 30 minutes; tune from your own timed dry run
livenessProbe:
  httpGet: { path: /health, port: 8080 }
  periodSeconds: 30
```

**Start one replica,** let it go ready, then scale up.

**Disk.** `V9_0_005` adds over 100 MB of index on a 5M-row audit table, and the brand asset store
adds database growth. Check headroom on the volume and on your backup target before you start.

**Mount nested configuration as files:** `dcb.peer-auth`, and the discovery trusted services as
`trusted-services-json` ([§6](#new-route-discovery-services-with-patron-assertions)).

## 8. AWS ECS/Fargate specifics

**Scale to 0 first.** Then deploy one task with `minimumHealthyPercent: 0`, and scale out once it
is healthy.

**Health check grace period.** The task-level equivalent of the startup probe. Raise it above your
rehearsed migration time for the upgrade, for example:

```
healthCheckGracePeriodSeconds: 1800
```

**Configuration in variables.** A Fargate task definition cannot mount a file, so use
`DCB_DISCOVERY_TRUSTED_SERVICES_JSON`.

**Secrets.** The NCIP peer-auth private JWK, `DCB_IDENTITY_PROVIDER_KEYCLOAK_CLIENT_SECRET` and
any webhook URL come from Secrets Manager or SSM Parameter Store, through `secrets` in the task
definition, never `environment`.

**Hazelcast.** DCB's default `hazelcast.yaml` uses multicast discovery, which does not work on AWS,
so each task is a single-member cluster and its locks are per task. That matters for the clustering
sweep ([§3](#3-the-clustering-sweep)). This is unchanged from v8.71.0.

---

## 9. Rollback

**To v8.71.0, restore the database** from the backup you took, and roll the image back with it.
Once `V9_0_004` has run, v8.71.0 cannot read the `consortium` table. In practice the restore point
is "before the first v9.2.0 task started".

If you must start v8.71.0 against a database v9.2.0 has written to, run this first. v8.71.0 has no
`AWAITING_RETURN_TO_SUPPLIER` status:

```sql
update patron_request set status_code = 'RETURN_TRANSIT' where status_code = 'AWAITING_RETURN_TO_SUPPLIER';
```

Audit rows that name the status remain unreadable to v8.71.0.

Source: `dcb/src/main/java/org/olf/dcb/core/model/PatronRequest.java` (`Status`).

**Without a restore,** each of these is undone by a restart with one setting:

| To undo | Set |
|---|---|
| The new transaction implementation | `DCB_R2DBC_LEGACY_TRANSACTION_OPERATIONS_ENABLED=true` |
| Placements refused for time | raise `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT` |
| The supplier preflight | `DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED=false` |
| The duplicate-request check | `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ENABLED=false` |
| The DCB Admin access bar refusing callers | `DCB_SECURITY_ADMIN_UI_MODE=WARN`, or remove `DCB_SECURITY_ADMIN_UI_CLIENT_ID` |
| Insights load | `DCB_INSIGHTS_ENABLED=false` |
| Brand uploads | `DCB_BRANDING_ASSETS_STORE=none` |
| Replica churn | `DCB_INDEX_NUMBER_OF_REPLICAS=<your current count>` |
| A misbehaving scheduled job | `DCB_SCHEDULED_TASKS_SKIPPED=<its class name>` |
| Webhook noise | remove `DCB_GLOBAL_NOTIFICATIONS_*` |

Log lines already written and webhook posts already sent cannot be undone.

If you upgraded Elasticsearch to 9.x for this release, rolling DCB back to an 8.x-era client does
not work against the upgraded cluster.

---

## 10. New operator surfaces

None needs configuration.

- **Host LMS pings** prove credentials, are recorded per Host LMS (`HostLms.lastPing`), alarm only
  on failure, and run when a Host LMS is saved. Saving a Polaris Host LMS also clears its token
  cache, so a changed password takes effect at once.
- **Placement metrics:** `dcb.preflight.duration` (tag `outcome`) and `dcb.preflight.check.duration`
  (tag `check`).
- **Stalled harvest detection** (since v9.0.0): a watchdog, and `GET /admin/sourceImport/status`,
  `POST /admin/sourceImport/{hostLmsCode}/reconcile` and
  `POST /admin/sourceImport/{hostLmsCode}/resetCheckpoint`.
- **Search backend reporting:** `curl -s http://<dcb-host>:8080/info | jq '.dcb.index.backend'`.
- **Event meters** (since v9.0.0): `dcb.stats.events` (counter) and `dcb.stats.timed` (timer),
  replacing an in-heap map that grew without bound.
- **Request statistics:** `GET /patrons/requests/stats/top-requestors` and `/top-requested-titles`
  still answer, through a deprecated shim over Insights that logs at WARN when called.

Source: `dcb/src/main/java/org/olf/dcb/core/api/AdminController.java`;
`dcb/src/main/java/org/olf/dcb/core/api/LegacyStatsController.java`.

---

## 11. What has not changed

| | Status |
|---|---|
| Ports | 8080 |
| Health, info and Prometheus endpoints | Same paths. Meters are added, none renamed |
| Image tag scheme | `<repo>:<tag>`, native as `<repo>:native-<tag>` |
| Hazelcast | Pinned at 5.4.0 |
| OpenSearch server requirements | `analysis-icu` still required |
| Keycloak roles | None added or removed for an existing deployment. What changed is which roles DCB requires ([§6](#6-security-changes-that-need-coordination)) |
| `GET /patrons/requests` | Still present, still `ADMIN` only |
| Environment variables | **Changed:** see [§5](#5-configuration) for the variables now refused when blank, the identity-provider keys, the Insights keys no longer read, `…REQUEST_WINDOW`, the YAML trusted-services list, and the scheduled-task skip names |

---

## 12. Upgrade procedure

**Before you start**

1. **Check client credentials** against [§6](#6-security-changes-that-need-coordination): every
   caller of `/patrons/requests/**`, and that EBSCO Locate sends its token on
   `/v2/patron/auth/lookup` ([§6](#legacy-route-ebsco-locate)).
2. **Check library staff access tokens** carry `code` or `agencyCodes`.
3. **Diff your task definitions or ConfigMaps against [§5](#every-setting-new-since-v8710).** Remove every
   variable set to an empty value, any `DCB_IDENTITY_PROVIDER_*` other than `..._KEYCLOAK_*`,
   `…REQUEST_WINDOW` and any YAML `dcb.discovery.trusted-services` list. Set
   `DCB_REQUESTS_STAFF_CLIENT_IDS` if your admin clients are not called `dcb-admin` and
   `dcb-admin-for-libraries`. Map `DCB_SCHEDULED_TASKS_SKIPPED` names.
4. **Export your index mapping** and compare it with `mappings-2.json`
   ([§4](#4-the-shared-index)). If it conflicts, plan a new `DCB_INDEX_NAME`.
5. **Run the clustering query** from [§3](#3-the-clustering-sweep).
6. **Check stored Host LMS records** ([§5](#host-lms-records)).
7. **If you run Elasticsearch, confirm it is 9.x.** If you run OpenSearch, confirm `analysis-icu`.
8. **Configure the ingress:** rate limits and blocks from [§6](#anonymous-routes-rate-limit-them-at-your-ingress),
   a body limit of at least 2 MB, and an idle timeout above the placement limit.
9. **OPTIONAL - Time the migrations on a restored copy of production.** It gives you the number for step 13.
10. **Clean up requests in ERROR** with consortium staff. A request in ERROR still holds its copy:
    v9.2.0 will not choose that copy for the same borrowing library again until the request is
    finished.

**In the window**

11. **Take a backup you have tested restoring.**
12. **Stop every v8.71.0 task or pod.**
13. **Deploy one v9.2.0 task or pod,** with a startup budget above the time from step 9, and:
    - `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT=PT5M` unless you have measured placements;
    - `DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED=false`;
    - `DCB_INDEX_NUMBER_OF_REPLICAS=<your current count>`;
    - a new `DCB_INDEX_NAME` if step 4 found a conflict.
14. **Watch the log:** Flyway applies `V8_71_001` through `V9_0_017`, then `Initialized index`, the
    search backend line, and, if discovery is on, `Discovery patron assertions accepted from <n>
    trusted service(s)`. Afterwards,
    `select version from flyway_schema_history order by installed_rank desc limit 1` returns
    `9.0.017`.
15. **If you used a new index name,** call `POST /admin/reindex`.

**After**

16. **Within minutes, read every Host LMS's last ping.** Correct and save any that read REFUSED or
    MISCONFIGURED.
17. **If a clustering sweep is running, let it finish before scaling out,** on Fargate especially.
18. **Scale back up,** and lower the startup budget again.
19. **After a few weeks,** set `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT` from the
    `dcb.preflight.duration` 99.9th percentile, and set `DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED=false`.

---

## 13. Troubleshooting

| Symptom | Cause |
|---|---|
| Startup fails naming a setting: `is set but blank`, `must not be blank`, `which is not a duration` or `names no client` | A variable from the table in [§5](#new-in-920-absent-means-off-blank-stops-startup) is present with no usable value. Remove it, or set it properly |
| Startup fails: `dcb.discovery.trusted-services is not read` | A YAML trusted-services list. Give the same services as `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` ([§6](#new-route-discovery-services-with-patron-assertions)) |
| Startup fails: `dcb.identity-provider "type" is not supported` | `DCB_IDENTITY_PROVIDER_TYPE`, or another key under `dcb.identity-provider` other than `keycloak`, is set. Remove it |
| Startup fails while initialising the index, citing a mapping or analyzer conflict | The live index holds a field another way. Deploy with a new `DCB_INDEX_NAME`, then `POST /admin/reindex` ([§4](#4-the-shared-index)) |
| `status: 400 … Accept version must be … found 9` | Elasticsearch 8.x behind a 9.x client: upgrade ES to 9.x |
| `status: 401 … [es/indices.exists]` | Index credentials not reaching the client: check `DCB_INDEX_USERNAME` / `DCB_INDEX_PASSWORD` |
| Pod restarts repeatedly, logs stop mid-migration | Liveness probe firing during migration: no `startupProbe`, or too small a `failureThreshold` |
| Placements fail on some tasks during the upgrade | A v8.71.0 task is still running against the migrated database. Stop it ([§2](#2-database-migrations)) |
| Sustained database and search load, `Processed N clusters with oudated bibs` in the logs | The clustering sweep ([§3](#3-the-clustering-sweep)) |
| Tracking still runs on a task meant to skip it | `DCB_SCHEDULED_TASKS_SKIPPED` names `TrackingServiceV3`; use `TrackingScheduler` |
| Integrations get 400 or 401 on `/patrons/requests/**` or `/v2/patron/auth/lookup` | No token (400) or no role (401). Grant a role from [§6](#6-security-changes-that-need-coordination) |
| Library staff see empty grids in DCB Admin for Libraries | Their access token carries no `code` or `agencyCodes` |
| Placements refused with `PREFLIGHT_CHECKS_TIMED_OUT` | Checks ran past `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT`. Raise it, then read `dcb.preflight.duration` |
| Every discovery patron call refused with `401` and no `code` (`400` without a token) | Discovery is off: `DCB_DISCOVERY_ENABLED` is not `true`, so the routes do not exist |
| Every discovery patron call refused, "JWT issuer is not trusted" in the log | No trusted service loaded. Check the `Discovery patron assertions accepted from <n>` startup line, and use the JSON form ([§6](#new-route-discovery-services-with-patron-assertions)) |
| Uploads rejected at just over 1 MB | Something overrides `micronaut.server.multipart.max-file-size`, or the ingress body limit is below 2 MB |
| Search index yellow after upgrade | `DCB_INDEX_NUMBER_OF_REPLICAS` defaults to `1` and is applied at every start. Set `0` on a single-node cluster |
| A burst of alarm posts a few minutes after start | Old ping failures clearing, now that webhooks post |
| `CP subsystem is a licensed feature` | Hazelcast newer than 5.4.0 on the classpath. It should not happen from our image; report it |

---


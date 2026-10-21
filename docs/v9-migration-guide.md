# DCB v9.2.0: upgrade guide for hosting providers migrating from v8

This guide is for anyone who runs DCB from the published image on Kubernetes or AWS ECS/Fargate.
It covers every change between **v8.71.0** (23 July 2026) and **v9.2.0**, including the changes
released in v9.0.0 and v9.1.0. It is not a guide for upgrading from v9.0.x or v9.1.x.

It describes the `v9.2.0` tag (`005b49a7e`). Every behaviour stated here names the file that
implements it, under **Source**, so you can check it against the code you deploy. Paths are
relative to the repository root.

**How to read it**

- **Required** means the upgrade fails, or part of DCB stops working, if you do not do it.
- **Advised** means the upgrade works without it, but we recommend it.
- An **instance** is one running copy of DCB: a Kubernetes pod or an ECS task.
- A **Host LMS** is a library management system that DCB connects to. A **ping** is DCB's check
  that it can reach a Host LMS and that its credentials work.
- The **preflight checks** are the checks DCB runs on a request before it places it.

| Section                                                                        | What it covers                                         |
|--------------------------------------------------------------------------------|--------------------------------------------------------|
| [1. What you must do](#1-what-you-must-do)                                     | The upgrade, as a checklist                            |
| [2. Settings and behaviour changes](#2-settings-and-behaviour-changes)         | What to set, and what will behave differently          |
| [3. Adding a trusted discovery service](#3-adding-a-trusted-discovery-service) | Connecting a discovery service other than EBSCO Locate |
| [4. Troubleshooting](#4-troubleshooting)                                       | Symptoms, causes and fixes, and how to undo a change   |
| [Appendix A](#appendix-a-database-migrations-and-rollback)                     | Database migrations and rollback                       |
| [Appendix B](#appendix-b-every-setting-new-since-v8710)                        | Every setting new since v8.71.0                        |
| [Appendix C](#appendix-c-roles-and-routes)                                     | Which roles each route needs, and the anonymous routes |

Companion documents, all in this repository:

| Topic                                                    | Document                                      |
|----------------------------------------------------------|-----------------------------------------------|
| Micronaut 5 / JDK 25, image bases, Elasticsearch         | `docs/micronaut-5-hosting-notes.md`           |
| Branding, the asset store, capacity                      | `docs/branding.md`                            |
| Discovery services: the design, and onboarding one       | `docs/discovery-service-approach.md`          |
| Discovery services: the contract a vendor builds against | `docs/discovery-service-contract.md`          |
| NCIP peer authentication                                 | `docs/ncip-peer-authentication.md`            |
| Insights endpoints                                       | `docs/insights.md`                            |
| The supplier preflight                                   | `docs/operational/supplier-preflight.md`      |
| The duplicate-request check                              | `docs/operational/duplicate-request-check.md` |
| Notes and tags on requests                               | `docs/operational/patron-request-notes.md`    |
| Request ids in logs                                      | `docs/request-id.md`                          |
| Cancelled while the item is out                          | `docs/cancellation-handling.md`               |
| The shared index                                         | `docs/shared-index.md`                        |

---

## 1. What you must do

DCB v9.2.0 changes the database the first time it starts. This means:

- **You cannot run v8.71.0 and v9.2.0 at the same time.** Stop every v8.71.0 instance first.
  `V9_0_004` removes six `consortium` columns that v8.71.0 reads, so placement fails on any old
  instance that is still running.
- **You cannot go back to v8.71.0 without restoring the database** from a backup.
- **The first start takes longer than a normal start.** Nineteen migrations run, and two of them
  lock the `patron_request` table while they run. How long depends on the size of your database.

Details: [Appendix A](#appendix-a-database-migrations-and-rollback).

### Before upgrade day

- [ ] **Required.** If you use Elasticsearch, upgrade it to 9.x, with the `analysis-icu` plugin.
  DCB v9.2.0 cannot talk to Elasticsearch 8.x. OpenSearch needs no change. *(Source:
  `docs/micronaut-5-hosting-notes.md`)*
- [ ] **Required.** In your task definitions or ConfigMaps, remove every DCB variable whose value is
  empty. Several of them now stop DCB from starting when empty
  ([§2.4](#24-blank-values-stop-startup)).
- [ ] **Advised.** Remove `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_REQUEST_WINDOW`. DCB
  ignores it and logs a warning ([§2.3](#23-settings-no-longer-read)).
- [ ] **Required.** Every system that calls DCB's request routes (`/patrons/requests/**`) or
  `POST /v2/patron/auth/lookup` must send a token with a role. Discovery systems use the lookup to
  find a patron who signed in somewhere else, for example through SAML. EBSCO Locate's account
  already has the role it needs, so no change should be needed for it. To be sure, check your
  access logs: `POST /v2/patron/auth/lookup` requests with no `Authorization` header must be 0
  ([Appendix C](#ebsco-locate)).
- [ ] **Required, if libraries use DCB Admin for Libraries.** Library staff accounts must carry their
  library's DCB agency code in the `code` or `agencyCodes` claim of the **access** token. Without it, DCB Admin for Libraries shows empty
  lists. Check them with `scripts/check-library-staff-agency-claims.sh`, or by hand in the Keycloak
  admin console ([§2.6](#checking-library-staff-accounts)).
- [ ] **Advised.** At your ingress, set the idle timeout longer than 55 seconds, the default
  `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT`. The usual default of 60 seconds already is. Then a slow
  placement gets DCB's own refusal, not a timeout from the ingress ([§2.6](#placement)).
- [ ] **Advised.** At your ingress, accept request bodies of at least 2 MB, so that brand images and
  mapping files up to DCB's upload limit get through.
- [ ] **Required.** Give the first start enough time before your platform restarts it
  ([Kubernetes and ECS](#kubernetes-and-ecs)).
- [ ] **Advised.** If DCB Admin or DCB Admin for Libraries sign in with client ids other than
  `dcb-admin` and `dcb-admin-for-libraries`, set `DCB_REQUESTS_STAFF_CLIENT_IDS` to yours.
- [ ] **Advised, if you run EBSCO Locate.** Test Locate from end to end against v9.2.0 on a test
  environment, before you upgrade production. **This test is the responsibility of whoever does QA
  for the hosting provider.** Check each thing Locate uses DCB for: a patron signs in with a library
  card, and through SAML where libraries use it; live availability shows; a pickup location can be
  chosen; a request can be placed, and then appears in DCB Admin
  ([Appendix C](#ebsco-locate)).
- [ ] **Advised.** If you set `FEATURES_ENABLED=improved-clustering` (the `features.enabled` list),
  remove it. Improved clustering is now always on, and no code reads a feature flag any more
  ([§2.3](#23-settings-no-longer-read)).
- [ ] **Advised.** Run the clustering query in [§2.6](#the-clustering-sweep). If any rows show `3`
  or `NULL`, expect high database and search load for hours after the upgrade.
- [ ] **Advised.** If anyone has changed the search index mapping by hand, export it and compare it
  with `mappings-2.json`. If they conflict, choose a new `DCB_INDEX_NAME`. We expect this step not to be needed, but the check should still be carried out for safety.
  ([§2.6](#the-shared-index)).
- [ ] **Advised.** Ask consortium staff to clean up requests in ERROR, and to check stored Polaris
  Host LMS records ([§2.6](#host-lms-records)).
- [ ] **Advised.** If no library uses NCIP peering (no OpenRS Appliance libraries), block
  `POST /ncip/v2_02` at your ingress: its peer authentication defaults to `INSECURE`. If you see
  abuse, rate-limit the other anonymous routes per client IP
  ([Appendix C](#anonymous-routes)).
- [ ] **Advised.** Check free disk space on the database volume and on your backup target.
  `V9_0_005` adds more than 100 MB of index on a table of 5 million audit rows.
- [ ] **Advised.** Restore a copy of production and time the upgrade against it. Allow twice that
  time as the startup limit on upgrade day.

### On upgrade day

- [ ] **Required.** Take a backup, and confirm you can restore it.
- [ ] **Required.** Stop every v8.71.0 instance ([Kubernetes and ECS](#kubernetes-and-ecs)).
- [ ] **Required.** Start **one** v9.2.0 instance. If you found a mapping conflict, give it a new
  `DCB_INDEX_NAME`.
- [ ] **Advised.** Set `DCB_INDEX_NUMBER_OF_REPLICAS` to the replica count your index has now.
  Otherwise DCB sets the index to 1 replica ([§2.6](#the-shared-index)).
- [ ] **Required.** Do not stop or restart DCB while the migrations run.
- [ ] **Required.** Read the log. Flyway applies `V8_71_001` to `V9_0_017`, then you see
  `Initialized index` and the search backend line. When it is done,
  `select version from flyway_schema_history order by installed_rank desc limit 1` returns
  `9.0.017`.
- [ ] **Required, only with a new index name.** Call `POST /admin/reindex`. This is only required **if the mappings conflict** - if not, you do not need to do this step.
- [ ] **Advised.** If you deploy new versions of DCB Admin and DCB Admin for Libraries, deploy them
  after DCB, not before. For a consortium in the US, set `VITE_LOCALE=en-US` in both. See each
  app's deployment guide.

### After the upgrade

- [ ] **Advised, on ECS.** If the clustering sweep is running, keep one task until it finishes,
  because each task runs its own sweep. On Kubernetes with working Hazelcast discovery, one pod runs
  it, so you can scale up. Then restore your normal startup probe or grace period.
- [ ] **Advised.** Within a few minutes, every Host LMS has a recorded ping. Ask consortium staff to
  correct and save any Host LMS whose last ping shows REFUSED or MISCONFIGURED.
- [ ] **Expected.** If you set `DCB_GLOBAL_NOTIFICATIONS_*`, many alarm webhook posts arrive a few
  minutes after the first start, as old ping failures clear ([§2.5](#25-behaviour-changes)).
- [ ] **Advised, for the first days.** Watch for placements refused with
  `PREFLIGHT_CHECKS_TIMED_OUT`, or `dcb.preflight.duration` with `outcome="timed_out"`. Only if they
  appear, raise `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT`, and raise the ingress idle timeout above it
  at the same time ([§2.6](#placement)).
- [ ] **Advised, once no Host LMS shows REFUSED or MISCONFIGURED and at least 2 weeks have passed.** Set
  `DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED=true` and restart.

### Kubernetes and ECS

|                              | Kubernetes                                                                                                                                              | AWS ECS/Fargate                                                                                                                                                                                               |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Stop the old version         | `strategy: type: Recreate`, or scale to 0 before you deploy. A `RollingUpdate` keeps v8.71.0 pods running while the migrations remove columns they read | Scale to 0. Deploy one task with `minimumHealthyPercent: 0`, and scale out once it is healthy                                                                                                                 |
| Allow for the first start    | A `startupProbe`, so a slow migration does not get the pod killed (example below)                                                                       | `healthCheckGracePeriodSeconds` of twice your timed upgrade, for example `1800`                                                                                                                               |
| Configuration that is a list | Can be a mounted file: `dcb.peer-auth`, or `trusted-services-json`                                                                                      | Cannot mount a file: use `DCB_DISCOVERY_TRUSTED_SERVICES_JSON`                                                                                                                                                |
| Secrets                      | Kubernetes secrets                                                                                                                                      | The NCIP peer-auth private JWK, `DCB_IDENTITY_PROVIDER_KEYCLOAK_CLIENT_SECRET` and any webhook URL from Secrets Manager or SSM Parameter Store, through `secrets` in the task definition, never `environment` |
| Locks                        | With working Hazelcast discovery, instances share locks                                                                                                 | DCB's default `hazelcast.yaml` uses multicast discovery, which does not work on AWS, so each task has its own locks. Unchanged from v8.71.0, but it means each task runs its own clustering sweep             |

```yaml
startupProbe:
  httpGet: { path: /health, port: 8080 }
  periodSeconds: 10
  failureThreshold: 180   # 30 minutes; tune from your own timed upgrade
livenessProbe:
  httpGet: { path: /health, port: 8080 }
  periodSeconds: 30
```

Flyway runs when DCB starts, before it takes traffic, so the probe must allow for the migrations.

### Going back to v8.71.0

Restore the backup and deploy v8.71.0 with it. Details:
[Appendix A](#rollback). Many single changes can be undone with a setting and a restart:
[§4](#undo-a-change-without-a-restore).

---

## 2. Settings and behaviour changes

Each variable is the environment form of the `dcb.…` key its source reads, and you can set any of
them in a mounted configuration file instead. A setting you leave out takes its default. The full
list is [Appendix B](#appendix-b-every-setting-new-since-v8710).

### 2.1 Settings to check on every upgrade

| Variable                                            | Default                             | What to do                                                                                                                                     | Source                                                                                                                                                           |
|-----------------------------------------------------|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT`             | `PT55S`                             | One time limit on all of a placement's checks. v8.71.0 had none. Leave it unset, and raise it only if placements time out ([§2.6](#placement)) | `dcb/src/main/java/org/olf/dcb/request/fulfilment/PreflightChecksTimeout.java`                                                                                   |
| `DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED`           | `false`                             | Leave off on upgrade day. Turn on after the Host LMS pings are clean                                                                           | `dcb/src/main/java/org/olf/dcb/request/fulfilment/SupplierPreflightService.java`                                                                                 |
| `DCB_REQUESTS_STAFF_CLIENT_IDS`                     | `dcb-admin,dcb-admin-for-libraries` | Set if your admin apps use other client ids                                                                                                    | `dcb/src/main/java/org/olf/dcb/security/StaffClients.java`                                                                                                       |
| `DCB_INDEX_NUMBER_OF_REPLICAS`                      | `1`                                 | Set to the replica count you run, if it is not 1. DCB applies it at every start ([§2.6](#the-shared-index))                                    | `dcb/src/main/java/org/olf/dcb/indexing/SharedIndexConfiguration.java`                                                                                           |
| `DCB_SCHEDULED_TASKS_SKIPPED`                       | unchanged                           | Names now match the declaring class. Tracking is `TrackingScheduler`, so `TrackingServiceV3` no longer skips it                                | `dcb/src/main/java/io/micronaut/scheduling/processor/AppTaskAwareScheduledMethodProcessor.java`; `dcb/src/main/java/org/olf/dcb/tracking/TrackingScheduler.java` |
| `DCB_JOBS_AVAILABILITY_MAPPED_RECHECK_GRACE_PERIOD` | `P60D`; was `P30D`                  | Nothing, unless you relied on the old default                                                                                                  | `dcb/src/main/java/org/olf/dcb/availability/job/AvailabilityCheckJobConfig.java`                                                                                 |
| `DCB_JOBS_AVAILABILITY_RECHECK_GRACE_PERIOD`        | `P14D`; was `P7D`                   | Nothing, unless you relied on the old default                                                                                                  | `dcb/src/main/java/org/olf/dcb/availability/job/AvailabilityCheckJobConfig.java`                                                                                 |

### 2.2 Optional features

Each is off, or works as v8.71.0 did, until you configure it.

| Feature                                                  | Switch                                                          | Default         | More                                                              |
|----------------------------------------------------------|-----------------------------------------------------------------|-----------------|-------------------------------------------------------------------|
| Discovery services with patron assertions                | `DCB_DISCOVERY_ENABLED`                                         | off             | [§3](#3-adding-a-trusted-discovery-service)                       |
| DCB Admin role check                                     | `DCB_SECURITY_ADMIN_UI_CLIENT_ID`, `DCB_SECURITY_ADMIN_UI_MODE` | off             | [§2.6](#the-dcb-admin-role-check)                                 |
| Creating DCB Admin for Libraries accounts from DCB Admin | the four `DCB_IDENTITY_PROVIDER_KEYCLOAK_*`                     | off             | [§2.6](#account-provisioning)                                     |
| Duplicate-request check                                  | `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ENABLED`      | off             | [§2.6](#placement); `docs/operational/duplicate-request-check.md` |
| Brand image uploads                                      | `DCB_BRANDING_ASSETS_STORE`                                     | `database` (on) | `docs/branding.md`                                                |
| Insights                                                 | `DCB_INSIGHTS_ENABLED`                                          | on              | `docs/insights.md`                                                |
| Tracking v4                                              | `DCB_TRACKING_SERVICE=v4`                                       | off (v3)        | `dcb/src/main/java/org/olf/dcb/tracking/MODULE.md`                |
| NCIP peering between DCBs                                | `DCB_PEER_AUTH_ENABLED`, `DCB_PEER_AUTH_NCIP_ENABLED`           | off             | `docs/ncip-peer-authentication.md`                                |
| Locks in one process (single instance only)              | `DCB_FEDERATION_LOCK_PROVIDER=local`                            | Hazelcast       | [Appendix B](#appendix-b-every-setting-new-since-v8710)           |
| The transaction code from before Micronaut 5             | `DCB_R2DBC_LEGACY_TRANSACTION_OPERATIONS_ENABLED`               | off             | [§4](#undo-a-change-without-a-restore)                            |

### 2.3 Settings no longer read

| Setting                                                                    | Last read by | Now                                                                                             |
|----------------------------------------------------------------------------|--------------|-------------------------------------------------------------------------------------------------|
| `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_REQUEST_WINDOW`          | v8.71.0      | Ignored, with a warning at startup. Remove it; `..._ACTIVE_WINDOW` is the limit if you need one |
| `FEATURES_ENABLED` (`features.enabled`), for example `improved-clustering` | v8.71.0      | Still accepted, but no code reads a feature flag: improved clustering is always on. Remove it   |

Source: `REMOVED_REQUEST_WINDOW` in
`dcb/src/main/java/org/olf/dcb/request/fulfilment/PreventDuplicateRequestsPreFlightCheck.java`;
`dcb/src/main/java/services/k_int/features/Features.java` (`@FeatureFlag` is commented out in
`ClusterHousekeepingService` and `DefaultRecordClusteringService`).

### 2.4 Blank values stop startup

A setting that is present with an empty value, the usual result of a template value nobody filled
in, stops DCB at startup with a message naming it. A feature that is off is not checked, so a blank
setting it owns does not stop startup.
**Remove any of these that you set to an empty value.**

| Setting                                                          | Blank stops startup                                    | Source                                                                                         |
|------------------------------------------------------------------|--------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT`                          | always; so does an unreadable or non-positive duration | `dcb/src/main/java/org/olf/dcb/request/fulfilment/PreflightChecksTimeout.java`                 |
| `DCB_REQUESTS_STAFF_CLIENT_IDS`                                  | always; so does a list that names no client            | `dcb/src/main/java/org/olf/dcb/security/StaffClients.java`                                     |
| `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ACTIVE_WINDOW` | when the duplicate-request check is on                 | `dcb/src/main/java/org/olf/dcb/request/fulfilment/PreventDuplicateRequestsPreFlightCheck.java` |
| `DCB_SECURITY_ADMIN_UI_CLIENT_ID`                                | always: set, it turns the role check on                | `dcb/src/main/java/org/olf/dcb/security/AdminUiAccessConfig.java`                              |
| `DCB_IDENTITY_PROVIDER_KEYCLOAK_*`                               | once any of the four is present                        | `dcb/src/main/java/org/olf/dcb/security/provisioning/IdentityProviderConfig.java`              |
| `DCB_DISCOVERY_AUDIENCE`                                         | when discovery is on                                   | `dcb/src/main/java/org/olf/dcb/security/discovery/DiscoveryConfig.java`                        |
| `DCB_DISCOVERY_TRUSTED_SERVICES_JSON`                            | when discovery is on                                   | `dcb/src/main/java/org/olf/dcb/security/discovery/DiscoveryTrustedServices.java`               |

The message depends on the setting: `is set but blank`, `must not be blank`, `which is not a
duration` or `names no client`.

### 2.5 Behaviour changes

| What changes                                                                                                                                                                                                                                                                                                                                                                                                                                 | What you do                                                                                                                                                                                                                                 | Source                                                                                                                                                                                                                       |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DCB uses `elasticsearch-java` 9.x. Elastic's clients send their major version in a header, so a 9.x client cannot talk to an Elasticsearch 8.x server                                                                                                                                                                                                                                                                                        | Upgrade Elasticsearch. OpenSearch needs no change                                                                                                                                                                                           | `docs/micronaut-5-hosting-notes.md`                                                                                                                                                                                          |
| The JVM image is based on `eclipse-temurin:25-jre-alpine` (was `21-jre-alpine`). The native image (`<repo>:native-<tag>`) is based on `cgr.dev/chainguard/wolfi-base` and needs glibc, so it will not run on musl/Alpine. Tag names are unchanged                                                                                                                                                                                            | Nothing, unless you rebase the image. To build from source you need JDK 25 and Gradle 9. Images are built by the Gradle `dockerBuild` and `dockerBuildNativeBinary` tasks; there is no committed `Dockerfile` (`docs/local-development.md`) | `dcb/build.gradle` (the docker tasks)                                                                                                                                                                                        |
| Migrations make the upgrade one-way and not rolling                                                                                                                                                                                                                                                                                                                                                                                          | [§1](#1-what-you-must-do)                                                                                                                                                                                                                   | [Appendix A](#appendix-a-database-migrations-and-rollback)                                                                                                                                                                   |
| A full re-cluster can start after the upgrade                                                                                                                                                                                                                                                                                                                                                                                                | Run the query before you upgrade                                                                                                                                                                                                            | [§2.6](#the-clustering-sweep)                                                                                                                                                                                                |
| The index mapping and replica count are applied at every start                                                                                                                                                                                                                                                                                                                                                                               | Set `DCB_INDEX_NUMBER_OF_REPLICAS`                                                                                                                                                                                                          | [§2.6](#the-shared-index)                                                                                                                                                                                                    |
| More routes need a role, `POST /v2/patron/auth/lookup` among them. The legacy request lookup by barcode matches the whole barcode                                                                                                                                                                                                                                                                                                            | Check every caller                                                                                                                                                                                                                          | [Appendix C](#appendix-c-roles-and-routes)                                                                                                                                                                                   |
| A `LIBRARY_ADMIN` or `LIBRARY_READ_ONLY` token must carry the library's DCB agency code in `code` or `agencyCodes`, in the access token, not only the ID token. Without it DCB Admin for Libraries shows empty lists and gets 403s                                                                                                                                                                                                           | Check every library staff account before you upgrade ([§2.6](#checking-library-staff-accounts))                                                                                                                                             | `dcb/src/main/java/org/olf/dcb/security/AgencyClaims.java`, `.../CallerScope.java`                                                                                                                                           |
| A placement has a time limit. One still checking when it runs out is refused with `PREFLIGHT_CHECKS_TIMED_OUT`, and nothing is saved                                                                                                                                                                                                                                                                                                         | [§2.6](#placement)                                                                                                                                                                                                                          | `dcb/src/main/java/org/olf/dcb/request/fulfilment/PreflightChecksTimeout.java`                                                                                                                                               |
| Pings check credentials, are recorded per Host LMS (`HostLms.lastPing` in GraphQL), raise an alarm only on failure, and run when a Host LMS is saved. Saving a Polaris Host LMS clears its token cache, so a changed password works at once. In v8.71.0 the FOLIO ping sent no key, so this is the first credential check some systems have had                                                                                              | Correct Host LMS records that show REFUSED or MISCONFIGURED                                                                                                                                                                                 | `dcb/src/main/java/org/olf/dcb/core/svc/HostLmsPingService.java`                                                                                                                                                             |
| Host LMS configuration is checked when it is saved                                                                                                                                                                                                                                                                                                                                                                                           | [§2.6](#host-lms-records)                                                                                                                                                                                                                   | `dcb/src/main/java/org/olf/dcb/graphql/validation/HostLmsConfigValidator.java`                                                                                                                                               |
| A request in ERROR that DCB created a virtual item for keeps its copy until the request is finalised. DCB does not choose that copy again for a borrowing system that cannot hold two virtual items for one copy                                                                                                                                                                                                                             | Ask consortium staff to clean up requests in ERROR                                                                                                                                                                                          | `dcb/src/main/java/org/olf/dcb/request/resolution/ExcludeCopyPromisedToBorrowerItemFilter.java`; `findSystemsWithRecordedVirtualItemForSupplierCopy` in `dcb/src/main/java/org/olf/dcb/storage/PatronRequestRepository.java` |
| A new status, `AWAITING_RETURN_TO_SUPPLIER`, polled every hour: a request cancelled after its item shipped waits there until the supplier reports the item back                                                                                                                                                                                                                                                                              | Nothing. To start v8.71.0 again, see [Appendix A](#rollback)                                                                                                                                                                                | `dcb.polling.durations` in `dcb/src/main/resources/application.yml`; `docs/cancellation-handling.md`                                                                                                                         |
| Alarm webhooks (`DCB_GLOBAL_NOTIFICATIONS_*`) are now sent. In v8.71.0 they never were. Many "deactivated" posts arrive a few minutes after the first start, as old ping failures clear. The target, URL included, is logged at INFO                                                                                                                                                                                                         | Keep the webhook URL in your secret store, and treat the logs as holding it                                                                                                                                                                 | `dcb/src/main/java/org/olf/dcb/core/svc/AlarmsService.java`                                                                                                                                                                  |
| Every inbound HTTP request gets a request id. Each log line carries it in brackets after the level (empty outside a request), and the response returns it as `X-Request-Id`                                                                                                                                                                                                                                                                  | Update your log parsers                                                                                                                                                                                                                     | `docs/request-id.md`                                                                                                                                                                                                         |
| Patron and Host LMS credentials are not logged. v8.71.0 logged a failed Polaris call's request headers and body, which on the staff login hold the staff user name and password. The placement parameters, the FOLIO virtual-patron lookup and the borrower-loan error lines do not put a patron barcode in a log line                                                                                                                       | Change the Polaris staff passwords ([§1](#after-the-upgrade)). Some DEBUG lines still carry patron data (the Sierra client logs a whole patron record when it creates a virtual patron), so turn a logger back down after troubleshooting   | `dcb/src/main/resources/logback.xml`; `dcb/src/main/java/org/olf/dcb/core/interaction/PlaceHoldRequestParameters.java`; `dcb/src/main/java/org/olf/dcb/core/interaction/polaris/PolarisLmsClient.java`                       |
| A file appender writes `logs/dcb-service-logs-<start>.log` inside the container, as well as the console, rolling at 250 MB                                                                                                                                                                                                                                                                                                                   | Make the working directory temporary and size-limited                                                                                                                                                                                       | `dcb/src/main/resources/logback.xml`                                                                                                                                                                                         |
| Uploads up to `DCB_BRANDING_ASSETS_MAX_BYTES` (2 MB) are accepted; the framework's limit was 1 MB                                                                                                                                                                                                                                                                                                                                            | Allow 2 MB bodies at your ingress                                                                                                                                                                                                           | `dcb/src/main/java/org/olf/dcb/core/branding/BrandAssetProperties.java`; `dcb/src/main/resources/application.yml`                                                                                                            |
| New things you can monitor: `dcb.preflight.duration` (tag `outcome`) and `dcb.preflight.check.duration` (tag `check`); stalled-harvest detection with `GET /admin/sourceImport/status`, `POST /admin/sourceImport/{hostLmsCode}/reconcile` and `POST /admin/sourceImport/{hostLmsCode}/resetCheckpoint`; the search backend, from `curl -s http://<dcb-host>:8080/info \| jq '.dcb.index.backend'`; `dcb.stats.events` and `dcb.stats.timed` | Nothing to configure                                                                                                                                                                                                                        | `dcb/src/main/java/org/olf/dcb/core/api/AdminController.java`                                                                                                                                                                |
| `GET /patrons/requests/stats/top-requestors` and `/top-requested-titles` still answer, through a shim over Insights that logs at WARN when called                                                                                                                                                                                                                                                                                            | Nothing                                                                                                                                                                                                                                     | `dcb/src/main/java/org/olf/dcb/core/api/LegacyStatsController.java`                                                                                                                                                          |

Not changed: port 8080; the health, info and Prometheus paths (meters are added, none renamed); the
image tag scheme; Hazelcast, pinned at 5.4.0; OpenSearch's need for `analysis-icu`; the Keycloak
roles that exist (what changed is which roles DCB requires).

### 2.6 Details

#### The clustering sweep

**Since v9.0.0.** Improved clustering is the only implementation, and the ingest process version is
fixed at 5. `ClusterHousekeepingService` reprocesses any cluster containing a bib below the current
process version. **If improved clustering was already on (`FEATURES_ENABLED=improved-clustering`),
nothing happens, and you can remove that setting.**

If it was not, every bib is at process version 3, and on first start the housekeeping task works
through the whole catalogue. It starts 10 seconds after startup, in batches of 2,000 clusters with a
500 ms pause, and logs its rate:

```
Processed 2000 clusters with oudated bibs. Total time 0 hours, 3 minute and 12 seconds (rate of 10.42 per second)
```

To know which applies to you, run this against production **before** you upgrade:

```sql
SELECT process_version, count(*) FROM bib_record GROUP BY process_version ORDER BY 1;
```

| Result                    | What happens on upgrade                                    |
|---------------------------|------------------------------------------------------------|
| Everything already at `5` | Nothing. The sweep finds no work                           |
| Anything at `3` or `NULL` | Those records are reprocessed in batches until none remain |

While it runs, expect raised load on Postgres and on the search backend. With working Hazelcast
discovery, one instance runs it. On ECS, each task runs its own sweep, so run a single task until it
finishes. There is no switch to delay it.

Source: `dcb/src/main/java/org/olf/dcb/ingest/IngestService.java` (`getProcessVersion`);
`dcb/src/main/java/org/olf/dcb/core/clustering/ClusterHousekeepingService.java`.

#### The shared index

- **The mapping only adds fields.** Between v8.71.0 and v9.2.0, `mappings-2.json` adds about thirty
  field paths for discovery metadata, such as `classification`, `shelfSort`, `alternateScripts`,
  `relationships`, `carrier-type`, member `availability` and `firstSeen`. No field is removed,
  renamed or retyped. `settings-2.json` is unchanged. The one new field that names an analyzer
  (`subjects.fullLabel`, `icu_folding_nopunc`) uses an analyzer v8.71.0's settings already define.
  `fullSettings-2.json` is new, and used only when DCB creates an index. Source:
  `dcb/src/main/resources/sharedIndex/mappings-2.json`, `settings-2.json`, `fullSettings-2.json`.
- **The mapping is sent at every start, and a refusal stops DCB.** DCB waits for the index to
  accept it. If the index refuses it, DCB does not start: fulfilment, tracking and patron
  authentication stop with search. Source:
  `dcb/src/main/java/org/olf/dcb/indexing/SharedIndexLiveUpdater.java`
  (`sharedIndexService.initialize()`, blocking).
- **New fields fill on the nightly sync.** `IndexSynch` re-sends every cluster 20 seconds after
  start and then every 24 hours, so documents gain `firstSeen` and the other Postgres fields on the
  first pass. MARC discovery metadata is not added to existing bibs: it is written when a record is
  processed. `shelfSort` also needs a library's `classification_scheme`, which `V9_0_015` adds empty
  for every library. Source: `dcb/src/main/java/org/olf/dcb/indexing/job/IndexSynch.java`
  (`@Scheduled(initialDelay = "20s", fixedDelay = "24h")`).
- **The replica count is applied at every start.** `DCB_INDEX_NUMBER_OF_REPLICAS` (default `1`) is
  set on the existing index at every start, not only when DCB creates it. v8.71.0 created the index
  with 1 replica, so an index nobody changed already has 1, and nothing happens. A count changed by
  hand on the cluster is set back to the setting, so set it to the count you run. DCB changes it
  with a live settings update, without closing the index. On a single-node cluster, `0` turns a
  yellow index green. Source: `reconcileNumberOfReplicas()` in
  `dcb/src/main/java/org/olf/dcb/indexing/opensearch/OpenSearchSharedIndexService.java` and
  `.../elasticsearch/ElasticsearchSharedIndexService.java`.

#### Placement

- **The time limit.** A placement whose checks take longer than
  `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT` (55 seconds by default) is refused with
  `PREFLIGHT_CHECKS_TIMED_OUT`, and nothing is saved. Keep the limit **shorter** than the time your
  ingress and every system that places requests will wait. If a caller stops waiting first, it
  shows its own error while DCB may still place the request, and a patron who tries again can end
  up with two requests. The default is below the 60-second idle timeout that ingresses and load
  balancers usually have, so leave it unset unless placements time out. Each placement logs one
  line, `Preflight checks <outcome> in <n> ms`, and `dcb.preflight.duration` is tagged with the
  same outcome: `passed`, `failed` or `timed_out`. Leaving the setting out logs
  `Using default preflight check timeout value of PT55S`. Source:
  `dcb/src/main/java/org/olf/dcb/request/fulfilment/PreflightChecksTimeout.java`;
  `dcb/src/main/java/org/olf/dcb/request/fulfilment/PatronRequestPreflightChecksService.java`.
- **The supplier preflight.** When on, DCB skips the copies of a supplier whose setup or last ping
  says it cannot supply, and the request goes to another library. Leave it off for the first start,
  and turn it on once no Host LMS shows REFUSED or MISCONFIGURED. Source:
  `dcb/src/main/java/org/olf/dcb/request/fulfilment/SupplierPreflightService.java`;
  `docs/operational/supplier-preflight.md`.
- **The duplicate-request check** (`DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ENABLED`, off by
  default) now refuses a repeat for as long as the earlier request is active, not for a fixed time.
  Requests placed through the clients in `DCB_REQUESTS_STAFF_CLIENT_IDS` are recorded as placed by
  staff, and are not checked. Source:
  `dcb/src/main/java/org/olf/dcb/request/fulfilment/PreventDuplicateRequestsPreFlightCheck.java`.

#### Host LMS records

Host LMS configuration is checked when it is saved, and this release refuses some values earlier
releases stored. A stored record that fails the check cannot be edited until those values are
corrected, because an edit replaces the whole configuration. Check before you upgrade:

- **Polaris:** each required item and service value is present and a whole number;
  `borrower-lending-flow` is not set (it is no longer supported, and a stored one fails placement);
  `shelfLocationPolicyMap` values are ones the check accepts (`REFERENCE` is not one;
  `REFERENCE_ONLY` is).
- **Polaris:** an empty or unknown `item-agency-resolution-method` now resolves items by shelf
  location.
- **FOLIO:** with `record-syntax` or `metadata-prefix` unset, harvesting uses the published defaults.

Source: `dcb/src/main/java/org/olf/dcb/graphql/validation/HostLmsConfigValidator.java`;
`dcb/src/main/java/org/olf/dcb/core/interaction/polaris/PolarisConfig.java`;
`dcb/src/main/java/org/olf/dcb/graphql/UpdateHostLmsDataFetcher.java` (`clientConfig`, when sent,
replaces the stored configuration, as `docs/polaris_notes.md` notes).

#### Checking library staff accounts

DCB limits a `LIBRARY_ADMIN` or `LIBRARY_READ_ONLY` user to their own library by the `code` or
`agencyCodes` claim in their **access** token. The claim holds one DCB agency code, or a list for
someone who works for several libraries. A blank value counts as missing. Without it, DCB Admin
for Libraries shows that user empty lists. The value comes from the user's `code` attribute,
through a User Attribute mapper in the `profile` client scope, as DCB Admin for Libraries'
deployment guide sets it up.

**With the script.** [`scripts/check-library-staff-agency-claims.sh`](../scripts/check-library-staff-agency-claims.sh)
asks Keycloak for the access token each user would receive from DCB Admin for Libraries' client,
and lists the library staff to fix. It only reads, apart from signing in to the admin API, and
needs `curl` and `jq`.

```sh
read -rs KC_ADMIN_PASSWORD && export KC_ADMIN_PASSWORD   # so the password stays out of shell history
KC_URL=https://keycloak.example.org KC_REALM=<realm> KC_CLIENT_ID=<DCB Admin for Libraries client id> \
DCB_URL=https://dcb.example.org KC_ADMIN_USER=<administrator> \
  scripts/check-library-staff-agency-claims.sh
```

Each line it prints is a user to fix, with their username, whether the account is enabled, and the
problem:

| Problem                                      | What it means                                                                                                 |
|----------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `no code or agencyCodes in the access token` | The user has no `code` attribute, or the mapper does not add it to the access token                           |
| `not a DCB agency: <code>`                   | The value is not the code of any agency in DCB, as `GET /discovery/libraries?includeAll=true` lists them      |
| `library role only in realm_access.roles`    | The client has no mapper that writes realm roles to a top-level `roles` claim, which is where DCB reads roles |

- Leave out `DCB_URL` to skip the agency check.
- To sign in with a service account instead of a master-realm administrator, set
  `KC_ADMIN_CLIENT_ID` and `KC_ADMIN_CLIENT_SECRET` in place of `KC_ADMIN_USER` and
  `KC_ADMIN_PASSWORD`. Use a confidential client in the realm whose service account has the
  `realm-management` roles `view-users` and `view-clients`.
- The script makes one admin call per user, so on a large realm run it at a quiet time.
- Keycloak 16 and older serve everything under `/auth`: add it to `KC_URL`.

**By hand, in the Keycloak admin console:**

1. **Find the library staff.** Open **Realm roles**, then `LIBRARY_ADMIN`, then **Users in role**.
   Do the same for `LIBRARY_READ_ONLY`. This list shows only users who were given the role
   directly. Users who get it from a group are the members of each group whose **Role mapping**
   includes it.
2. **See the user's token.** Open **Clients**, then DCB Admin for Libraries' client, then
   **Client scopes**, then **Evaluate**. Choose the user, and open **Generated access token**.
3. **Check it.** `roles` must list `LIBRARY_ADMIN` or `LIBRARY_READ_ONLY`, and `code` (or
   `agencyCodes`) must be the DCB agency code of the user's library.

**To fix a user**, open **Users**, then the user, then **Attributes**. Add `code` with the agency
code, and save. In Keycloak 24 and later the **Attributes** tab appears only when **Realm settings**
→ **General** → **Unmanaged attributes** is enabled; otherwise add `code` in **Realm settings** →
**User profile**. If a user has the attribute and their token still has no `code`, open
**Client scopes** → `profile` → **Mappers**, and check that the mapper for the `code` attribute has
**Add to access token** on.

Source: `dcb/src/main/java/org/olf/dcb/security/AgencyClaims.java`;
`scripts/check-library-staff-agency-claims.sh`.

#### The DCB Admin role check

**Since v9.1.0, off unless configured.** Set `DCB_SECURITY_ADMIN_UI_CLIENT_ID` and DCB refuses a
token issued to DCB Admin's client unless it carries a consortium role. DCB logs at startup whether
the check is off, or which mode it is in for which client. Run `DCB_SECURITY_ADMIN_UI_MODE=WARN`
(the default: it logs what it would refuse) first, and read the log before you set `ENFORCE`.

Source: `dcb/src/main/java/org/olf/dcb/security/AdminUiAccessConfig.java`,
`.../AdminUiAccessPolicy.java`.

#### Account provisioning

DCB Admin can create DCB Admin for Libraries accounts (since v9.1.0) through an identity provider.
Only Keycloak is supported, and it is set as one block: the four `DCB_IDENTITY_PROVIDER_KEYCLOAK_*`
settings. Once any of them is present, all four are required and none may be blank; the secret has
no default. Leave all four out and provisioning is off; the GraphQL mutations answer that it is not
configured. **Any other key under `dcb.identity-provider` stops startup** with
`dcb.identity-provider "<key>" is not supported`.

**We recommend leaving this off until the user interface for it is in place.**

Source: `dcb/src/main/java/org/olf/dcb/security/provisioning/IdentityProviderConfig.java`,
`.../keycloak/KeycloakConfig.java`, `.../IdentityProviderConfigCheck.java`.

---

## 3. Adding a trusted discovery service

A discovery system reaches DCB by one of two routes. A deployment can serve both at once: they use
different credentials and different routes, and nothing set for one changes the other.

|              | Legacy route                                                               | New route                                         |
|--------------|----------------------------------------------------------------------------|---------------------------------------------------|
| Used by      | EBSCO Locate                                                               | any other discovery service                       |
| Credential   | a Keycloak service account holding `ADMIN`                                 | a confidential client holding `DISCOVERY_SERVICE` |
| Which patron | named by the caller in the request                                         | a signed patron assertion, which DCB checks       |
| Routes       | the staff API: `/patrons/requests/**`, `/v2/patron/auth/lookup` and others | `/discovery/**`                                   |
| DCB settings | none                                                                       | `DCB_DISCOVERY_*`                                 |

This section is about the new route. EBSCO Locate needs no DCB setting; what changed for it is in
[Appendix C](#ebsco-locate).

On the new route, the discovery service holds a confidential Keycloak client with the
`DISCOVERY_SERVICE` role, and sends a signed patron assertion with each call. What the vendor builds
against is [`docs/discovery-service-contract.md`](discovery-service-contract.md). What you do is in
[`docs/discovery-service-approach.md`](discovery-service-approach.md) §7. In short:

### Add a service

1. **In Keycloak,** create a confidential client holding the **realm** role `DISCOVERY_SERVICE`, with
   a mapper that writes realm roles to a top-level `roles` claim. Use the vendor's `service-id` as
   the client id ([approach §7.1](discovery-service-approach.md#71-part-one-keycloak-environment-independent)).
2. **From the vendor,** get its `service-id`, `issuer` and `jwks-uri`
   ([contract §5](discovery-service-contract.md#5-what-the-operator-will-need-from-you)).
3. **Add one entry** to `DCB_DISCOVERY_TRUSTED_SERVICES_JSON`, and keep the entries that are there.
   Each `service-id`, `issuer` and client id must be unique, or DCB does not start
   ([approach §7.2](discovery-service-approach.md#72-part-two-everything-can-be-an-environment-variable)).
4. **For the first service only,** set `DCB_DISCOVERY_ENABLED=true` and a `DCB_DISCOVERY_AUDIENCE`
   for this deployment, and give that audience to every vendor
   ([approach §7.2](discovery-service-approach.md#72-part-two-everything-can-be-an-environment-variable)).
5. **Allow outbound HTTPS** to the vendor's `jwks-uri` host
   ([approach §7.6](discovery-service-approach.md#76-part-three-egress-to-the-vendors-jwks-url)).
6. **Restart, then read the log.** `Discovery patron assertions accepted from N trusted service(s):
   [...]` should name the new service ([approach §7.8](discovery-service-approach.md#78-verifying-that-an-onboarding-worked)).
7. **Test with the vendor** before you announce it
   ([contract §12](discovery-service-contract.md#12-testing-your-integration);
   [approach §7.8](discovery-service-approach.md#78-verifying-that-an-onboarding-worked)).

To remove a service, delete its entry and restart
([approach §7.9](discovery-service-approach.md#79-offboarding-and-rotation)). The settings that limit what one patron call may
cost are the `DCB_DISCOVERY_*` rows of [Appendix B](#appendix-b-every-setting-new-since-v8710); the
defaults suit most deployments.

Source: `dcb/src/main/java/org/olf/dcb/security/discovery/` (`DiscoveryConfig`,
`DiscoveryTrustedServices`, `DiscoveryTrustedServiceStore`);
`dcb/src/main/java/org/olf/dcb/security/RoleNames.java`.

---

## 4. Troubleshooting

### DCB does not start

| Symptom                                                                                                                 | Cause                                                                                | What to do                                                                                      |
|-------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| Startup fails naming a setting: `is set but blank`, `must not be blank`, `which is not a duration` or `names no client` | A setting from [§2.4](#24-blank-values-stop-startup) is present with no usable value | Remove it, or give it a value                                                                   |
| Startup fails: `dcb.identity-provider "<key>" is not supported`                                                         | A key under `dcb.identity-provider` other than `keycloak` is set                     | Remove it ([§2.6](#account-provisioning))                                                       |
| Startup fails: `Duplicate discovery service issuer`, `… id` or `… client-id`                                            | Two trust entries share a value                                                      | Give each service its own `service-id`, `issuer` and client id                                  |
| Startup fails: `Incomplete … entry (service-id=…)`                                                                      | A trust entry has no `issuer`, or neither `jwks-uri` nor `jwks`                      | Complete the entry                                                                              |
| Startup fails while preparing the index, citing a mapping or analyzer conflict                                          | The live index holds a field in another way                                          | Deploy with a new `DCB_INDEX_NAME`, then call `POST /admin/reindex` ([§2.6](#the-shared-index)) |
| `status: 400 … Accept version must be … found 9`                                                                        | Elasticsearch 8.x behind a 9.x client                                                | Upgrade Elasticsearch to 9.x                                                                    |
| `status: 401 … [es/indices.exists]`                                                                                     | The index credentials do not reach the client                                        | Check `DCB_INDEX_USERNAME` and `DCB_INDEX_PASSWORD`                                             |
| `CP subsystem is a licensed feature`                                                                                    | A Hazelcast newer than 5.4.0 on the classpath                                        | It should not happen with our image: report it                                                  |

Source: the classes named in [§2.3](#23-settings-no-longer-read) and [§2.4](#24-blank-values-stop-startup);
`dcb/src/main/java/org/olf/dcb/security/discovery/DiscoveryTrustedServiceStore.java`.

### The first start goes wrong

| Symptom                                                                                | Cause                                                            | What to do                                                                        |
|----------------------------------------------------------------------------------------|------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| The instance restarts again and again; the log stops during a migration                | The liveness probe fires during the migrations                   | Add a `startupProbe`, or raise its `failureThreshold` ([§1](#kubernetes-and-ecs)) |
| Placements fail on some instances                                                      | A v8.71.0 instance is still running against the changed database | Stop it ([§1](#1-what-you-must-do))                                               |
| High database and search load, and `Processed N clusters with oudated bibs` in the log | The clustering sweep                                             | Let it finish ([§2.6](#the-clustering-sweep))                                     |

### Something fails once DCB is running

| Symptom                                                                               | Cause                                                                                                   | What to do                                                                                                                              |
|---------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| Integrations get `400` or `401` on `/patrons/requests/**` or `/v2/patron/auth/lookup` | No token (`400`) or no role (`401`)                                                                     | Give the caller a role from [Appendix C](#appendix-c-roles-and-routes)                                                                  |
| Library staff see empty lists in DCB Admin for Libraries                              | Their access token has no `code` or `agencyCodes`                                                       | Set the claim in Keycloak ([§2.6](#checking-library-staff-accounts))                                                                    |
| Placements refused with `PREFLIGHT_CHECKS_TIMED_OUT`                                  | The checks ran past `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT`                                             | Read `dcb.preflight.duration` to see how long placements take. Raise the limit above that, and the ingress idle timeout above the limit |
| Every discovery patron call gets `401` with no `code` (`400` without a token)         | Discovery is off, so the routes do not exist                                                            | Set `DCB_DISCOVERY_ENABLED=true`                                                                                                        |
| Every discovery patron call is refused; the log says "JWT issuer is not trusted"      | No trusted service is loaded                                                                            | Check the `Discovery patron assertions accepted from <n>` startup line, and use the JSON form                                           |
| `403` on every `/discovery/` call                                                     | The role was given as a client role, or the client has no `roles` mapper                                | Fix the Keycloak client ([§3](#add-a-service), step 1)                                                                                  |
| Patron assertions refused: `lifetime … exceeds the … maximum`                         | The vendor's assertions last longer than `DCB_DISCOVERY_MAX_ASSERTION_LIFETIME`                         | Ask the vendor for shorter assertions                                                                                                   |
| Uploads refused at just over 1 MB                                                     | Something overrides `micronaut.server.multipart.max-file-size`, or the ingress body limit is below 2 MB | Remove the override, or raise the ingress limit                                                                                         |
| The replica count changed after the upgrade                                           | A count set by hand on the cluster was set back to `DCB_INDEX_NUMBER_OF_REPLICAS` (default `1`)         | Set it to the count you run                                                                                                             |
| Tracking still runs on an instance that should skip it                                | `DCB_SCHEDULED_TASKS_SKIPPED` names `TrackingServiceV3`                                                 | Use `TrackingScheduler`                                                                                                                 |
| Many alarm posts a few minutes after start                                            | Old ping failures clearing, now that webhooks are sent                                                  | Nothing                                                                                                                                 |

Source: `dcb/src/main/java/org/olf/dcb/security/DcbAuthorizationExceptionHandler.java`;
`dcb/src/main/java/org/olf/dcb/security/discovery/PatronAssertionVerifier.java`;
`docs/discovery-service-approach.md` §7.1.

### Undo a change without a restore

Each of these is undone by one setting and a restart:

| To undo                                   | Set                                                                                      |
|-------------------------------------------|------------------------------------------------------------------------------------------|
| The new transaction code                  | `DCB_R2DBC_LEGACY_TRANSACTION_OPERATIONS_ENABLED=true`                                   |
| Placements refused for time               | a higher `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT`, with the ingress idle timeout above it |
| The supplier preflight                    | `DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED=false`                                          |
| The duplicate-request check               | `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ENABLED=false`                         |
| The DCB Admin role check refusing callers | `DCB_SECURITY_ADMIN_UI_MODE=WARN`, or remove `DCB_SECURITY_ADMIN_UI_CLIENT_ID`           |
| Insights load                             | `DCB_INSIGHTS_ENABLED=false`                                                             |
| Brand uploads                             | `DCB_BRANDING_ASSETS_STORE=none`                                                         |
| Replica changes                           | `DCB_INDEX_NUMBER_OF_REPLICAS=<your current count>`                                      |
| A misbehaving scheduled job               | `DCB_SCHEDULED_TASKS_SKIPPED=<its class name>`                                           |
| Webhook posts                             | remove `DCB_GLOBAL_NOTIFICATIONS_*`                                                      |

Log lines already written and webhook posts already sent cannot be undone.

---

## Appendix A. Database migrations and rollback

### Migrations

From v8.71.0, nineteen migrations run on first start, one transaction per file, so each lock is
held until that file commits. None touches `bib_record`, `cluster_record`, `source_record` or
`match_point`.

Source: `dcb/src/main/resources/db/migration/`, one file per row below; Flyway settings under
`flyway:` in `dcb/src/main/resources/application.yml`.

| Migration                                  | What it does                                                                                                                                                     | Cost and lock                                                                                                                           |
|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `V8_71_001__patron_request_outcome`        | Adds `outcome_code` to `patron_request`, then fills it for every historical row: four `UPDATE`s, three with correlated subqueries against `patron_request_audit` | **The expensive one.** `ACCESS EXCLUSIVE` on `patron_request` for the whole file                                                        |
| `V8_72_001__dcb_profile_membership`        | New table, four indexes                                                                                                                                          | Trivial                                                                                                                                 |
| `V9_0_001__consortium_branding`            | Six nullable columns on `consortium`                                                                                                                             | Trivial                                                                                                                                 |
| `V9_0_002__library_branding`               | Three nullable columns on `library`, plus `idx_library_agency_id`                                                                                                | Small                                                                                                                                   |
| `V9_0_003__brand_asset`                    | New `brand_asset` table                                                                                                                                          | Trivial                                                                                                                                 |
| `V9_0_004__consortium_brand_merge`         | Copies two URLs into the brand columns, writes provenance to `data_change_log`, then **removes six columns** from `consortium`                                   | Small, **cannot be reversed**                                                                                                           |
| `V9_0_005__analytics_indexes`              | Six indexes on `patron_request` and `patron_request_audit`, built without `CONCURRENTLY`                                                                         | **Significant.** Writes to both tables wait for each build. The file's own note: about 107 MB for `pra_audit_date_idx` at 5M audit rows |
| `V9_0_006__library_user_account`           | New table, index and audit triggers                                                                                                                              | Trivial                                                                                                                                 |
| `V9_0_007__agency_max_local_holds`         | Nullable `max_local_holds` on `agency`                                                                                                                           | Trivial                                                                                                                                 |
| `V9_0_008__pr_tracking_resumed_at`         | Nullable timestamp on `patron_request`                                                                                                                           | Metadata only                                                                                                                           |
| `V9_0_009__location_creation_source`       | `creation_source` on `location`, `NOT NULL DEFAULT 'UNKNOWN'`                                                                                                    | Metadata only on Postgres 11+                                                                                                           |
| `V9_0_010__alarm_code_length`              | Widens `alarm.code` to `varchar(255)`                                                                                                                            | Trivial                                                                                                                                 |
| `V9_0_011__host_lms_last_ping`             | Nullable `jsonb` on `host_lms`                                                                                                                                   | Trivial                                                                                                                                 |
| `V9_0_012__supplier_request_item_index`    | `idx_supplier_request_item` on `supplier_request (host_lms_code, local_item_id) WHERE is_active`, not `CONCURRENTLY`                                             | Writes to `supplier_request` wait for the build                                                                                         |
| `V9_0_013__support_url`                    | Nullable columns on `library` and `consortium`                                                                                                                   | Trivial                                                                                                                                 |
| `V9_0_014__announcement`                   | New table, index and audit trigger                                                                                                                               | Trivial                                                                                                                                 |
| `V9_0_015__library_classification_scheme`  | Nullable column on `library`                                                                                                                                     | Trivial                                                                                                                                 |
| `V9_0_016__patron_request_placed_by_staff` | Adds `placed_by_staff` to `patron_request`, then sets it for earlier staff requests (expedited checkout, or a note or description starting `Staff Request:`)     | `ACCESS EXCLUSIVE` on `patron_request` for the whole file; one table scan                                                               |
| `V9_0_017__patron_request_notes_and_tags`  | Three new tables for request notes and tags, and five seed tags                                                                                                  | Trivial; no foreign key to `patron_request`                                                                                             |

While `V8_71_001` or `V9_0_016` runs, every read and write of `patron_request` waits. How much
longer the first start takes depends on the size of `patron_request` and `patron_request_audit`.
For an exact figure, restore a copy of your production database and time the upgrade against it.

### Rollback

**To go back to v8.71.0, restore the database** from the backup you took, and deploy the v8.71.0
image with it. Once `V9_0_004` has run, v8.71.0 cannot read the `consortium` table, so in practice
the restore point is "before the first v9.2.0 instance started".

If you must start v8.71.0 against a database v9.2.0 has written to, run this first, because v8.71.0
has no `AWAITING_RETURN_TO_SUPPLIER` status:

```sql
update patron_request set status_code = 'RETURN_TRANSIT' where status_code = 'AWAITING_RETURN_TO_SUPPLIER';
```

Audit rows that name the status stay unreadable to v8.71.0.

If you upgraded Elasticsearch to 9.x for this release, a DCB with an 8.x-era client cannot use the
upgraded cluster.

Source: `dcb/src/main/java/org/olf/dcb/core/model/PatronRequest.java` (`Status`).

---

## Appendix B. Every setting new since v8.71.0

**Since** is the first release that read the setting. Source paths are under
`dcb/src/main/java/org/olf/dcb/` unless given in full. What a blank value does is in
[§2.4](#24-blank-values-stop-startup).

| Variable                                                                                          | Default                                                                                                                                                                 | What it does                                                                                                                                                                                                                                                           | Since                    | Source                                                                                            |
|---------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|---------------------------------------------------------------------------------------------------|
| **Placement**                                                                                     |                                                                                                                                                                         |                                                                                                                                                                                                                                                                        |                          |                                                                                                   |
| `DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT`                                                           | `PT55S`, logged at startup when you leave it out                                                                                                                        | One limit on all of a placement's checks together. A placement still checking when it runs out is refused with `PREFLIGHT_CHECKS_TIMED_OUT`, and nothing is saved. v8.71.0 had no limit                                                                                | 9.2.0                    | `request/fulfilment/PreflightChecksTimeout.java`                                                  |
| `DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED`                                                         | `false`                                                                                                                                                                 | On, resolution skips the copies of a supplier whose setup or last ping says it cannot supply, and the request goes to another library. See `docs/operational/supplier-preflight.md`                                                                                    | 9.2.0                    | `request/fulfilment/SupplierPreflightService.java`                                                |
| `DCB_REQUESTS_PREFLIGHT_CHECKS_CHOSEN_COPY_SUPPLIER_ENABLED`                                      | `true`                                                                                                                                                                  | With the supplier preflight on, a request for a copy staff chose is refused before it exists, with `SUPPLIER_CANNOT_SUPPLY` and the fix, when that copy's supplier cannot supply. Does nothing while the supplier preflight is off. `false` turns this check off alone | 9.2.0                    | `request/fulfilment/ChosenCopySupplierPreflightCheck.java`                                        |
| `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ACTIVE_WINDOW`                                  | unset: the earlier request's whole active life                                                                                                                          | With the duplicate-request check on, only active requests placed within this duration count as duplicates. Must be positive                                                                                                                                            | 9.2.0                    | `request/fulfilment/PreventDuplicateRequestsPreFlightCheck.java`                                  |
| `DCB_REQUESTS_STAFF_CLIENT_IDS`                                                                   | `dcb-admin,dcb-admin-for-libraries`                                                                                                                                     | The OIDC clients DCB Admin and DCB Admin for Libraries sign in through. A request placed with a token issued to one is recorded as placed by staff (`placedByStaff`) and is exempt from the duplicate-request check                                                    | 9.2.0                    | `security/StaffClients.java`                                                                      |
| **Access and accounts**                                                                           |                                                                                                                                                                         |                                                                                                                                                                                                                                                                        |                          |                                                                                                   |
| `DCB_SECURITY_ADMIN_UI_CLIENT_ID`                                                                 | unset: the role check is off                                                                                                                                            | DCB Admin's OIDC client. Set, a token issued to it needs a consortium role                                                                                                                                                                                             | 9.1.0                    | `security/AdminUiAccessConfig.java`                                                               |
| `DCB_SECURITY_ADMIN_UI_MODE`                                                                      | `WARN`                                                                                                                                                                  | `WARN` logs what the check would refuse and refuses nothing; `ENFORCE` refuses. Read only when the client id is set                                                                                                                                                    | 9.1.0                    | `security/AdminUiAccessConfig.java`                                                               |
| `DCB_IDENTITY_PROVIDER_KEYCLOAK_BASE_URL`                                                         | none                                                                                                                                                                    | The Keycloak in which DCB Admin creates DCB Admin for Libraries accounts                                                                                                                                                                                               | 9.2.0                    | `security/provisioning/IdentityProviderConfig.java`                                               |
| `DCB_IDENTITY_PROVIDER_KEYCLOAK_REALM`                                                            | none                                                                                                                                                                    | Its realm                                                                                                                                                                                                                                                              | 9.2.0                    | `security/provisioning/keycloak/KeycloakConfig.java`                                              |
| `DCB_IDENTITY_PROVIDER_KEYCLOAK_CLIENT_ID`                                                        | none                                                                                                                                                                    | The confidential client whose service account creates the accounts                                                                                                                                                                                                     | 9.2.0                    | `security/provisioning/IdentityProviderConfig.java`                                               |
| `DCB_IDENTITY_PROVIDER_KEYCLOAK_CLIENT_SECRET`                                                    | none, deliberately                                                                                                                                                      | That client's secret                                                                                                                                                                                                                                                   | 9.2.0                    | `security/provisioning/IdentityProviderConfig.java`                                               |
| **Discovery, new route: not used by EBSCO Locate ([§3](#3-adding-a-trusted-discovery-service))**  |                                                                                                                                                                         |                                                                                                                                                                                                                                                                        |                          |                                                                                                   |
| `DCB_DISCOVERY_ENABLED`                                                                           | `false`                                                                                                                                                                 | Turns on the patron `/discovery/` routes. Off, no other `DCB_DISCOVERY_*` setting is read                                                                                                                                                                              | 9.0.0                    | `security/discovery/DiscoveryConfig.java`                                                         |
| `DCB_DISCOVERY_AUDIENCE`                                                                          | `dcb`                                                                                                                                                                   | The audience a patron assertion must name. Give each deployment its own                                                                                                                                                                                                | 9.0.0                    | `security/discovery/DiscoveryConfig.java`                                                         |
| `DCB_DISCOVERY_MAX_ASSERTION_LIFETIME`                                                            | `PT2M`                                                                                                                                                                  | DCB's limit on an assertion's life, whatever its `exp` says                                                                                                                                                                                                            | 9.0.0                    | `security/discovery/DiscoveryConfig.java`                                                         |
| `DCB_DISCOVERY_TRUSTED_SERVICES_JSON`                                                             | none: no service is trusted                                                                                                                                             | The trusted discovery services, as one JSON array                                                                                                                                                                                                                      | 9.0.0                    | `security/discovery/DiscoveryTrustedServices.java`                                                |
| `DCB_DISCOVERY_MAX_LOANS`                                                                         | `25`                                                                                                                                                                    | The most loans one patron is told about. Each is one live read at the patron's library                                                                                                                                                                                 | 9.2.0                    | `security/discovery/DiscoveryConfig.java`                                                         |
| `DCB_DISCOVERY_LOAN_LOOKUP_CONCURRENCY`                                                           | `2`                                                                                                                                                                     | How many of those reads run at once, all against the patron's library                                                                                                                                                                                                  | 9.2.0                    | `security/discovery/DiscoveryConfig.java`                                                         |
| `DCB_DISCOVERY_LOAN_LOOKUP_TIMEOUT`                                                               | `8s`                                                                                                                                                                    | The limit on those reads                                                                                                                                                                                                                                               | 9.2.0                    | `security/discovery/DiscoveryConfig.java`                                                         |
| `DCB_DISCOVERY_MAX_ACCOUNT_ROWS`                                                                  | `25`                                                                                                                                                                    | The most loans, and the most charges, one account call returns                                                                                                                                                                                                         | 9.2.0                    | `security/discovery/DiscoveryConfig.java`                                                         |
| `DCB_DISCOVERY_ACCOUNT_LOOKUP_TIMEOUT`                                                            | `8s`                                                                                                                                                                    | The limit on an account call's read at the patron's library                                                                                                                                                                                                            | 9.2.0                    | `security/discovery/DiscoveryConfig.java`                                                         |
| `DCB_DISCOVERY_PATRON_RESOLVE_TIMEOUT`                                                            | `8s`                                                                                                                                                                    | The limit on one `POST /discovery/patrons/resolve`                                                                                                                                                                                                                     | 9.2.0                    | `security/discovery/DiscoveryConfig.java`                                                         |
| `DCB_DISCOVERY_PATRON_RESOLVES_PER_MINUTE`                                                        | `600`                                                                                                                                                                   | How many institution sign-ins one trusted service may resolve in a minute                                                                                                                                                                                              | 9.2.0                    | `security/discovery/DiscoveryConfig.java`                                                         |
| **Branding**                                                                                      |                                                                                                                                                                         |                                                                                                                                                                                                                                                                        |                          |                                                                                                   |
| `DCB_BRANDING_ASSETS_STORE`                                                                       | `database`                                                                                                                                                              | Where uploaded brand images live: `database` (Postgres), or `none`, which removes the upload and serve routes so that brand fields accept absolute URLs only                                                                                                           | 9.0.0                    | `core/branding/BrandAssetProperties.java`                                                         |
| `DCB_BRANDING_ASSETS_MAX_BYTES`                                                                   | `2097152` (2 MB)                                                                                                                                                        | The largest upload. It also sets `micronaut.server.multipart.max-file-size`, up from the framework's 1 MB, so your ingress must accept bodies this size                                                                                                                | 9.0.0                    | `core/branding/BrandAssetProperties.java`; `dcb/src/main/resources/application.yml`               |
| `DCB_BRANDING_ASSETS_MAX_DIMENSION`                                                               | `4096`                                                                                                                                                                  | The largest width or height, read from the image header before any pixel is decoded                                                                                                                                                                                    | 9.0.0                    | `core/branding/BrandAssetProperties.java`                                                         |
| `DCB_BRANDING_ASSETS_ORPHAN_GRACE_PERIOD`                                                         | `24h`                                                                                                                                                                   | How long an uploaded image that no brand uses is kept by the sweep                                                                                                                                                                                                     | 9.0.0                    | `core/branding/BrandAssetProperties.java`                                                         |
| `DCB_BRANDING_ASSETS_PUBLIC_PATH_PREFIX`                                                          | `/discovery/brand-assets/`                                                                                                                                              | The path every stored image URL starts with. Changing it after images exist breaks every stored URL                                                                                                                                                                    | 9.0.0                    | `core/branding/BrandAssetProperties.java`                                                         |
| `DCB_BRANDING_ASSETS_SWEEP_INTERVAL`                                                              | `24h`                                                                                                                                                                   | How often the orphan sweep runs                                                                                                                                                                                                                                        | 9.0.0                    | `core/branding/BrandAssetSweep.java`                                                              |
| `DCB_BRANDING_THEME_NAMES`                                                                        | `openRS,kInt`                                                                                                                                                           | The theme names a brand's default theme may be set to. Widen it only for a discovery front end that ships other themes                                                                                                                                                 | 9.0.0                    | `core/branding/BrandingProperties.java`                                                           |
| **Insights**                                                                                      |                                                                                                                                                                         |                                                                                                                                                                                                                                                                        |                          |                                                                                                   |
| `DCB_INSIGHTS_ENABLED`                                                                            | `true`                                                                                                                                                                  | The endpoints under `/insights`. `false` removes `/patrons/requests/stats/*` as well                                                                                                                                                                                   | 9.0.0                    | `dcb.insights` in `dcb/src/main/resources/application.yml`; `core/api/LegacyStatsController.java` |
| `DCB_INSIGHTS_COLLECTION_ANALYSIS_CACHE_TTL`                                                      | `15m`                                                                                                                                                                   | Every collection figure is at most this old                                                                                                                                                                                                                            | 9.0.0                    | `dcb/src/main/resources/application.yml`                                                          |
| `DCB_INSIGHTS_COLLECTION_ANALYSIS_STATEMENT_TIMEOUT`                                              | `10m`                                                                                                                                                                   | Postgres cancels a whole-catalogue pass that runs longer                                                                                                                                                                                                               | 9.2.0                    | `dcb/src/main/resources/application.yml`                                                          |
| **Availability and the index**                                                                    |                                                                                                                                                                         |                                                                                                                                                                                                                                                                        |                          |                                                                                                   |
| `DCB_INDEX_NUMBER_OF_REPLICAS`                                                                    | `1`                                                                                                                                                                     | Replicas of the shared index, applied at every start ([§2.6](#the-shared-index)). Zero or more                                                                                                                                                                         | 9.0.0                    | `indexing/SharedIndexConfiguration.java`                                                          |
| `DCB_LIVE_AVAILABILITY_BATCH_MAX_CLUSTERS`                                                        | `25`                                                                                                                                                                    | The most clusters one anonymous `GET /items/availability/batch` may ask about                                                                                                                                                                                          | 9.2.0                    | `core/api/LiveAvailabilityController.java`                                                        |
| `DCB_LIVE_AVAILABILITY_BATCH_CONCURRENCY`                                                         | `4`                                                                                                                                                                     | How many of those clusters are checked at once                                                                                                                                                                                                                         | 9.2.0                    | `core/api/LiveAvailabilityController.java`                                                        |
| `DCB_JOBS_AVAILABILITY_CONCURRENCY_MAPPING_WRITES`                                                | `3`                                                                                                                                                                     | How many location-mapping writes the availability job runs at once                                                                                                                                                                                                     | 9.2.0                    | `availability/job/AvailabilityCheckJobConfig.java`                                                |
| `DCB_JOBS_AVAILABILITY_MAPPED_RECHECK_GRACE_PERIOD`                                               | `P60D`; was `P30D`                                                                                                                                                      | How long before the job rechecks a location code it mapped                                                                                                                                                                                                             | default changed in 9.2.0 | `availability/job/AvailabilityCheckJobConfig.java`                                                |
| `DCB_JOBS_AVAILABILITY_RECHECK_GRACE_PERIOD`                                                      | `P14D`; was `P7D`                                                                                                                                                       | How long before it rechecks a location code it could not map                                                                                                                                                                                                           | default changed in 9.2.0 | `availability/job/AvailabilityCheckJobConfig.java`                                                |
| **Tracking**                                                                                      |                                                                                                                                                                         |                                                                                                                                                                                                                                                                        |                          |                                                                                                   |
| `DCB_TRACKING_TOO_LONG`                                                                           | `56d`                                                                                                                                                                   | How long a request may stay in one non-final status before automatic polling stops for it. A manual "check for updates" starts it again and restarts the clock                                                                                                         | 9.1.0                    | `tracking/TooLongPolicy.java`                                                                     |
| `DCB_TRACKING_SERVICE`                                                                            | unset: the v3 tracking service                                                                                                                                          | `v4` selects the lifecycle-evidence tracking service, which is optional and not yet the default                                                                                                                                                                        | 9.0.0                    | `tracking/TrackingServiceV4.java`; `tracking/MODULE.md`                                           |
| **Platform**                                                                                      |                                                                                                                                                                         |                                                                                                                                                                                                                                                                        |                          |                                                                                                   |
| `DCB_R2DBC_LEGACY_TRANSACTION_OPERATIONS_ENABLED`                                                 | `false`                                                                                                                                                                 | `true` and a restart bring back the transaction code from before Micronaut 5, while you investigate a problem. It is due to be removed                                                                                                                                 | 9.2.0                    | `dcb.r2dbc` in `dcb/src/main/resources/application.yml`                                           |
| `DCB_FEDERATION_LOCK_PROVIDER`                                                                    | unset: Hazelcast                                                                                                                                                        | `local` holds DCB's named locks in one process's memory, so it suits a single instance only                                                                                                                                                                            | 9.0.0                    | `dcb/src/main/java/services/k_int/federation/local/LocalFederatedLockService.java`                |
| `DCB_SOURCE_IMPORT_VANISHED_MAX_SHARE`                                                            | `0.10`                                                                                                                                                                  | A manual "vanished records" sweep that would remove more than this share of a Host LMS's bibs reports it and deletes nothing. Between 0 and 1                                                                                                                          | 9.2.0                    | `dataimport/SourceImportProperties.java`; `docs/polaris_notes.md`                                 |
| `CAPABILITIES_*` (no `DCB_` prefix)                                                               | placement `IMPERATIVE`, tracking `SCHEDULED_POLL`                                                                                                                       | Defaults for the whole instance for how requests are placed and tracked. The defaults are how v8.71.0 worked: leave them unset                                                                                                                                         | 9.0.0                    | `request/lifecycle/LifecycleCapabilitiesConfiguration.java`                                       |
| **NCIP peering between DCBs: leave unset unless your environment has OpenRS Appliance libraries** |                                                                                                                                                                         |                                                                                                                                                                                                                                                                        |                          |                                                                                                   |
| `DCB_PEER_AUTH_ENABLED`, `DCB_PEER_AUTH_NCIP_ENABLED`                                             | `false`, `false`                                                                                                                                                        | Both `true` turn on token authentication between NCIP peers. See `docs/ncip-peer-authentication.md`                                                                                                                                                                    | 9.0.0                    | `request/lifecycle/ncip/peerauth/DcbPeerAuthProperties.java`                                      |
| `DCB_PEER_AUTH_LOCAL_IDENTITY_*`                                                                  | `ID` `dcb`, `TOKEN_LIFETIME` `PT5M`; `ISSUER`, `SUBJECT`, `AUDIENCES`, `JWKS_URI`, `KEY_ID`, `PUBLIC_JWK` and `PRIVATE_JWK` none                                        | DCB's own signing identity towards its peers. `PRIVATE_JWK` is a secret                                                                                                                                                                                                | 9.0.0                    | `request/lifecycle/ncip/peerauth/DcbPeerAuthProperties.java`                                      |
| `DCB_PEER_AUTH_REMOTE_JWKS_CONNECT_TIMEOUT`, `DCB_PEER_AUTH_REMOTE_JWKS_READ_TIMEOUT`             | `PT2S`, `PT5S`                                                                                                                                                          | The limits on fetching a peer's keys                                                                                                                                                                                                                                   | 9.0.0                    | `request/lifecycle/ncip/peerauth/DcbPeerAuthProperties.java`                                      |
| `dcb.peer-auth.trusted-peers`                                                                     | none                                                                                                                                                                    | The peers, as a list: mount it as a file ([§1](#kubernetes-and-ecs))                                                                                                                                                                                                   | 9.0.0                    | `request/lifecycle/ncip/peerauth/DcbPeerAuthProperties.java`                                      |
| `DCB_NCIP_SYSTEM_ID`, `DCB_NCIP_AGENCY_ID`                                                        | none                                                                                                                                                                    | DCB's own NCIP identity. A Host LMS that places or tracks through NCIP fails without them, on first use                                                                                                                                                                | 9.0.0                    | `request/lifecycle/ncip/NcipIdentityConfiguration.java`                                           |
| `DCB_PROFILE_REGISTRATION_*`                                                                      | `NODE_NAME` `DCB`, `INVITATION_TTL` `30m`, `SYNC_INTERVAL` `15m`, `SYNC_POLL_INTERVAL` `1m`, `ALLOW_HTTP` and `ALLOW_PRIVATE_ADDRESSES` `false`; `PUBLIC_BASE_URL` none | Registering this DCB with a peer, and keeping that membership up to date                                                                                                                                                                                               | 9.0.0                    | `request/lifecycle/ncip/profile/application/DcbProfileRegistrationProperties.java`                |

---

## Appendix C. Roles and routes

### `/patrons/requests/**` (since v9.0.0)

At v8.71.0, `PatronRequestController` defaulted to `IS_AUTHENTICATED`, so several methods were open
to any principal the realm would authenticate. Every method now has an explicit role set:

| Endpoint                                                                        | Roles                                                                 |
|---------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| Class default, which covers `/{id}/update` (and an ownership check)             | `CONSORTIUM_ADMIN`, `ADMIN`, `LIBRARY_ADMIN`, `INTERNAL_API`          |
| `/place`, `/place/expeditedCheckout`, `/place/walkup`                           | the above plus `LIBRARY_READ_ONLY`                                    |
| `/{id}/transition/cleanup`, `/{id}/transition/confirm-returned`                 | `CONSORTIUM_ADMIN`, `ADMIN`, `LIBRARY_ADMIN`, with an ownership check |
| `/{id}/rollback`                                                                | `CONSORTIUM_ADMIN`, `ADMIN`                                           |
| `GET /{hostLmsCode}`                                                            | `CONSORTIUM_ADMIN`, `ADMIN`                                           |
| `GET /patrons/{hostLmsCode}/requests` (legacy) and `GET /{?pageable*}` (legacy) | `ADMIN` only                                                          |

A missing token gets `400`, and a token without the role gets `401`. Service-to-service callers
want `INTERNAL_API`.

Source: `@Secured` on each method of `dcb/src/main/java/org/olf/dcb/core/api/PatronRequestController.java`;
`dcb/src/main/java/org/olf/dcb/security/RoleNames.java` (`ADMINISTRATOR = "ADMIN"`);
`dcb/src/main/java/org/olf/dcb/security/DcbAuthorizationExceptionHandler.java`. Every route's
security is checked by `ApiSecurityArchitectureTests`.

### EBSCO Locate

Locate calls DCB with a Keycloak service account holding `ADMIN`, and names the patron in the
request body. The Locate team confirmed on 20 October 2026 that its account holds `ADMIN`. No DCB
setting is involved, and `DCB_DISCOVERY_ENABLED` has no effect on this route. The routes Locate uses
are listed in `docs/discovery-service-approach.md` §8.3. Three of them changed after v8.71.0:

| Route                                                           | Change                                                                                                                                                                                                                                        | Since |
|-----------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------|
| `POST /v2/patron/auth/lookup`                                   | Needs one of `ADMIN`, `INTERNAL_API`, `CONSORTIUM_ADMIN`, `LIBRARY_ADMIN` or `LIBRARY_READ_ONLY`; it was anonymous. `ADMIN` qualifies, so Locate must send its token on this call                                                             | 9.2.0 |
| `GET /patrons/requests/patrons/{hostLmsCode}/requests?barcode=` | `ADMIN` only, and marked `LEGACY` in the source; v8.71.0 also let `CONSORTIUM_ADMIN` in. The barcode is matched exactly: v8.71.0 matched part of it, so a partial or reformatted barcode that used to find a patron's requests now finds none | 9.0.0 |
| `POST /graphql` (agencies)                                      | Needs a staff role in the data fetcher; v8.71.0 answered any signed-in caller. `ADMIN` is a staff role, so Locate is not affected                                                                                                             | 9.2.0 |

`GET /patrons/requests`, the request list, was `ADMIN` only at v8.71.0 and still is; it is now marked
`LEGACY`. `POST /patrons/requests/place` keeps `ADMIN` in its now explicit role set. The credential
checks, `POST /patron/auth` and `POST /v2/patron/auth`, stay anonymous, and are open to every
discovery service, not only Locate.

Before you upgrade:

1. In your access logs, count `POST /v2/patron/auth/lookup` requests that carry no `Authorization`
   header. It must be 0.
2. Whoever does QA for the hosting provider tests Locate from end to end against v9.2.0 on a test
   environment: sign in with a library card, and through SAML where libraries use it; see live
   availability; choose a pickup location; place a request, and find it in DCB Admin.

Source: `@Secured` in `dcb/src/main/java/org/olf/dcb/core/api/PatronAuthV2Controller.java` and
`.../PatronRequestController.java`; `findActiveRequestsForPatronByBarcode` and
`findAllRequestsForPatronByBarcode` in `dcb/src/main/java/org/olf/dcb/storage/PatronRequestRepository.java`;
`getAgenciesDataFetcher` in `dcb/src/main/java/org/olf/dcb/graphql/DataFetchers.java` (`GraphQLRoles.STAFF`).

### Anonymous routes

These routes are anonymous by design. The limits in DCB are their only protection inside the
application:

| Route                                               | Why anonymous                                                                                                                 | Limit in DCB                                                                              |
|-----------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `GET /items/availability`, `/items/availability-v2` | discovery layers ask before anyone signs in                                                                                   | one cluster per call; a Host LMS that times out is reported in `errors[]`                 |
| `GET /items/availability/batch`                     | the same, for a page of results                                                                                               | 25 clusters, 4 at a time                                                                  |
| `/discovery/brand-assets/{key}`                     | brand images show before sign-in. Responses are `Cache-Control: public, max-age=31536000, immutable`: let your CDN cache them | none needed                                                                               |
| `POST /v2/patron/auth`                              | the credential check                                                                                                          | none                                                                                      |
| `POST /ncip/v2_02`                                  | NCIP peers                                                                                                                    | peer authentication, whose default mode is `INSECURE`. Block it if you have no NCIP peers |

If you see abuse, rate-limit `/items/availability*` (count a batch as its number of clusters),
`/discovery/*` and `/v2/patron/auth` per client IP at your ingress.

Source: `dcb/src/main/java/org/olf/dcb/core/api/LiveAvailabilityController.java`;
`dcb/src/main/java/org/olf/dcb/request/lifecycle/ncip/NcipController.java`;
`dcb/src/main/java/org/olf/dcb/request/lifecycle/ncip/peerauth/NcipPeerAuthProfile.java`.

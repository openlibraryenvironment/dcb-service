# Running DCB locally

This is a work in progress document that aims to assist local development on dcb-service.
It is written after the Micronaut 5 upgrade to highlight certain changes, and will be updated continuously.
It may also be useful for developers working with AI-assisted development tools, in explaining how local development works in this repo.

## Quick start

```bash
./scripts/local_dev.sh
```

This is a new script that starts PostgreSQL **and Elasticsearch 9** in Docker, waits for both to
report healthy, checks you can actually authenticate against the database,
exports the datasource settings and runs DCB.

Variations:

```bash
./scripts/local_dev.sh --index os2     # OpenSearch 2.x -- closest to production
./scripts/local_dev.sh --index none    # Postgres only -- fastest loop
```

Other flags: `--down` (stop everything), `--no-run` (bring dependencies up
without starting DCB), `--help`.

### Starting from a clean slate

```bash
./scripts/local_dev.sh --fresh          # database AND index
./scripts/local_dev.sh --fresh-db       # database only
./scripts/local_dev.sh --fresh-index    # index only
```

`--fresh-index` removes only the volume belonging to the **active `--index`
mode**, so wiping while on `--index os2` leaves an ES 9 index intact. With
`--index none` there is nothing to remove and the script says so.

If you wipe the database *without* the index, be aware
that the index still holds documents whose cluster
records no longer exist, so searches return hits that resolve to nothing. Use
plain `--fresh` unless you specifically want to keep one of them.

Never use `docker compose -f scripts/docker-compose.yml down -v` for this — `-v`
removes *every* named volume in the file, taking both indices and the database.

### Turning scheduled tasks off

If you want to reduce noise or system load, you may want to turn some scheduled tasks off. Here's how you do that.

```bash
./scripts/local_dev.sh --skip-tasks AvailabilityCheckJob,IngestJob
./scripts/local_dev.sh --no-scheduled-tasks          # disable all of them
```

`--skip-tasks` sets `dcb.scheduled-tasks.skipped`, which matches on the **simple
class name of the type declaring the `@AppTask @Scheduled` method**:

```
AvailabilityCheckJob  ClusterHousekeepingService  ConfigurationService
DcbProfileMembershipSyncJob  HealthMonitorTask  HouseKeepingService
IndexSynch  IngestJob  SourceRecordService  StatsService  TrackingScheduler
```

Note **`TrackingScheduler`, not `TrackingServiceV3`** — the annotations moved
onto a dedicated scheduler class, so the older name silently does nothing. The
set depends on configuration, and the startup log is authoritative:

```bash
grep AppTaskAwareScheduledMethodProcessor <log>   # "Continue to process" / "explicitly skipped"
```

`AvailabilityCheckJob` is skipped automatically when you run `--index none`,
because it requires the shared index and otherwise fails on a timer with
`Failed to inject value for parameter [sharedIndexService]`. Passing
`--skip-tasks` yourself overrides that.

### Office hours

`AvailabilityCheckJob` and `IndexSynch` are wrapped in
`subscribeOnlyOutsideOfficeHours`, so they run **outside** the configured
window and pause inside it. The default is a deliberately narrow window
inherited from the old `run_dev_server_fs.sh`, which means they run nearly all
the time while the gate still gets exercised:

```bash
./scripts/local_dev.sh                                      # 17:00:00-17:50:00 UTC (default)
./scripts/local_dev.sh --office-hours 09:00:00-17:00:00     # a realistic working day
./scripts/local_dev.sh --no-office-hours                    # no gate at all
```

`DCB_OFFICEHOURS_START` / `DCB_OFFICEHOURS_END` work too; the flag takes precedence over the
environment variables.

Two things worth knowing:

- **Times are UTC.** `OfficeHours` parses them as `LocalTime` and calls
  `atOffset(ZoneOffset.UTC)`, so they are not interpreted in your local zone.
- **Unset is not the same as "all day".** `isInsideHours()` short-circuits to
  `false` when neither value is supplied — "No office hours data, assuming
  outside hours" — so `--no-office-hours` means those jobs *never* pause, which
  is the opposite of what leaving them blank might suggest.

Then load some configuration into it:

```bash
cp scripts/profiles/example.env.template scripts/profiles/local.env
$EDITOR scripts/profiles/local.env
./scripts/dcb_setup.sh --profile local --bundle example
```

### What changed: Test Resources is gone

**Micronaut Test Resources used to provision PostgreSQL automatically on every
`./gradlew run`.** The `io.micronaut.test-resources` plugin has been removed from
`dcb/build.gradle`, so nothing does that any more. If you run `./gradlew run`
with no database available you get:

```
Error starting Micronaut server: Bean definition [io.micronaut.data.r2dbc.config.R2dbcSchemaGenerator]
could not be loaded: ... Message: No value found for host
```

Two things replaced it:

- `dcb/src/main/resources/application-development.yml` now carries datasource
  defaults (`localhost:5432`, database/user/password all `dcb`) matching the
  `persistent-db` compose profile. `build.gradle` sets
  `micronaut.environments=development` for the `run` task, so only local runs
  see them — deployed instances are unaffected. Every value is overridable via
  `DB_HOST`, `DB_PORT`, `DB_DATABASE`, `DB_USER`, `DB_PASSWORD`.
- `scripts/local_dev.sh` starts the container and sanity-checks it.

The practical consequence: **your database now survives restarts.** That is
usually what you want, but it does mean reference data accumulates and
migrations are not replayed from scratch. See "Getting a throwaway database
back" below if you preferred the old behaviour.

The test suite is unaffected — it drives Testcontainers directly through
`DcbTestContainerContextBuilder`, and still gets a fresh database per run.

### Which shell

`local_dev.sh` is bash, like everything else in `scripts/`. On Linux and macOS
just run it.

**On Windows, it's usually better to run it from Git Bash, not WSL.** A WSL distro typically has its own
Docker daemon, so a script run there manages different containers from the ones
Docker Desktop shows you — and, more subtly, a PostgreSQL installed inside WSL
will occupy `127.0.0.1:5432` on the Windows side via `wslrelay` and shadow the
container's published port. The script detects that and tells you what to do; see
the troubleshooting entry below.

### Getting a throwaway database back

To reproduce the old Test Resources behaviour — an empty database on every run:

```bash
./scripts/local_dev.sh --fresh-db
```

That removes the `scripts_dcb_pg_data` volume before starting, so Flyway
migrates from nothing and you re-bootstrap reference data (`scripts/bootstrap-dev.sh`
and friends) as you used to.

To tear everything down by hand:

```bash
./scripts/local_dev.sh --down                       # stop containers, keep data
docker volume rm scripts_dcb_pg_data                # destroy the database
```

Do **not** use `docker compose -f scripts/docker-compose.yml down -v` for this:
`-v` removes every named volume in the file, so it takes your Elasticsearch and
OpenSearch indices with it.

## Prerequisite: JDK 25

Since the Micronaut 5 upgrade, DCB builds and runs on **JDK 25** (Gradle 9.3,
Micronaut 5.0.x). Java 17/21 will not compile it.

The repo is configured to provision the toolchain itself:

- `gradle/gradle-daemon-jvm.properties` pins the Gradle daemon to Java 25
- `settings.gradle` applies `org.gradle.toolchains.foojay-resolver-convention`,
  which lets Gradle download that JDK if it is not installed

If you see a cryptic failure about class file versions or an unsupported
`--release`, check `java -version` and `JAVA_HOME` before anything else.

## Elasticsearch must be 9.x — read this first

Micronaut 5 upgraded `micronaut-elasticsearch` to 6.0.0, which brings
**`elasticsearch-java` 9.4.0**. Elastic's clients enforce a major-version
compatibility header, and an 8.x server rejects it outright:

```
Accept version must be either version 8 or 7, but found 9.
Accept=application/vnd.elasticsearch+json; compatible-with=9
```

Against `folio-es:8.7.0` this surfaces at startup as a confusing error, because
`indices.exists` is a HEAD request and a 400 carries no body:

```
TransportException: node: http://localhost:9200/, status: 400,
[es/indices.exists] Expecting a response body, but none was sent
```

**A 9.x client cannot talk to an 8.x server.** The pre-existing `folio-es:8.7.0`
image no longer works for local development; use the `es9` profile below.

The shared index needs the **ICU analysis plugin** (`sharedIndex/settings-2.json`
defines an `icu_folding_nopunc` analyzer over `icu_tokenizer`/`icu_folding`), so a
stock `elasticsearch:9.x` image is not enough either. `scripts/es9-icu.Dockerfile`
adds the plugin — that is the 9.x counterpart to what `folio-es` did for 8.7.

### OpenSearch has no such restriction

DCB supports either backend, and **OpenSearch is unaffected by the version wall
above**. `opensearch-java` does not send Elastic's compatibility header, and the
3.9.0 client this branch uses has been verified working against OpenSearch
**2.14.0** and **2.19.1** — index created from the checked-in settings and
mappings, `icu_folding_nopunc` tokenising correctly on both.

Since production runs OpenSearch, the `os2` profile is the closest local match to
production and is often the better choice:

```bash
docker compose -f scripts/docker-compose.yml --profile os2 up -d --build opensearch
export OPENSEARCH_HTTP_HOSTS="http://localhost:9200"   # and leave ELASTICSEARCH_HTTP_HOSTS unset
```

Configure **one** backend, not both. Both profiles bind port 9200, so only one can
run at a time.

## What DCB actually needs

| Dependency | Required? | Who starts it | Survives a restart? |
|---|---|---|---|
| **PostgreSQL** | Always | **You**, via `local_dev.sh` or docker compose | Yes — named volume |
| **Elasticsearch 9.x _or_ OpenSearch 2.x** | Only when the shared index is on | **You**, via `local_dev.sh --index` or docker compose | Yes — named volume |

Nothing is provisioned automatically any more. `./gradlew run` on its own starts
no containers at all: if Postgres is not up it fails with `No value found for
host`, and if the shared index is enabled without a search backend it fails with
`Connection refused`. `local_dev.sh` exists so you do not have to remember that.

## The three modes

### Mode A — no index (simplest; no Elasticsearch at all)

The whole shared-index stack is gated on `SharedIndexConfiguration`, which is
`@Requires(property = "dcb.index")`. Leave `DCB_INDEX_NAME` unset and none of
those beans are created, so DCB never opens an ES connection:

```bash
./scripts/local_dev.sh --index none
```

or, by hand:

```bash
docker compose -f scripts/docker-compose.yml --profile persistent-db up -d postgres
unset DCB_INDEX_NAME DCB_INDEX_USERNAME DCB_INDEX_PASSWORD ELASTICSEARCH_HTTP_HOSTS
./gradlew run
```

Use this unless you are specifically working on indexing, discovery or search.
It is the fastest loop and has the fewest moving parts. Postgres is still
required — "no index" does not mean "no dependencies".

### Mode B — with a search backend (the default)

`./scripts/local_dev.sh` does the whole of this. By hand:

```bash
docker compose -f scripts/docker-compose.yml --profile persistent-db up -d postgres
docker compose -f scripts/docker-compose.yml --profile es9 up -d --build elasticsearch9
docker compose -f scripts/docker-compose.yml --profile es9 ps   # wait for "healthy"
./gradlew run
```

The first run builds the ICU image and takes a couple of minutes. The legacy
`elasticsearch` service (8.7.0) is still defined for reference, but **DCB can no
longer talk to it** — see the version note above. Both bind port 9200, so stop one
before starting the other. They use separate volumes, so switching does not
destroy either dataset, but ES 9 will not read the 8.x data directory: the index
has to be rebuilt via `/admin/reindex`.

If you run with `--fresh-db` the database is wiped but the ES index is not, so
expect to re-bootstrap reference data (see `scripts/bootstrap-dev.sh` and
friends) while the index retains documents from previous runs. **An index full of
documents whose cluster records no longer exist in the database is normal in that
case** and is not a bug worth chasing.

### Mode C — pointing at a database you manage yourself

`application-development.yml` supplies defaults, and everything in it is
env-overridable, so redirecting DCB at some other PostgreSQL is just:

```bash
export DB_HOST=my-host
export DB_PORT=5432
export DB_DATABASE=dcb
export DB_USER=dcb
export DB_PASSWORD=secret
./gradlew run
```

The lower-level Micronaut properties still work if you need finer control —
`DATASOURCES_DEFAULT_URL` / `R2DBC_DATASOURCES_DEFAULT_URL` and their
`_USERNAME` / `_PASSWORD` companions. Both datasources must resolve to the same
database: Flyway migrates over JDBC while the application reads and writes over
R2DBC.

> Verified on this branch: with the container on a free port, `./gradlew run`
> boots end-to-end — `Startup completed in 5569ms. Server Running:
> http://localhost:8080`, Flyway having migrated over JDBC and R2DBC connected.

## Loading configuration: `dcb_setup.sh`

A running DCB with an empty database does nothing useful. `scripts/dcb_setup.sh`
loads host LMS records, agencies, libraries, rulesets, locations and mappings
into an environment.

You make **two independent choices**, in this order:

| | Flag | Where it lives | Committed? | Answers |
|---|---|---|---|---|
| 1. Environment profile | `--profile` | `scripts/profiles/<name>.env` | **No** (gitignored) | *Where* does it go — target URL, Keycloak, API keys |
| 2. Config profile | `--config` | `scripts/config/<bundle>/` | Yes | *What* gets sent — which library groups |

`--config` is the replacement for the old `INSTALL_ALMA` / `INSTALL_KOHA` flags.
Bundle files reference `${VARIABLES}` that the environment profile supplies,
which is what lets configuration live in the repo while secrets do not.

(There is a third, rarely needed flag: `--bundle` picks which *collection* of
config profiles to use. It defaults to `private-local` if that exists, otherwise
`example`.)

### Getting started

```bash
cp scripts/profiles/example.env.template scripts/profiles/local.env
$EDITOR scripts/profiles/local.env    # Keycloak + any bundle variables
./scripts/dcb_setup.sh --list         # environment profiles, bundles, config profiles
./scripts/dcb_setup.sh --profile local --config all --dry-run
./scripts/dcb_setup.sh --profile local --config all
```

`--dry-run` validates the profile and walks the selection without sending
anything — worth running first, since it catches missing variables before any
partial configuration is applied.

### Config profiles

Each is a named set of vendor groups:

```bash
./scripts/dcb_setup.sh --profile local --config all       # every library
./scripts/dcb_setup.sh --profile local --config folio     # FOLIO libraries only
./scripts/dcb_setup.sh --profile local --config alma      # Alma only
./scripts/dcb_setup.sh --profile local --config sierra    # Sierra only
./scripts/dcb_setup.sh --profile local --config polaris   # Polaris only
./scripts/dcb_setup.sh --profile local --config koha      # Koha only
./scripts/dcb_setup.sh --profile local --config mobius    # cross-vendor: polaris + folio
```

A group is just a directory under the bundle holding that vendor's whole
configuration — host LMS, agencies, libraries, rulesets, uploads:

```
scripts/config/private-local/
  folio/          10-hostlms/  20-agencies/  25-locations/  30-graphql/  50-…  60-…
  alma/           10-hostlms/  20-agencies/  30-graphql/    50-…  60-…
  sierra/         …
  polaris/        …  40-object-rulesets/  65-numeric-mappings-upload/
  koha/           10-hostlms/
  zz-consortium/  70-graphql/  80-group-membership/
  profiles.conf
```

Groups named `zz-*` are **shared**: applied for every config profile, and last.
The consortium lives there because it has to be created after the libraries it
groups.

`profiles.conf` maps a name to a comma-separated group list, which is how
cross-vendor selections work:

```
folio=folio
alma=alma
mobius=polaris,folio
```

`all` is built in and means every non-shared group, so it needs no entry.

### Adding a new vendor

Two steps, no script changes:

1. `mkdir -p scripts/config/private-local/koha/10-hostlms` and drop the config
   files in, using the same `NN-<kind>` step directories as the other groups.
2. Add `koha=koha` to `profiles.conf`.

`--config koha` then works, and `--config all` picks it up automatically. A
group directory that exists but is empty is valid — you still get the shared
consortium — which is how the `koha` placeholder currently behaves.

### Bundle layout

Directories are applied in lexical order; the `NN-` prefix encodes dependency
order, and the suffix selects the endpoint:

Inside a group, step directories are applied in lexical order; the `NN-` prefix
encodes dependency order and the suffix selects the endpoint:

```
scripts/config/<bundle>/<group>/
  10-hostlms/                 CODE.json          -> POST /hostlmss
  20-agencies/                code.json          -> POST /agencies
  25-locations/               code.json          -> POST /locations
  30-graphql/                 10-library.graphql -> POST /graphql
  40-object-rulesets/         name.json          -> POST /object-rulesets
  50-locations-upload/        CODE.tsv           -> POST /locations/upload
  60-mappings-upload/         CODE.tsv           -> POST /uploadedMappings/upload
  65-numeric-mappings-upload/ CODE.tsv           -> POST /uploadedMappings/upload
  70-graphql/                 20-consortium.json -> POST /graphql
  80-group-membership/        name.json          -> resolve group, join libraries
```

**Adding a library means adding a file. You never edit the script.** For the
upload steps the filename is the host LMS code, so `SLOUC.tsv` uploads with
`code=SLOUC`; both `.csv` and `.tsv` are accepted. A kind can be used more than
once — `30-graphql` for libraries and `70-graphql` for the consortium.

GraphQL mutations are plain multi-line `.graphql` documents — `jq` does the JSON
escaping. The old script hand-escaped each mutation onto a single line, which is
why they were effectively read-only.

`group-membership` is the one step that cannot be a static payload, since the
group ID only exists at runtime. It takes
`{"groupCode": "...", "libraryQuery": "..."}`, resolves the group **by code**,
and joins every matching library. The old script captured the ID from the
create-group response, which meant a second run always failed.

### Setting up a Foundation host (the Evergreen example)

`FoundationClient` covers profiles A and B: a library whose primitives come from
**NCIP** (or SIP2), optionally with a **vendor-API override** for the operations
the protocol handles badly. It stays **imperative** — no `capabilities` role
block, so placement defaults to imperative and tracking to scheduled-poll, the
same as Sierra or FOLIO.

```bash
./scripts/dcb_setup.sh --profile local --bundle example --config foundation --dry-run
```

```
scripts/config/example/foundation/
  10-hostlms/EXAMPLE_EVERGREEN.json
  20-agencies/EXAMPLE_EVERGREEN.json
  30-graphql/10-library-example-evergreen.graphql
```

#### The composition

```json
{
  "lmsClientClass": "org.olf.dcb.core.interaction.foundation.FoundationClient",
  "clientConfig": {
    "capabilities": {
      "imperative": {
        "base-protocol": "NCIP",
        "ncip-endpoint-url": "https://evergreen.example.org/ncip",
        "overrides": { "renew": "EvergreenExampleCustomOverride" }
      }
    },
    "evergreen-api-url": "https://evergreen.example.org",
    "ncip": {
      "fromAgency": "DCB-CENTRAL",
      "toAgency": "EXAMPLE_EVERGREEN",
      "appProfileType": "EZBORROW"
    }
  }
}
```

| Key | Notes |
|---|---|
| `capabilities.imperative.base-protocol` | `NCIP` (default) or `SIP2`. Selects the adaptor everything falls back to. |
| `capabilities.imperative.ncip-endpoint-url` | Outbound NCIP URL. Falls back to the legacy `ncip.endpoint`. |
| `capabilities.imperative.overrides` | **Only two keys exist**: `renew` → `CirculationStrategy`, `patron` → `PatronStrategy`. Anything else is silently ignored. |
| `evergreen-api-url` | Read from the **top level** of `clientConfig`, not from the capabilities block — `EvergreenExampleCustomOverride` does `getClientConfig().get("evergreen-api-url")` directly. It appends `/osrf-gateway-v1`. |
| `ncip.fromAgency` / `toAgency` / `appProfileType` | Optional. Default to `DCB-CENTRAL`, the host code, and `EZBORROW`. |

**The `renew` key swaps the whole strategy, not one method.** `CirculationStrategy`
covers `checkOutItem`, `renew` and `getItem`, so an override must handle all
three. `EvergreenExampleCustomOverride` shows the intended shape: a bespoke
OpenSRF call for `renew`, and delegation to a private `NcipAdaptor` for the rest.

There is no `capabilities.<role>.strategy` block here, and that is deliberate —
a Foundation host is imperative, and adding one would opt it into the
declarative path.

#### Writing your own override

An override bean is `@Named` with the value you put in the `overrides` map, and
takes the host as a `@Parameter("hostLms")` constructor argument:

```java
@Bean
@Named("MyLibraryRenewalOverride")
public class MyLibraryRenewalOverride implements CirculationStrategy {
	public MyLibraryRenewalOverride(@Parameter("hostLms") HostLms lms, HttpClient httpClient) { … }
}
```

Two constraints worth knowing, both of which are covered by
`FoundationOverrideResolutionTests`:

- The `@Parameter` argument makes the bean **parameterized**, so DCB resolves it
  with `createBean(...)` rather than `findBean(...)`. That is why the host is
  available to your constructor at all.
- Inject **`io.micronaut.serde.ObjectMapper`**, not Jackson's. DCB registers no
  Jackson `ObjectMapper` bean, so injecting one makes the override
  uninstantiable. Annotate DTOs `@Serdeable`.

A name that matches no bean fails fast with
`Missing override bean: <name>` while the client is being built.

### Setting up an ORS Appliance host

An ORS Appliance is the "profile D" integration: a library that can expose
neither NCIP nor a vendor API, fronted by an external appliance that speaks NCIP
v2.02 on its behalf. It differs from every other host in the bundle because it
is **declarative** — DCB sends one coarse `RequestItem`/`AcceptItem` and the
appliance performs the local choreography — and **event-driven**, so DCB does
not poll it.

The example bundle ships a working group:

```bash
./scripts/dcb_setup.sh --profile local --bundle example --config ors --dry-run
```

```
scripts/config/example/ors/
  10-hostlms/EXAMPLE_ORS.json
  20-agencies/EXAMPLE_ORS.json
  30-graphql/10-library-example-ors.graphql
```

Copy it into your own bundle to adapt:

```bash
cp -r scripts/config/example/ors scripts/config/private-local/ors
echo 'ors=ors' >> scripts/config/private-local/profiles.conf
```

#### What makes the host record different

```json
{
  "code": "EXAMPLE_ORS",
  "lmsClientClass": "org.olf.dcb.request.lifecycle.ncip.ORSApplianceHostLMS",
  "ingestSourceClass": "org.olf.dcb.core.interaction.ors.ORSApplianceOaiPmhIngestSource",
  "clientConfig": {
    "capabilities": {
      "supplying-agency-request": { "strategy": "declarative", "protocol": "ncip-v202" },
      "borrowing-agency-request": { "strategy": "declarative", "protocol": "ncip-v202" },
      "supplier-tracking":        { "mode": "event-driven",    "protocol": "ncip-v202" },
      "borrower-tracking":        { "mode": "event-driven",    "protocol": "ncip-v202" }
    },
    "ncip-endpoint-url": "https://appliance.example.org/ncip/v2_02",
    "ncip-system-id": "EXAMPLE-APPLIANCE",
    "ncip-agency-id": "EXAMPLE-APPLIANCE",
    "ncip-peer-auth-mode": "INSECURE",
    "base-url": "https://appliance.example.org",
    "tenant-id": "example-tenant",
    "metadata-prefix": "marc21"
  }
}
```

| Key | Why |
|---|---|
| `capabilities.*` | Without this block the host defaults to **imperative + scheduled-poll** and nothing declarative happens. `declarative` and `event-driven` each *must* name a `protocol`, or startup fails fast. |
| `ncip-endpoint-url` | Where DCB POSTs outbound NCIP. Required. |
| `ncip-system-id` | The **appliance's** NCIP identity. Required. Legacy spelling `ncipSystemId` also accepted. |
| `ncip-agency-id` | Optional; defaults to the system id. |
| `ncip-peer-auth-mode` | `INSECURE` (default) or `JWT_REQUIRED`. |
| `tenant-id` | OAI ingest path is derived as `/ors-appliance/api/V1/public/<tenant-id>/oai`. Set `oai-endpoint-url` instead to override the path outright — one of the two is required, or ingest construction throws. |

#### DCB's own NCIP identity — required, and not part of the bundle

Declarative NCIP will not work until DCB knows who *it* is. This is instance-wide
application config, not a profile variable:

```bash
export DCB_NCIP_SYSTEM_ID=DCB-CENTRAL
export DCB_NCIP_AGENCY_ID=DCB-CENTRAL
./scripts/local_dev.sh
```

`NcipIdentityConfiguration` throws `dcb.ncip.system-id is required` on first use
if these are unset, so the failure surfaces when a request is placed rather than
at startup.

#### Inbound events

Because tracking is event-driven, DCB sets `nextScheduledPoll = null` for these
requests and waits. The appliance drives progress by POSTing NCIP messages
(`ItemShipped`, `RequestItemResponse`, …) back to **`POST /ncip/v2_02`**, which
validates against the XSD and advances the workflow idempotently. If a request
appears stuck, check that the appliance is actually calling back — no amount of
waiting will make DCB poll it.

For the full reference — the declarative flow step by step, JWT/JWKS peer
authentication, and end-to-end test recipes — see
[`docs/unified-host-interaction-integration-guide.md`](unified-host-interaction-integration-guide.md)
sections 4, 6 and 7.

### Configuration you cannot genericise

`scripts/config/private-*/` is gitignored. Copy the example bundle and work
there if you have config with real institution data that does not belong in the
repo:

```bash
cp -r scripts/config/example scripts/config/private-mine
```

The contents of the old `myScripts/local_setup.sh` have been migrated to
`scripts/config/private-local` (11 host LMS, 10 agencies, 10 libraries, the
MOBIUS consortium, and the location/mapping uploads), grouped by vendor, with
its credentials in `scripts/profiles/local.env`. Neither is tracked. It is the
default bundle, so:

```bash
./scripts/dcb_setup.sh --profile local --config all --dry-run
./scripts/dcb_setup.sh --profile local --config all
```

Note that even in a private bundle the secrets stay in the profile as
`${VARIABLES}` rather than being inlined — one place to rotate a key, and the
bundle stays shareable if it is ever genericised.

### Behaviour worth knowing

- Every request's HTTP status is checked and reported. The old script piped
  responses straight to stdout, so a 401 looked exactly like a 201.
- GraphQL returns 200 even when a mutation fails, so the response body is
  inspected for `errors` as well.
- A 409 is reported as `skip (already exists)`, not a failure, so re-running a
  bundle is safe.
- Missing variables are a hard failure *before* anything is sent, rather than a
  silently empty API key.
- Exit status is non-zero if anything failed.

## Troubleshooting

### `No value found for host` on startup

```
Error starting Micronaut server: Bean definition [io.micronaut.data.r2dbc.config.R2dbcSchemaGenerator]
could not be loaded: ... Message: No value found for host
```

No database. Test Resources used to provide one; it no longer exists. Run
`./scripts/local_dev.sh`, or start the container yourself with
`docker compose -f scripts/docker-compose.yml --profile persistent-db up -d postgres`.

### `FATAL: password authentication failed for user "dcb"`

Two distinct causes, and they look identical.

**1. Another PostgreSQL owns the port.** The container is healthy and the
credentials are right, but you are not talking to it. A natively installed
PostgreSQL — or one inside WSL, which Docker Desktop exposes on the Windows side
through `wslrelay` — shadows the container's published port. The giveaway is
that connecting *inside* the container works while connecting through the host
port does not:

```bash
docker exec dcb-postgres psql -U dcb -d dcb -c 'select 1'              # works
docker run --rm -e PGPASSWORD=dcb postgres:18 \
  psql -h host.docker.internal -p 5432 -U dcb -d dcb -c 'select 1'     # fails
```

Find the real owner:

```bash
ss -lntp | grep 5432                                            # Linux/WSL
Get-NetTCPConnection -LocalPort 5432 -State Listen              # PowerShell
```

Then stop that server, or move ours out of the way — the port is configurable
and the script passes it through to DCB automatically:

```bash
DCB_PG_PORT=5433 ./scripts/local_dev.sh
```

`local_dev.sh` checks for this before handing over, so you get the message above
rather than a stack trace.

**2. The volume predates the current credentials.** `POSTGRES_PASSWORD` is only
applied when the container initialises an *empty* data directory. A volume
created earlier with a different password keeps the old one forever. Realign it:

```bash
docker exec dcb-postgres psql -U dcb -d dcb -c "ALTER ROLE dcb WITH PASSWORD 'dcb';"
```

`local_dev.sh` does this on every start, so it should not bite you there.

### `ConnectException: Connection refused` on startup

```
Error starting Micronaut server: java.net.ConnectException: Connection refused: getsockopt
```

Nearly always Elasticsearch: the shared index is enabled but ES is not running.

```bash
docker ps -a --filter name=elasticsearch      # is it Exited?
docker compose -f scripts/docker-compose.yml up -d elasticsearch
curl -s -u elastic:elastic http://localhost:9200/_cluster/health
```

A `yellow` cluster status is expected and fine on a single node — replicas are
unassigned because there is nowhere to put them. Only `red` is a problem.

If you do not need the index, use Mode A instead of fixing ES.

### `status: 400, [es/indices.exists] Expecting a response body, but none was sent`

Your Elasticsearch is 8.x and the client is 9.x. Check the server version and
switch to the `es9` profile:

```bash
curl -s -u elastic:elastic http://localhost:9200 | jq .version.number
```

Confirm the header handling directly if you want to see the cause:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u elastic:elastic -I \
  -H 'Accept: application/vnd.elasticsearch+json; compatible-with=9' \
  http://localhost:9200/          # 400 on an 8.x server, 200 on 9.x
```

### Elasticsearch keeps dying (exit 255) — check Docker disk first

ES refuses to operate as its disk fills: it warns at the 90% high watermark and
forces indices read-only at the 95% flood stage. Because Docker Desktop uses its
own virtual disk, **free space on your host drive tells you nothing** — ask
Docker:

```bash
docker system df
```

Every test run leaves an anonymous Testcontainers volume behind, and they
accumulate fast. (`./gradlew run` no longer does — it uses the named volume.)
Reclaim them:

```bash
docker volume ls --filter dangling=true | wc -l   # how many are orphaned
docker volume prune -f                            # anonymous volumes only
```

`docker volume prune` without `-a` removes only *anonymous* volumes, so named
ones such as `elastic_data` and `dcb_pg_data` are left alone. Do **not** add
`-a` unless you intend to destroy your index and database as well.

### `Address already in use: bind` on port 8080

An earlier DCB is still running. The port is stable now that the database is,
so this is easy to hit twice in a row:

```bash
ss -lntp | grep 8080                                 # Linux/WSL
Get-NetTCPConnection -LocalPort 8080 -State Listen   # PowerShell
```

### `Failed to inject value for parameter [sharedIndexService]`

A *scheduled task* error, repeating every couple of minutes, not a startup
failure — the server is up. `AvailabilityCheckJob` wants the shared index, which
does not exist when running `--index none`. `local_dev.sh` skips that job
automatically in that mode, so you should only see this if you started DCB some
other way, or passed your own `--skip-tasks`. Fix with either:

```bash
./scripts/local_dev.sh --skip-tasks AvailabilityCheckJob --index none
./scripts/local_dev.sh                                   # or just run with an index
```

### Connecting to the database with psql

The port is fixed and the database persists, so:

```bash
psql -h localhost -p 5432 -U dcb -d dcb     # password: dcb
```

If you moved it with `DCB_PG_PORT`, use that port instead. `local_dev.sh` prints
the exact command for your configuration on every start.

### `/var/lib/postgresql/data (unused mount/volume)`

`postgres:18` moved its expected mount point: mount the volume at
`/var/lib/postgresql`, not `/var/lib/postgresql/data`. The pre-18 path makes the
entrypoint refuse to start. Testcontainers handles this internally, so it only
bites hand-written compose files.

## Building the native image

You almost never need this locally — it is a release artifact — but when you do:

**Build it in a container.** `native-image` on Windows also requires the MSVC C++
toolchain, and building in Linux matches what CI and production actually ship.
`gradlew` carries CRLF from a Windows checkout, so invoke the wrapper jar directly
rather than the shell script:

```bash
docker run --rm -v "$PWD:/work" -v dcb-gradle-cache:/root/.gradle -w /work \
  --memory=16g ghcr.io/graalvm/graalvm-community:25 \
  java -classpath gradle/wrapper/gradle-wrapper.jar \
  org.gradle.wrapper.GradleWrapperMain :dcb:nativeCompile -x test --no-daemon
```

Expect roughly 4-5 minutes and up to ~10GB of build memory. The binary lands in
`dcb/build/native/nativeCompile/dcb`.

To package it as an image (the GraalVM image has no Docker CLI, so run this from
the host once the binary exists):

```bash
./gradlew :dcb:dockerBuildNativeBinary -x :dcb:nativeCompile
```

### Use `dockerBuildNativeBinary`, not `dockerBuildNative`

Micronaut's own `dockerBuildNative` / `dockerPushNative` **cannot run on Gradle
9**. Their `NativeImageDockerfile` task fails validation with:

```
property 'nativeImageOptions.layers' is missing an input or output annotation
```

Micronaut Gradle plugin 5.0.1 and 5.0.2 are both affected and 5.0.2 is the latest
release, so there is nothing to upgrade to. `nativeCompile` itself is fine, so
`dockerfileNativeBinary` / `dockerBuildNativeBinary` / `dockerPushNativeBinary`
package its output with plain Docker tasks instead. Both release workflows call
the `...NativeBinary` variants.

The runtime base image must be **glibc**-based. The binary is dynamically linked
against `/lib64/ld-linux-x86-64.so.2`, so the Alpine/musl `BASE_IMAGE` used for the
JVM image will not run it. The default is `cgr.dev/chainguard/wolfi-base`,
overridable with `-PNATIVE_BASE_IMAGE`.

## A note on secrets

`~/.dcb.sh` holds live Keycloak credentials and is deliberately outside the
repo. Keep yours in the home directory, never commit it.

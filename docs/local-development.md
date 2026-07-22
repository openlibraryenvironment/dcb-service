# Running DCB locally

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
| **PostgreSQL** | Always | Micronaut Test Resources, automatically | **No** — new empty DB every run |
| **Elasticsearch 9.x _or_ OpenSearch 2.x** | Only when the shared index is on | **You**, via docker compose | Yes — named volume |

**This asymmetry is the single biggest source of confusion, so be explicit about
it:** running `./gradlew run` provisions Postgres for you and throws it away
afterwards, but it does *not* provision Elasticsearch. If the shared index is
enabled and you have not started ES yourself, the application will not boot.

(Test Resources *can* provision Elasticsearch too — a
`test-resources.containers.elasticsearch.image-name` entry in
`application-development.yml` does it, and the orphaned
`test-resources.containers.elasticsearch.environment.*` line in
`test-resources.properties` is a leftover from when that was configured. It is not
enabled on this branch, and doing so would only provision the same incompatible
8.7.0 image, so compose is the supported route.)

## The three modes

### Mode A — no index (simplest; no Elasticsearch at all)

The whole shared-index stack is gated on `SharedIndexConfiguration`, which is
`@Requires(property = "dcb.index")`. Leave `DCB_INDEX_NAME` unset and none of
those beans are created, so DCB never opens an ES connection:

```bash
unset DCB_INDEX_NAME DCB_INDEX_USERNAME DCB_INDEX_PASSWORD ELASTICSEARCH_HTTP_HOSTS
./gradlew run
```

Use this unless you are specifically working on indexing, discovery or search.
It is the fastest loop and has the fewest moving parts.

### Mode B — persistent index, throwaway database (what the dev scripts do)

Start Elasticsearch **first**, then run. ES keeps its data in the
`elastic_data` volume, so your index survives; the database does not.

```bash
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

Because the database is wiped every run, expect to re-bootstrap reference data
(see `scripts/bootstrap-dev.sh` and friends) each time, while the ES index
retains documents from previous runs. **An index full of documents whose cluster
records no longer exist in the database is normal in this mode** and is not a
bug worth chasing.

### Mode C — fully persistent (database survives too)

Postgres is opt-in via a compose profile, so it never starts by accident:

```bash
docker compose -f scripts/docker-compose.yml --profile persistent-db up -d
```

Starting the container is only half the job. Test Resources provisions Postgres
precisely *because* no datasource URL is configured, so you must also point DCB
at the container — otherwise you end up running two Postgres instances and
writing to the throwaway one:

```bash
export DATASOURCES_DEFAULT_URL="jdbc:postgresql://localhost:5432/dcb"
export DATASOURCES_DEFAULT_USERNAME="dcb"
export DATASOURCES_DEFAULT_PASSWORD="dcb"
export R2DBC_DATASOURCES_DEFAULT_URL="r2dbc:pool:postgresql://localhost:5432/dcb"
export R2DBC_DATASOURCES_DEFAULT_USERNAME="dcb"
export R2DBC_DATASOURCES_DEFAULT_PASSWORD="dcb"
./gradlew run
```

Both datasources must point at the same database: Flyway migrates over JDBC
while the application reads and writes over R2DBC.

> Verified: the container starts healthy with `max_connections=200`. Booting DCB
> end-to-end against it has **not** been verified — if Flyway or R2DBC complains,
> that is the first place to look.

## Troubleshooting

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

Every `./gradlew run` and test run leaves an anonymous Testcontainers volume
behind, and they accumulate fast. Reclaim them:

```bash
docker volume ls --filter dangling=true | wc -l   # how many are orphaned
docker volume prune -f                            # anonymous volumes only
```

`docker volume prune` without `-a` removes only *anonymous* volumes, so named
ones such as `elastic_data` and `dcb_pg_data` are left alone. Do **not** add
`-a` unless you intend to destroy your index and database as well.

### Connecting to the database with psql

In Modes A and B the port is random and changes on every run, because Test
Resources maps the container to an ephemeral port:

```bash
docker ps --filter ancestor=postgres:18 --format "{{.Ports}}"
psql -h localhost -p <PORT_FROM_ABOVE> -U test
```

In Mode C it is simply `psql -h localhost -p 5432 -U dcb -d dcb`.

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
repo. Keep it that way: never move real client secrets, admin passwords or Slack
webhook URLs into a tracked script. If you copy an existing dev script as a
starting point, strip the credentials out of it first.

# DCB on Micronaut 5 — notes for hosting providers

For anyone running DCB from the published image. Covers the release that moves
DCB to Micronaut 5 / JDK 25.

## Short version

**If you run OpenSearch, you should need no changes.** Pull the new tag as usual.

Everything below is either "no action needed, but be aware", or conditional on a
setup you may not have.

## What does not change

| | Status |
|---|---|
| Environment variables | **Unchanged.** No renames, no new required settings |
| Host LMS / DCB configuration in the database | **Unchanged.** No migration needed |
| Image tags | **Unchanged.** JVM image as before; native image still `native-<tag>` |
| Ports | **Unchanged** (8080) |
| Hazelcast version | **Unchanged** — 5.4.0, same as the previous release |
| OpenSearch requirements | **Unchanged** — see below |

## OpenSearch — verified, no action needed

DCB's OpenSearch client moved from `opensearch-java` 2.21.0 to 3.9.0 as part of
the upgrade. **This does not require you to upgrade OpenSearch.**

Verified working against **OpenSearch 2.14.0** and **2.19.1**: DCB starts, creates
its index from the shipped settings and mappings, and the `icu_folding_nopunc`
analyzer tokenises correctly on both.

Unlike Elasticsearch, OpenSearch clients do not send a major-version compatibility
header, so a 3.x client talking to a 2.x server is fine.

**Still required (as before):** the OpenSearch cluster needs the **`analysis-icu`**
plugin. DCB's index settings define an `icu_folding_nopunc` analyzer over
`icu_tokenizer`/`icu_folding`. This is not new — if your current deployment works,
you already have it.

## Elasticsearch — conditional, and it is a hard break

**Only relevant if you run Elasticsearch rather than OpenSearch.**

Micronaut 5 brings `elasticsearch-java` 9.4.0, and Elastic's clients enforce a
major-version compatibility header. **A 9.x client cannot talk to an 8.x server.**
Requests are rejected with:

```
Accept version must be either version 8 or 7, but found 9
```

which DCB surfaces at startup as a confusing 400:

```
TransportException: node: http://.../, status: 400,
[es/indices.exists] Expecting a response body, but none was sent
```

If you are on Elasticsearch, **you must be on 9.x before taking this release**,
and that cluster also needs the `analysis-icu` plugin.

## Be aware: the native image base has changed

Only relevant if you deploy the **native** image (`native-<tag>`).

The native image is now packaged on **`cgr.dev/chainguard/wolfi-base`**. The
binary is dynamically linked against glibc, so a musl/Alpine base will not run it.

This matters if you:

- allowlist or mirror base images,
- run image scanning that is pinned to specific base distributions,
- have policies about image provenance.

The JVM image base also moved from `eclipse-temurin:21-jre-alpine` to
`eclipse-temurin:25-jre-alpine`.

Nothing about how you *run* either image changes.

## Be aware: JDK 25 in the image

DCB now runs on JDK 25 inside the image. You do not need JDK 25 on the host — it
ships in the image — but note:

- The JVM image passes `-XX:MaxRAMPercentage=80.0` etc. as before, so it still
  sizes itself from the container memory limit. **Review your Fargate task memory**
  if you are close to a limit; JDK 25 and Micronaut 5 have somewhat different
  baseline heap behaviour, though we have not observed a material increase.
- If you build from source rather than pulling the image, you now need **JDK 25**
  and **Gradle 9**. JDK 17/21 will not compile it.

## AWS Fargate specifics

- **No multicast on AWS.** DCB's default `hazelcast.yaml` uses multicast discovery,
  which does not work on Fargate — each task runs as an isolated single-member
  cluster. This is **pre-existing behaviour, unchanged by this release**, but it is
  worth knowing that federated locks are per-task, not cluster-wide, unless you
  supply your own Hazelcast configuration.
- Nothing in this release changes networking, port bindings, or health endpoints.

## If something goes wrong

The two failure modes most likely to be release-related, and how to tell them
apart quickly:

| Symptom | Likely cause |
|---|---|
| `status: 400 ... Accept version must be ... found 9` | Elasticsearch 8.x behind a 9.x client — upgrade ES to 9.x |
| `status: 401 ... [es/indices.exists]` | Index credentials not reaching the client — check `DCB_INDEX_USERNAME` / `DCB_INDEX_PASSWORD` |
| `CP subsystem is a licensed feature` | Hazelcast newer than 5.4.0 on the classpath — should not happen from our image; report it |
| `Unrecognized field "..." (class ...Config)` | A Host LMS config key DCB does not model. Should no longer fail; report it if it does |

Checking the server version behind the index is usually the fastest first step:

```bash
curl -s $OPENSEARCH_HTTP_HOSTS | jq .version.number
```

## Rollback

This release does not migrate data or change configuration, so rolling back to the
previous tag is safe. The one exception is Elasticsearch: if you upgraded ES to 9.x
in order to take this release, rolling DCB back to an 8.x-era client will not work
against that upgraded cluster.

# Brand asset storage in Postgres

**Status:** schema proposed, awaiting approval. Nothing built yet.
**Follows:** `consortial-and-library-branding`, which adds the `BrandAssetStore` seam and
the S3 implementation. See `docs/branding.md` for how branding works today.

## Why the default should be the database

Uploads currently require an S3-API bucket. That makes the *convenience* path — upload
rather than paste a CDN URL — conditional on infrastructure a deployment may not have, and
it makes the code path conditional on infrastructure a **developer** may not have.

The cost comparison decides it:

| | Test locally | Test in CI | Deploy |
|---|---|---|---|
| **Postgres** | nothing — `DcbTestContainerContextBuilder` already provisions one for every `@DcbTest` | nothing | nothing |
| **S3** | MinIO container, three `AWS_*` exports, endpoint override, path-style addressing | Testcontainers MinIO | a bucket and credentials |

Postgres is cheaper at every stage, not just at deployment. S3 remains worth keeping for
estates that already run object storage, but it is the specialised choice rather than the
default one.

### The objection, and why it does not survive the numbers

`BrandAssetStore`'s javadoc rejects the database: *"a background image is 200-800 KB, it
would bloat every backup."* Against what is actually storable:

| | |
|---|---|
| Referenced images, realistically | 1 consortium × 3 + 500 libraries × 1 logo, mostly small marks — **well under 100 MB** |
| Hard ceiling on any single row | 2 MB, enforced before storage by `BrandAssetValidator` |

There are exactly four columns that can hold an uploaded asset:
`consortium.brand_logo_url`, `consortium.brand_header_icon_url`,
`consortium.brand_background_image_url`, `library.brand_logo_url`. At 500 libraries that is
503 referenceable images, so the referenced set is bounded at ~1 GB even if every one of
them is a maximum-size upload — in a database already holding 20 million bibliographic
records.

It is true that this lands in every backup and every replica, and that it is the one thing
in the database that cannot be rebuilt from an upstream source. That is the honest cost.

## The orphan problem is the real finding, and the database is what solves it

**Uploads are not bounded by those four columns.** An administrator uploads, gets a URL,
and only then saves it against a brand field with a separate mutation. An upload that is
never saved is orphaned immediately, and `BrandAssetCleanup` will never see it: that class
is delete-on-*replace*, driven by a field changing, so it only ever removes an asset that
was once referenced.

So an authenticated administrator can insert unbounded 2 MB rows, by uploading repeatedly
and saving nothing. With S3 that grows a bucket. With Postgres it grows the transactional
database and every backup of it, which is worse.

`BrandAssetStore`'s javadoc explains why a sweep was rejected for S3:

> A sweep needs a scheduler, a way to know which keys are still referenced by any brand
> field on any row, and somebody to notice when it stops running.

The middle clause is the expensive one for object storage — and it is **one SQL statement**
when the assets and the brand columns are in the same database:

```sql
delete from brand_asset a
 where a.date_created < :cutoff
   and not exists (select 1 from consortium c
                    where :prefix || a.asset_key in (c.brand_logo_url,
                                                     c.brand_header_icon_url,
                                                     c.brand_background_image_url))
   and not exists (select 1 from library l
                    where :prefix || a.asset_key = l.brand_logo_url)
```

**The `date_created < :cutoff` grace period is not optional.** Between the upload and the
mutation that saves the URL, an asset is legitimately unreferenced. A sweep without a
grace window would delete the image an administrator is part-way through choosing. 24 hours
is generous for a form submission and short enough that abandoned uploads do not accumulate.

That the database store can be swept and the S3 store cannot is a point in its favour that
the original design did not consider.

## Proposed schema — AWAITING APPROVAL

One table, no changes to any existing table. Take the next free version at merge time; on
current `main` plus the branding branch that is `V8_74_001`, but re-check.

```sql
-- Uploaded brand images, when dcb.branding.assets.store is `database` (R-17b).
--
-- WHY THE DATABASE AT ALL. The alternative is an S3-API bucket, which is supported and
-- stays supported - but it makes uploading conditional on infrastructure a deployment may
-- not have and a developer almost certainly does not. Postgres is already provisioned
-- everywhere DCB runs, including in every test. See docs/branding.md.
--
-- SIZE. Four columns can reference an uploaded asset (consortium.brand_logo_url,
-- consortium.brand_header_icon_url, consortium.brand_background_image_url and
-- library.brand_logo_url), so at 500 libraries the referenced set is 503 images, each
-- capped at 2MB by BrandAssetValidator before it is ever stored. Unreferenced uploads are
-- swept - see BrandAssetSweep - which is the part object storage could not do cheaply.
--
-- IMMUTABLE ROWS. asset_key is the SHA-256 of the content plus an extension, so a row is
-- inserted once and never updated: re-uploading identical bytes is the same key, and a
-- changed image is a different key. There is no version column because there is nothing
-- to conflict over, and size_bytes cannot drift from bytes because nothing rewrites either.

create table brand_asset (
	-- 64 hex characters and a 4-character extension. varchar(80) leaves room for a
	-- format we do not accept yet without a migration to widen it.
	asset_key    varchar(80) primary key,

	-- What the bytes turned out to be after decoding, never what the upload claimed.
	content_type varchar(64) not null,

	bytes        bytea       not null,

	-- Denormalised so "how much is stored" does not have to detoast every row. Safe to
	-- denormalise precisely because rows are never updated.
	size_bytes   integer     not null,

	date_created timestamp   not null
);

-- PNG and JPEG are already compressed, so Postgres would attempt pglz on every insert,
-- fail to shrink anything, and store it out of line anyway. EXTERNAL skips the attempt:
-- same storage, less CPU on the one path that writes 2MB at a time.
alter table brand_asset alter column bytes set storage external;

-- No further indexes. Every read is by primary key, and the sweep is a once-daily scan of
-- a table bounded by the upload quota - an index on date_created would be maintained by
-- every insert to serve one statement a day.
```

## Selection

`BrandAssetStore` already exists as the seam; both controllers are
`@Requires(beans = BrandAssetStore.class)`, so "no store" already means "no upload route".
Add one property:

| `dcb.branding.assets.store` | Result |
|---|---|
| `database` *(new default)* | Works wherever DCB works |
| `s3` | Requires `dcb.branding.assets.bucket` and an `S3Client` |
| `none` | Upload routes absent. Brand fields still take an absolute CDN URL |

```java
@Requires(property = "dcb.branding.assets.store", value = "database", defaultValue = "database")
class DatabaseBrandAssetStore implements BrandAssetStore { ... }

@Requires(property = "dcb.branding.assets.store", value = "s3")
@Requires(beans = S3Client.class)
class S3BrandAssetStore implements BrandAssetStore { ... }
```

Defaulting to `database` changes behaviour for nobody: the feature has not shipped, and
today a blank bucket means no uploads at all. A deployment that wants the CDN-only posture
sets `store: none` deliberately rather than getting it by omission.

## Two things to fix while doing this

**Lift the cache out of the S3 store.** `S3BrandAssetStore` holds a Caffeine cache because
it was the only implementation. Caching is not S3's private business — the database store
wants exactly the same behaviour, and for the same reason: the serve route is anonymous and
hit on first paint. Move it into a `CachingBrandAssetStore` decorator so both get it and
neither reimplements it.

**Add the Testcontainers MinIO test.** Once the database is the default, S3 becomes the
path nobody exercises by accident. A Testcontainers-backed test pins it in CI, closes the
last open item from the branding review, and lets MinIO leave `scripts/docker-compose.yml`.

## Scale

| Path | Bound |
|---|---|
| Store an asset | one insert, ≤ 2 MB, by primary key |
| Serve an asset | one primary-key read, then cached |
| Sweep | once daily, bounded by upload volume; two `not exists` against a 1-row and a 500-row table |
| Table growth | referenced set ≤ 503 images; unreferenced set bounded by the sweep |

## Evidence to produce

| Claim | Artefact |
|---|---|
| Bytes round-trip through Postgres unchanged | a test storing and re-reading a PNG, asserting byte equality |
| The store needs no infrastructure | the test is a plain `@DcbTest` with no container of its own |
| An unreferenced upload is swept | a test asserting an asset older than the grace period, referenced by nothing, is deleted |
| A referenced asset is never swept | one test per referencing column — all four, or the next column added silently loses its images |
| A just-uploaded asset survives | a test asserting an unreferenced asset inside the grace period is kept |
| Both stores behave identically | one contract test run against both implementations |
| S3 still works | Testcontainers MinIO test |

## Open decisions

1. **Schema approval.** One new table, no existing table touched.
2. Grace period before an unreferenced asset is swept: 24 hours proposed.
3. Should `size_bytes` exist, or is `length(bytes)` enough? Proposed: keep it, since rows
   are immutable and detoasting 500 rows to answer "how much is stored" is the wrong shape.

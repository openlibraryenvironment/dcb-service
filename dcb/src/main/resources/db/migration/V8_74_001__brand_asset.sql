-- Uploaded brand images (R-17b).
--
-- WHY THE DATABASE. The alternative is an S3-API bucket, which makes uploading conditional
-- on infrastructure a deployment may not have and a developer almost certainly does not.
-- Postgres is already provisioned everywhere DCB runs, including in every test, so the
-- upload path is exercisable locally and in CI rather than only once deployed. Object
-- storage is a worthwhile option for estates that already run it and will return as one;
-- it is not the right thing to require of everybody. See docs/branding.md.
--
-- NOT REQUIRED AT ALL. dcb.branding.assets.store=none removes the upload routes from the
-- running service and brand fields still accept an absolute CDN URL, which stays a
-- first-class way to brand a consortium. A deployment that does not want images in its
-- database does not have to have them there.
--
-- SIZE. Four columns can reference an uploaded asset - consortium.brand_logo_url,
-- consortium.brand_header_icon_url, consortium.brand_background_image_url and
-- library.brand_logo_url - so at 500 libraries the referenced set is 503 images, each
-- capped at 2MB by BrandAssetValidator before it is ever stored. Unreferenced uploads are
-- swept by BrandAssetSweep, which is the part object storage could not do cheaply: knowing
-- which keys are still referenced is one statement when the assets and the brand columns
-- share a database.
--
-- IMMUTABLE ROWS. asset_key is the SHA-256 of the content plus an extension, so a row is
-- inserted once and never updated - re-uploading identical bytes is the same key, and a
-- changed image is a different key. Hence no version column: there is nothing to conflict
-- over. Hence also that size_bytes cannot drift from bytes, because nothing rewrites
-- either of them.

create table brand_asset (
	-- 64 hex characters and a 4-character extension. varchar(80) leaves room for a format
	-- we do not accept yet without a migration to widen it.
	asset_key    varchar(80) primary key,

	-- What the bytes turned out to be after decoding, never what the upload claimed.
	content_type varchar(64) not null,

	bytes        bytea       not null,

	-- Denormalised so "how much is stored" does not have to detoast every row. Safe
	-- precisely because rows are never updated.
	size_bytes   integer     not null,

	date_created timestamp   not null
);

-- PNG and JPEG are already compressed, so Postgres would attempt pglz on every insert,
-- fail to shrink anything, and store the value out of line anyway. EXTERNAL skips the
-- attempt: same storage, less CPU on the one path that writes two megabytes at a time.
alter table brand_asset alter column bytes set storage external;

-- No further indexes. Every read is by primary key, and the sweep is a once-daily scan of
-- a table bounded by upload volume - an index on date_created would be maintained by every
-- insert in order to serve one statement a day.

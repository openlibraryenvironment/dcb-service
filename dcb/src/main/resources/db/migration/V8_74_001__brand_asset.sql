-- Uploaded brand images (R-17b), when dcb.branding.assets.store is `database`.
-- Why the database rather than object storage, and what bounds the size:
-- docs/branding.md, "Migration choices".

create table brand_asset (
	-- SHA-256 hex plus an extension. Content-addressed, so rows are written once and
	-- never updated - hence no version column, and size_bytes cannot drift from bytes.
	asset_key    varchar(80) primary key,

	content_type varchar(64) not null,
	bytes        bytea       not null,
	size_bytes   integer     not null,
	date_created timestamp   not null
);

-- Not optional, and not a micro-optimisation: PNG and JPEG are already compressed, so
-- Postgres would attempt pglz on every insert, fail to shrink anything, and store the
-- value out of line anyway. EXTERNAL skips the wasted attempt.
alter table brand_asset alter column bytes set storage external;

-- No further indexes, deliberately. Every read is by primary key and the sweep runs once
-- a day; an index on date_created would be maintained by every insert to serve one
-- statement a day.

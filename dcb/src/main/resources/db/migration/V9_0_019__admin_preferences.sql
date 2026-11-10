-- A member of staff's display settings, each admin app's state, and saved views, filed under an
-- opaque key: base64url(HMAC-SHA256(secret, "u:" + sub)). Nothing here names a person.
-- docs/operational/admin-preferences.md. New tables only: no existing table is locked.

CREATE TABLE admin_preference (
	admin_key varchar(64) PRIMARY KEY,
	-- Each null means the person has not chosen, which is not the default value
	theme_mode varchar(16),
	text_size varchar(16),
	density varchar(16),
	font_name varchar(32),
	motion varchar(16),
	language varchar(16),
	-- {"admin": {...}, "libraries": {...}}, each slice written alone and at most 16 KB
	app_state jsonb NOT NULL DEFAULT '{}'::jsonb,
	date_created timestamptz NOT NULL,
	date_updated timestamptz NOT NULL,
	-- Last use, read or write: the only input to retention
	date_last_seen timestamptz NOT NULL
);

CREATE INDEX idx_admin_preference_last_seen ON admin_preference (date_last_seen);

CREATE TABLE admin_saved_view (
	id uuid PRIMARY KEY,
	admin_key varchar(64) NOT NULL REFERENCES admin_preference (admin_key) ON DELETE CASCADE,
	name varchar(128) NOT NULL,
	view_type varchar(48) NOT NULL,
	query_state jsonb NOT NULL,
	date_created timestamptz NOT NULL,
	date_updated timestamptz NOT NULL
);

CREATE UNIQUE INDEX admin_saved_view_key_name_uq ON admin_saved_view (admin_key, view_type, lower(name));

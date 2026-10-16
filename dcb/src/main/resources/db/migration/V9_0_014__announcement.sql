-- Patron-facing notices, scoped to a library or the consortium (ScopeLevel). The schema
-- enforces three rules; why: docs/discovery-service-approach.md, "Announcements".
--  1. expires_at is NOT NULL, so no notice stays up for ever.
--  2. body is plain text and there is no markup column.
--  3. scope_type/scope_id name a level, so precedence is decided by the query per scope.
-- Dismissal is not stored here: dcb-service holds no patron identity.
create table announcement (
	id uuid primary key,

	scope_type varchar(32) not null,
	scope_id uuid not null,

	-- Plain text, both of them. A headline long enough to be a paragraph is a paragraph.
	headline varchar(200) not null,
	body varchar(1000) not null,

	-- Only an URGENT consortium announcement shows beneath a library's own. Everything else
	-- is one at a time, narrowest wins.
	urgent boolean not null default false,

	-- Whether a patron may dismiss it. An outage notice usually should not be dismissible;
	-- a "new source available" one should.
	dismissible boolean not null default true,

	-- Both required: a future start schedules a notice, and an end is rule 1 above.
	starts_at timestamptz not null,
	expires_at timestamptz not null,

	last_edited_by varchar(100),
	reason varchar(100),
	change_reference_url varchar(200),
	change_category varchar(200),

	date_created timestamptz,
	date_updated timestamptz,

	-- A window that ends before it begins would never show and nothing would say why.
	constraint announcement_window check (expires_at > starts_at)
);

-- The anonymous patron read is one scope, starts_at <= now < expires_at, newest first, LIMIT 1:
-- a backward scan of this index. Expired notices are never deleted, so without it every call
-- reads the table's whole history (20,040 rows, 1,253 buffers, against 6 with the index).
create index idx_announcement_live on announcement (scope_type, scope_id, starts_at, expires_at);

-- Who published what, at which scope, and when. An untraceable broadcast to every
-- patron of every member library is exactly what the audit trail is for, and the scope is
-- columns on this row so the log records it without anything else changing.
create trigger data_change_log_trigger
after insert or update or delete on announcement
for each row execute function audit_trigger();

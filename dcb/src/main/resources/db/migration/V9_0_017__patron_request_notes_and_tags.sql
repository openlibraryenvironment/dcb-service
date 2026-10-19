-- Notes and tags that libraries add to a patron request. Each row names the library (or the
-- consortium) it speaks for, never a person, so no audit trigger is attached: audit_trigger()
-- records the editing username.
--
-- No foreign key onto patron_request, so creating these tables takes no lock on it.

CREATE TABLE patron_request_note (
	id uuid PRIMARY KEY,
	patron_request_id uuid NOT NULL,
	author_kind varchar(16) NOT NULL,
	agency_code varchar(32),
	-- Null once retracted
	body varchar(2000),
	date_created timestamptz NOT NULL,
	retracted_at timestamptz,
	retracted_by varchar(16),
	CONSTRAINT ck_pr_note_author CHECK (
		(author_kind = 'LIBRARY' AND agency_code IS NOT NULL) OR
		(author_kind = 'CONSORTIUM' AND agency_code IS NULL))
);

CREATE INDEX idx_pr_note_request ON patron_request_note (patron_request_id, date_created);

CREATE TABLE request_tag (
	code varchar(32) PRIMARY KEY,
	label varchar(64) NOT NULL,
	description varchar(200),
	active boolean NOT NULL DEFAULT true
);

CREATE TABLE patron_request_tag (
	id uuid PRIMARY KEY,
	patron_request_id uuid NOT NULL,
	tag_code varchar(32) NOT NULL REFERENCES request_tag (code),
	author_kind varchar(16) NOT NULL,
	agency_code varchar(32),
	date_created timestamptz NOT NULL,
	CONSTRAINT ck_pr_tag_author CHECK (
		(author_kind = 'LIBRARY' AND agency_code IS NOT NULL) OR
		(author_kind = 'CONSORTIUM' AND agency_code IS NULL))
);

-- One tag per author per request. Partial indexes rather than NULLS NOT DISTINCT, which needs Postgres 15
CREATE UNIQUE INDEX uq_pr_tag_library ON patron_request_tag (patron_request_id, tag_code, agency_code) WHERE author_kind = 'LIBRARY';
CREATE UNIQUE INDEX uq_pr_tag_consortium ON patron_request_tag (patron_request_id, tag_code) WHERE author_kind = 'CONSORTIUM';
CREATE INDEX idx_pr_tag_code ON patron_request_tag (tag_code, patron_request_id);

-- A starting vocabulary; consortium staff edit it through createRequestTag and updateRequestTag
INSERT INTO request_tag (code, label, description, active) VALUES
	('DAMAGED', 'Damaged', 'The item arrived or was returned damaged', true),
	('MISSING_PIECES', 'Missing pieces', 'Part of the item is missing', true),
	('PATRON_CONTACTED', 'Patron contacted', 'Staff have been in touch with the patron about this request', true),
	('RETURN_DISPUTED', 'Return disputed', 'A library disputes whether or when the item was returned', true),
	('SLOW_TO_SHIP', 'Slow to ship', 'The item took longer than expected to ship', true);

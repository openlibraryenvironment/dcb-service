CREATE TABLE dcb_profile_membership (
	id uuid PRIMARY KEY,
	version bigint NOT NULL DEFAULT 0,
	profile_id varchar(64) NOT NULL,
	profile_version integer NOT NULL,
	state varchar(32) NOT NULL,
	token_hash varchar(64) NOT NULL,
	expires_at timestamp NOT NULL,
	policy jsonb NOT NULL,
	remote_base_url varchar(512),
	remote_directory_url varchar(512),
	remote_issuer varchar(512),
	remote_self_slug varchar(200),
	selected_symbol varchar(200),
	host_lms_id uuid REFERENCES host_lms(id),
	approved_descriptor jsonb,
	approved_descriptor_hash varchar(64),
	pending_descriptor jsonb,
	pending_descriptor_hash varchar(64),
	idempotency_key varchar(200),
	issued_by varchar(200),
	issued_at timestamp NOT NULL,
	redeemed_at timestamp,
	revoked_at timestamp,
	last_synced_at timestamp,
	next_sync_at timestamp,
	last_sync_error varchar(1000),
	date_created timestamp,
	date_updated timestamp
);

CREATE UNIQUE INDEX dcb_profile_membership_token_hash_uq
	ON dcb_profile_membership(token_hash);
CREATE UNIQUE INDEX dcb_profile_membership_host_lms_uq
	ON dcb_profile_membership(host_lms_id)
	WHERE host_lms_id IS NOT NULL;
CREATE UNIQUE INDEX dcb_profile_membership_remote_binding_uq
	ON dcb_profile_membership(remote_issuer, remote_self_slug, selected_symbol)
	WHERE state IN ('ACTIVE', 'REVIEW_REQUIRED');
CREATE INDEX dcb_profile_membership_sync_idx
	ON dcb_profile_membership(state, next_sync_at);

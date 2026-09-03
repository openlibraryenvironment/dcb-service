-- The DCB-side record of a DCB Admin for Libraries account.
--
-- NOT a credential mirror. The identity provider owns the password, the enabled state and
-- email verification, and this table never sees any of them. What it owns is the BINDING -
-- which provider user belongs to which library, in which role - and, through the trigger
-- below, the audit trail.
--
-- The audit trail is the argument for the table existing at all. DataChangeLog rows are
-- written by the Postgres audit_trigger() function rather than by Java, so a provisioning
-- flow that only called the provider would be the one privileged mutation in the system
-- with no audit record of who created an account, for whom, when.
--
-- New rows are bounded by staff headcount, not by corpus or request volume: hundreds of
-- libraries with tens of staff each. The feature makes two reads: list a library's accounts,
-- served by idx_library_user_account_library; and find the row for a provider user, served by
-- the identity_provider_user_id unique constraint. agency_code is written and never used as a
-- predicate, so it carries no index of its own.

CREATE TABLE library_user_account (
	id uuid PRIMARY KEY,
	identity_provider varchar(32) NOT NULL,
	identity_provider_user_id varchar(128) NOT NULL,
	library_id uuid NOT NULL REFERENCES library(id),
	-- Denormalised from the library deliberately. It is the value written into the token
	-- as the agency claim, and the account must keep saying which agency it was created
	-- for even if the library's own code is later corrected.
	agency_code varchar(64) NOT NULL,
	email varchar(256) NOT NULL,
	first_name varchar(128),
	last_name varchar(128),
	role varchar(32) NOT NULL,
	status varchar(16) NOT NULL,
	date_created timestamp,
	date_updated timestamp,
	last_edited_by varchar(100),
	reason text,
	change_category varchar(100),
	change_reference_url varchar(200),

	-- The deepest of three independent gates on the role, after the GraphQL enum and the
	-- Java valueOf. This one cannot be bypassed by any code path, including a future one
	-- that forgets the other two. An account row can never say ADMIN.
	CONSTRAINT library_user_account_role_allowlist
		CHECK (role IN ('LIBRARY_ADMIN', 'LIBRARY_READ_ONLY')),

	CONSTRAINT library_user_account_status_allowlist
		CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED')),

	-- One DCB row per provider user. Re-running a provision that half-completed must
	-- collide rather than create a second binding for the same person.
	CONSTRAINT library_user_account_idp_unique
		UNIQUE (identity_provider, identity_provider_user_id)
);

-- The list query: one library's accounts, ordered. Tens of rows.
CREATE INDEX idx_library_user_account_library ON library_user_account (library_id);

-- Account creation, enable/disable and removal are all privileged mutations and all three
-- must be attributable. Separate triggers for the same reason the consortium contact
-- migration used them: a BEFORE DELETE sees the row it is about to lose.
CREATE TRIGGER data_change_log_trigger_library_user_account_insert_update
	AFTER INSERT OR UPDATE ON library_user_account
	FOR EACH ROW
	EXECUTE FUNCTION audit_trigger();

CREATE TRIGGER data_change_log_trigger_library_user_account_delete
	BEFORE DELETE ON library_user_account
	FOR EACH ROW
	EXECUTE FUNCTION audit_trigger();

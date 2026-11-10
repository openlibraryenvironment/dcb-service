-- Requests a member of staff watches, in either admin app. No foreign key onto patron_request,
-- so this migration takes no lock on it: the watch route checks the request exists and is the
-- caller's to see, and the daily retention sweep removes watches whose request has gone.

CREATE TABLE admin_watched_request (
	admin_key varchar(64) NOT NULL REFERENCES admin_preference (admin_key) ON DELETE CASCADE,
	patron_request_id uuid NOT NULL,
	date_created timestamptz NOT NULL,
	-- Set on watching and whenever the person opens the request
	date_last_viewed timestamptz NOT NULL,
	PRIMARY KEY (admin_key, patron_request_id)
);

alter table patron_request add is_paused boolean default FALSE;
alter table patron_request add needs_attention boolean default FALSE;
alter table patron_request add next_scheduled_poll timestamp;

CREATE INDEX IF NOT EXISTS pr_next_scheduled_poll_idx ON patron_request(next_scheduled_poll); 

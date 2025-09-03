drop index pr_next_scheduled_poll_idx;
create index patron_request_next_scheduled_poll_idx on patron_request(next_scheduled_poll, is_too_long)
WHERE next_scheduled_poll IS NOT NULL and
	  is_too_long = false;

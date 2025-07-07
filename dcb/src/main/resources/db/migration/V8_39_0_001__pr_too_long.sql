alter table patron_request add column is_too_long boolean default false;

update patron_request set is_too_long = false where is_too_long is null;

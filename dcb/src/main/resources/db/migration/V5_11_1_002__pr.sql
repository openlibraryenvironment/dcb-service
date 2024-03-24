alter table patron_request add previous_status_code varchar(200);
alter table patron_request add is_loaned_to_patron boolean;

alter table patron_request add poll_count_for_current_status integer;
alter table patron_request_audit add current_status_stamp timestamp;
alter table patron_request_audit add elapsed_time_in_current_status BIGINT;
alter table patron_request_audit add next_expected_status varchar(200);
alter table patron_request_audit add out_of_sequence_flag boolean;

alter table patron_request_audit drop column current_status_stamp;
alter table patron_request_audit drop column elapsed_time_in_current_status;
alter table patron_request_audit drop column next_expected_status;
alter table patron_request_audit drop column out_of_sequence_flag;

alter table patron_request add current_status_timestamp timestamp;
alter table patron_request add elapsed_time_in_current_status BIGINT;
alter table patron_request add next_expected_status varchar(200);
alter table patron_request add out_of_sequence_flag boolean;
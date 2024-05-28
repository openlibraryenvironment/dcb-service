alter table patron_request add local_item_hostlms_code varchar(200);
alter table patron_request add local_item_agency_code varchar(200);
alter table patron_request add is_manually_selected_item boolean;

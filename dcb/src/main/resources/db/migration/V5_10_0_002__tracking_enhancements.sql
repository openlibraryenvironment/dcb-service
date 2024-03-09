alter table patron_request add pickup_item_status_repeat integer;
alter table patron_request add pickup_request_status_repeat integer;
alter table patron_request add local_request_status_repeat integer;
alter table patron_request add local_item_status_repeat integer;
alter table supplier_request add local_request_status_repeat integer;
alter table supplier_request add local_item_status_repeat integer;

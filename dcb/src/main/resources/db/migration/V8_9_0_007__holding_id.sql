alter table patron_request add local_holding_id varchar(200);
alter table patron_request add pickup_holding_id varchar(200);
alter table supplier_request add local_holding_id varchar(200);
alter table inactive_supplier_request add local_holding_id varchar(200);

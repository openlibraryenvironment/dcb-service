alter table agency ADD UNIQUE(code);
alter table agency ADD date_created timestamp;
alter table agency ADD date_updated timestamp;
alter table patron_request add column protocol varchar(10);
alter table supplier_request add column protocol varchar(10);

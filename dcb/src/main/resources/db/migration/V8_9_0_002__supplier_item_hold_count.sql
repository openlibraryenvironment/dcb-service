alter table supplier_request add local_hold_count INTEGER DEFAULT 0;
alter table inactive_supplier_request add local_hold_count INTEGER DEFAULT 0;

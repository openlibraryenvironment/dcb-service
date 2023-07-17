ALTER TABLE patron_request_audit ADD COLUMN from_status varchar(200);
ALTER TABLE patron_request_audit ADD COLUMN to_status varchar(200);
ALTER TABLE patron_request_audit RENAME COLUMN briefDescription TO brief_description;
ALTER TABLE patron_request_audit DROP CONSTRAINT fk_patron_request;

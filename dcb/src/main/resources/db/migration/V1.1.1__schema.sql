ALTER TABLE supplier_request ADD COLUMN local_bib_id varchar(256);
ALTER TABLE patron_identity ADD COLUMN local_names varchar(256);
ALTER TABLE patron_identity ADD COLUMN local_agency varchar(256);

ALTER TABLE patron_request ADD COLUMN requesting_identity_id uuid REFERENCES patron_identity (id);
ALTER TABLE patron_request ADD COLUMN description varchar(256);
ALTER TABLE supplier_request ADD COLUMN virtual_identity_id uuid REFERENCES patron_identity (id);

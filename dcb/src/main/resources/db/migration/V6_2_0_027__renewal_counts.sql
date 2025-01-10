ALTER TABLE patron_request
ADD renewal_count INTEGER DEFAULT 0;

ALTER TABLE patron_request
ADD local_renewal_count INTEGER DEFAULT 0;

ALTER TABLE supplier_request
ADD local_renewal_count INTEGER DEFAULT 0;

ALTER TABLE inactive_supplier_request
ADD local_renewal_count INTEGER DEFAULT 0;

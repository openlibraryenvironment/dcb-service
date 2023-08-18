ALTER TABLE agency
ADD COLUMN auth_profile VARCHAR(64),
ADD COLUMN idp_url VARCHAR(200);

CREATE INDEX idx_agency_host_lms_id ON agency (host_lms_id);

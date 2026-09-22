ALTER TABLE location
	ADD COLUMN creation_source varchar(32) NOT NULL DEFAULT 'UNKNOWN';

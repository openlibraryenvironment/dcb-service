create table cluster_record (
	id uuid primary key,
	date_created timestamp,
	date_updated timestamp,
	title text
);

create table bib_record (
	id uuid primary key,
	date_created timestamp,
	date_updated timestamp,
	source_system_id uuid,
	source_record_id varchar(256),
	title text,
	contributes_to uuid NOT NULL,
  CONSTRAINT fk_contributes_to FOREIGN KEY (contributes_to) REFERENCES cluster_record(id)
);
CREATE INDEX idx_bib_source_system ON bib_record(source_system_id);
CREATE INDEX idx_bib_source_id ON bib_record(source_record_id);
CREATE INDEX idx_bib_contributes_to ON bib_record(contributes_to);

create table bib_identifier (
	id uuid primary key,
	value varchar(255),
	namespace varchar(255)
);

create table location (
	id uuid primary key,
	code varchar(200),
	name varchar(255),
  type varchar(32),
  agency_fk uuid,
  is_pickup boolean
);

create table patron_request (
	id uuid primary key,
	date_created timestamp,
	date_updated timestamp,
	patron_id varchar(200),
	patron_agency_code varchar(200),
	bib_cluster_id uuid,
	pickup_location_code varchar(200),
	status_code varchar(200)
);

create table supplier_request (
	id uuid primary key,
	patron_request_id uuid,
	holdings_item_id uuid,
	holdings_agency_code varchar(200),
	CONSTRAINT fk_patron_request FOREIGN KEY (patron_request_id) REFERENCES patron_request(id)
);

create table host_lms (
        id uuid primary key,
        code varchar(32),
        name varchar(200),
        lms_client_class varchar(200),
        client_config text
);

create table agency (
        id uuid primary key,
        code varchar(32),
        name varchar(200),
        host_lms_id uuid,
        CONSTRAINT fk_host_lms FOREIGN KEY (host_lms_id) REFERENCES host_lms(id)
);

create table location_symbol (
        id uuid primary key,
        authority varchar(32),
        code varchar(64),
        owning_location_fk uuid,
        CONSTRAINT fk_location FOREIGN KEY (owning_location_fk) REFERENCES location(id)
);

create table raw_source (
    id uuid NOT NULL,
    host_lms_id uuid NOT NULL,
    remote_id varchar(255) NOT NULL,
    json jsonb NOT NULL,
    CONSTRAINT raw_source_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_rs_host_lms ON raw_source(host_lms_id);
CREATE INDEX idx_rs_remote_id ON raw_source(remote_id);

--create table patron_request_supplier_request (
--	supplier_request_id uuid primary key,
--	patron_request_id uuid,
--	holdings_item_id uuid,
--	holdings_agency_code varchar(200),
--	CONSTRAINT fk_patron_request
--  FOREIGN KEY(patron_request_id)
--  REFERENCES patron_request(id)
--);

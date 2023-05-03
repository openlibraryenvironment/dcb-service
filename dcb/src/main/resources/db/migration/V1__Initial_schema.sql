CREATE TABLE cluster_record (
	id uuid primary key,
	date_created timestamp,
	date_updated timestamp,
	title text
);

CREATE TABLE bib_record (
	id uuid primary key,
	date_created timestamp,
	date_updated timestamp,
	source_system_id uuid,
	source_record_id varchar(256),
	title text,
	contributes_to uuid NOT NULL,
	record_status varchar(8),
	type_of_record varchar(8),
	derived_type varchar(32),
	blocking_title text,
  CONSTRAINT fk_contributes_to FOREIGN KEY (contributes_to) REFERENCES cluster_record(id)
);

CREATE INDEX idx_bib_source_system ON bib_record(source_system_id);
CREATE INDEX idx_bib_source_id ON bib_record(source_record_id);
CREATE INDEX idx_bib_contributes_to ON bib_record(contributes_to);

CREATE TABLE bib_identifier (
	id uuid primary key,
	owner_id uuid,
	value varchar(255),
	namespace varchar(255),
	CONSTRAINT fk_owner FOREIGN KEY(owner_id) REFERENCES bib_record(id)
);

CREATE TABLE host_lms (
	id uuid primary key,
	code varchar(32),
	name varchar(200),
	lms_client_class varchar(200),
	client_config JSONB
);

CREATE TABLE location (
	id uuid primary key,
	code varchar(200),
	name varchar(255),
	type varchar(32),
	agency_fk uuid,
	host_system_id uuid,
	is_pickup boolean
);

CREATE TABLE patron (
	id uuid NOT NULL,
	date_created timestamp,
	date_updated timestamp,
	CONSTRAINT patron_pkey PRIMARY KEY (id)
);

CREATE TABLE patron_identity (
	id uuid NOT NULL,
	date_created timestamp,
	date_updated timestamp,
	patron_id uuid REFERENCES patron (id),
	host_lms_id uuid REFERENCES host_lms (id),
	local_id varchar(200),
	home_identity boolean,
	CONSTRAINT patron_identity_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_pi_patron ON patron_identity(patron_id);
CREATE INDEX idx_hli_patron ON patron_identity(host_lms_id);

CREATE TABLE patron_request (
	id uuid primary key,
	date_created timestamp,
	date_updated timestamp,
	patron_id uuid REFERENCES patron (id),
	patron_agency_code varchar(200),
	bib_cluster_id uuid,
	pickup_location_code varchar(200),
	status_code varchar(200)
);

CREATE INDEX idx_pr_patron ON patron_request(patron_id);

CREATE TABLE supplier_request (
	id uuid primary key,
	patron_request_id uuid,
	item_id varchar(200),
	host_lms_code varchar(200),
	CONSTRAINT fk_patron_request FOREIGN KEY (patron_request_id) REFERENCES patron_request(id)
);

CREATE TABLE agency (
        id uuid primary key,
        code varchar(32),
        name varchar(200),
        host_lms_id uuid,
        CONSTRAINT fk_host_lms FOREIGN KEY (host_lms_id) REFERENCES host_lms(id)
);

CREATE TABLE location_symbol (
        id uuid primary key,
        authority varchar(32),
        code varchar(64),
        owning_location_fk uuid,
        CONSTRAINT fk_location FOREIGN KEY (owning_location_fk) REFERENCES location(id)
);

CREATE TABLE process_state (
        id uuid primary key,
        context uuid,
        process_name varchar(200),
        process_state JSONB
);

CREATE TABLE raw_source (
    id uuid NOT NULL,
    host_lms_id uuid NOT NULL,
    remote_id varchar(255) NOT NULL,
    json jsonb NOT NULL,
    CONSTRAINT raw_source_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_rs_host_lms ON raw_source(host_lms_id);
CREATE INDEX idx_rs_remote_id ON raw_source(remote_id);

CREATE TABLE shelving_location (
	id uuid NOT NULL,
	date_created timestamp,
	date_updated timestamp,
	code varchar(64),
	name varchar(64),
	host_system_id uuid,
	agency_id uuid,
	loan_policy varchar(32),
  CONSTRAINT shelving_location_pk PRIMARY KEY (id)
);

CREATE INDEX idx_sl_host_system ON shelving_location(host_system_id);
CREATE INDEX idx_sl_agency ON shelving_location(agency_id);

CREATE TABLE refdata_value (
	id uuid NOT NULL,
	context varchar(64),
	category varchar(64),
	value varchar(255),
	label varchar(255),
	CONSTRAINT refdata_value_pk PRIMARY KEY (id)
);

CREATE INDEX idx_rdv ON refdata_value(category,context);

CREATE TABLE reference_value_mapping (
	 id uuid NOT NULL,
	 from_context varchar(64),
	 from_category varchar(64),
	 from_value varchar(255),
	 to_context varchar(64),
	 to_category varchar(64),
	 to_value varchar(255),
   CONSTRAINT pk_reference_value_mapping primary key (id)
);

CREATE INDEX idx_rvm_mapping on reference_value_mapping(from_context,from_category,from_value,to_context);

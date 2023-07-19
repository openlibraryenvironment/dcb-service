CREATE TABLE cluster_record (
	id uuid PRIMARY KEY,
	date_created timestamp,
	date_updated timestamp,
	title text,
	selected_bib uuid,
        is_deleted boolean
);

CREATE INDEX idx_cluster_date_updated on cluster_record(date_updated);

CREATE TABLE bib_record (
	id uuid PRIMARY KEY,
	date_created timestamp,
	date_updated timestamp,
	source_system_id uuid,
	source_record_id varchar(256),
	title text,
	contributes_to uuid REFERENCES cluster_record(id),
	cluster_reason varchar(128),
	record_status varchar(8),
	type_of_record varchar(8),
	derived_type varchar(32),
	blocking_title text,
	canonical_metadata JSONB,
        metadata_score integer,
        process_version integer
);

CREATE INDEX idx_bib_source_system ON bib_record(source_system_id);
CREATE INDEX idx_bib_source_id ON bib_record(source_record_id);
CREATE INDEX idx_bib_contributes_to ON bib_record(contributes_to);

CREATE TABLE bib_identifier (
	id uuid PRIMARY KEY,
	owner_id uuid REFERENCES bib_record(id),
	value varchar(255),
	namespace varchar(255)
);

CREATE INDEX bib_identifier_value_idx on bib_identifier(value,namespace);
CREATE INDEX bib_identifier_owner_fk on bib_identifier(owner_id);

CREATE TABLE host_lms (
	id uuid PRIMARY KEY,
	code varchar(32),
	name varchar(200),
	lms_client_class varchar(200),
	client_config JSONB
);

CREATE TABLE location (
	id uuid PRIMARY KEY,
	code varchar(200),
	name varchar(255),
	type varchar(32),
	agency_fk uuid,
	host_system_id uuid,
	is_pickup boolean
);

CREATE TABLE patron (
	id uuid PRIMARY KEY,
	home_library_code varchar(200),
	date_created timestamp,
	date_updated timestamp
);

CREATE TABLE patron_identity (
	id uuid PRIMARY KEY,
	date_created timestamp,
	date_updated timestamp,
	patron_id uuid REFERENCES patron (id),
	host_lms_id uuid REFERENCES host_lms (id),
	local_id varchar(200),
	home_identity boolean,
        local_barcode varchar(200),
        local_ptype varchar(200),
        last_validated timestamp,
        local_names varchar(256),
        local_home_library_code varchar(256),
        resolved_agency_id uuid
);

CREATE INDEX idx_pi_patron ON patron_identity(patron_id, host_lms_id);
CREATE INDEX idx_hli_patron ON patron_identity(host_lms_id);

CREATE TABLE patron_request (
	id uuid PRIMARY KEY,
	date_created timestamp,
	date_updated timestamp,
	patron_hostlms_code varchar(200),
	patron_id uuid REFERENCES patron (id),
	bib_cluster_id uuid,
	pickup_location_code varchar(200),
        pickup_hostlms_code varchar(200),
        pickup_patron_id varchar(200),
        pickup_item_id varchar(200),
        pickup_item_status varchar(200),
        pickup_request_id varchar(200),
        pickup_request_status varchar(200),
	status_code varchar(200),
	local_request_id varchar(200),
	local_request_status varchar(32),
	local_item_id varchar(200),
	local_bib_id varchar(200),
        requesting_identity_id uuid REFERENCES patron_identity (id),
        description varchar(256),
        error_message varchar(256)
);



CREATE INDEX idx_pr_patron ON patron_request(patron_id);

CREATE TABLE supplier_request (
	id uuid PRIMARY KEY,
	patron_request_id uuid REFERENCES patron_request(id),
	local_item_id varchar(200),
	local_item_barcode varchar(200),
	local_item_location_code varchar(200),
	host_lms_code varchar(200),
	status_code varchar(200),
	local_id varchar(200),
	local_status varchar(32),
        virtual_identity_id uuid REFERENCES patron_identity (id),
        local_bib_id varchar(256),
        local_agency varchar(256),
        resolved_agency_id uuid
);

CREATE INDEX idx_lender_hold on supplier_request(host_lms_code, local_id);

CREATE TABLE agency (
	id uuid PRIMARY KEY,
	code varchar(32),
	name varchar(200),
	host_lms_id uuid REFERENCES host_lms(id)
);

CREATE TABLE location_symbol (
	id uuid PRIMARY KEY,
	authority varchar(32),
	code varchar(64),
	owning_location_fk uuid REFERENCES location(id)
);

CREATE TABLE process_state (
	id uuid PRIMARY KEY,
	context uuid,
	process_name varchar(200),
	process_state JSONB
);

CREATE TABLE raw_source (
	id uuid PRIMARY KEY,
	host_lms_id uuid NOT NULL,
	remote_id varchar(255) NOT NULL,
	json jsonb NOT NULL
);

CREATE INDEX idx_rs_host_lms ON raw_source(host_lms_id);
CREATE INDEX idx_rs_remote_id ON raw_source(remote_id);

CREATE TABLE shelving_location (
	id uuid PRIMARY KEY,
	date_created timestamp,
	date_updated timestamp,
	code varchar(64),
	name varchar(64),
	host_system_id uuid,
	agency_id uuid,
	location_id uuid,
	loan_policy varchar(32),
	CONSTRAINT fk_agency FOREIGN KEY (agency_id) REFERENCES agency(id),
	CONSTRAINT fk_location FOREIGN KEY (location_id) REFERENCES location(id)
);

CREATE INDEX idx_sl_host_system ON shelving_location(host_system_id);
CREATE INDEX idx_sl_agency ON shelving_location(agency_id);

CREATE TABLE refdata_value (
	id uuid PRIMARY KEY,
	context varchar(64),
	category varchar(64),
	value varchar(255),
	label varchar(255)
);

CREATE INDEX idx_rdv ON refdata_value(category,context);

CREATE TABLE reference_value_mapping (
	id uuid PRIMARY KEY,
	from_context varchar(64),
	from_category varchar(64),
	from_value varchar(255),
	to_context varchar(64),
	to_category varchar(64),
	to_value varchar(255),
	reciprocal boolean
);

CREATE INDEX idx_rvm_mapping on reference_value_mapping(from_context,from_category,from_value,to_context);

CREATE TABLE match_point (
  id uuid PRIMARY KEY,
  bib_id uuid NOT NULL,
  value uuid NOT NULL,
  CONSTRAINT fk_bib_id FOREIGN KEY (bib_id) REFERENCES public.bib_record (id)
);

CREATE INDEX idx_fk_bib_id ON match_point (bib_id);
CREATE INDEX idx_value ON match_point (value);

CREATE TABLE status_code (
        id uuid PRIMARY KEY,
        model varchar(64),
        code varchar(64),
        description varchar(128),
        tracked boolean
);

CREATE TABLE event_log (
	id uuid PRIMARY KEY,
	date_created timestamp,
	event_type varchar(64),
	event_summary varchar(128),
	additional_data jsonb
);

CREATE TABLE patron_request_audit (
	id uuid PRIMARY KEY,
	patron_request_id uuid,
	audit_date timestamp,
	brief_description varchar(256),
	audit_data JSONB,
        from_status varchar(200),
        to_status varchar(200)
);

CREATE INDEX pra_pr_fk ON patron_request_audit (patron_request_id);

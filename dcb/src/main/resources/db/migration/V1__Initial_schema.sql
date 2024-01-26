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
) partition by hash(id);

CREATE INDEX idx_bib_source_system ON bib_record(source_system_id);
CREATE INDEX idx_bib_source_id ON bib_record(source_record_id);
CREATE INDEX idx_bib_contributes_to ON bib_record(contributes_to);
CREATE INDEX idx_bib_record_derived_type ON bib_record USING hash(derived_type);

CREATE TABLE bib_record_1 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 0);
CREATE TABLE bib_record_2 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 1);
CREATE TABLE bib_record_3 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 2);
CREATE TABLE bib_record_4 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 3);
CREATE TABLE bib_record_5 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 4);
CREATE TABLE bib_record_6 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 5);
CREATE TABLE bib_record_7 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 6);
CREATE TABLE bib_record_8 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 7);
CREATE TABLE bib_record_9 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 8);
CREATE TABLE bib_record_10 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 9);
CREATE TABLE bib_record_11 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 10);
CREATE TABLE bib_record_12 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 11);
CREATE TABLE bib_record_13 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 12);
CREATE TABLE bib_record_14 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 13);
CREATE TABLE bib_record_15 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 14);
CREATE TABLE bib_record_16 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 15);
CREATE TABLE bib_record_17 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 16);
CREATE TABLE bib_record_18 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 17);
CREATE TABLE bib_record_19 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 18);
CREATE TABLE bib_record_20 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 19);
CREATE TABLE bib_record_21 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 20);
CREATE TABLE bib_record_22 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 21);
CREATE TABLE bib_record_23 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 22);
CREATE TABLE bib_record_24 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 23);
CREATE TABLE bib_record_25 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 24);
CREATE TABLE bib_record_26 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 25);
CREATE TABLE bib_record_27 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 26);
CREATE TABLE bib_record_28 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 27);
CREATE TABLE bib_record_29 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 28);
CREATE TABLE bib_record_30 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 29);
CREATE TABLE bib_record_31 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 30);
CREATE TABLE bib_record_32 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 31);
CREATE TABLE bib_record_33 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 32);
CREATE TABLE bib_record_34 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 33);
CREATE TABLE bib_record_35 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 34);
CREATE TABLE bib_record_36 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 35);
CREATE TABLE bib_record_37 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 36);
CREATE TABLE bib_record_38 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 37);
CREATE TABLE bib_record_39 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 38);
CREATE TABLE bib_record_40 PARTITION OF bib_record FOR VALUES WITH (MODULUS 40, REMAINDER 39);



CREATE TABLE bib_identifier (
	id uuid PRIMARY KEY,
	owner_id uuid REFERENCES bib_record(id),
	value varchar(255),
	namespace varchar(255)
) partition by hash(id);

CREATE INDEX bib_identifier_value_idx on bib_identifier(value,namespace);
CREATE INDEX bib_identifier_owner_fk on bib_identifier(owner_id);

CREATE TABLE bib_identifier_1 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 0);
CREATE TABLE bib_identifier_2 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 1);
CREATE TABLE bib_identifier_3 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 2);
CREATE TABLE bib_identifier_4 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 3);
CREATE TABLE bib_identifier_5 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 4);
CREATE TABLE bib_identifier_6 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 5);
CREATE TABLE bib_identifier_7 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 6);
CREATE TABLE bib_identifier_8 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 7);
CREATE TABLE bib_identifier_9 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 8);
CREATE TABLE bib_identifier_10 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 9);
CREATE TABLE bib_identifier_11 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 10);
CREATE TABLE bib_identifier_12 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 11);
CREATE TABLE bib_identifier_13 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 12);
CREATE TABLE bib_identifier_14 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 13);
CREATE TABLE bib_identifier_15 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 14);
CREATE TABLE bib_identifier_16 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 15);
CREATE TABLE bib_identifier_17 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 16);
CREATE TABLE bib_identifier_18 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 17);
CREATE TABLE bib_identifier_19 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 18);
CREATE TABLE bib_identifier_20 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 19);
CREATE TABLE bib_identifier_21 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 20);
CREATE TABLE bib_identifier_22 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 21);
CREATE TABLE bib_identifier_23 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 22);
CREATE TABLE bib_identifier_24 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 23);
CREATE TABLE bib_identifier_25 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 24);
CREATE TABLE bib_identifier_26 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 25);
CREATE TABLE bib_identifier_27 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 26);
CREATE TABLE bib_identifier_28 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 27);
CREATE TABLE bib_identifier_29 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 28);
CREATE TABLE bib_identifier_30 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 29);
CREATE TABLE bib_identifier_31 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 30);
CREATE TABLE bib_identifier_32 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 31);
CREATE TABLE bib_identifier_33 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 32);
CREATE TABLE bib_identifier_34 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 33);
CREATE TABLE bib_identifier_35 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 34);
CREATE TABLE bib_identifier_36 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 35);
CREATE TABLE bib_identifier_37 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 36);
CREATE TABLE bib_identifier_38 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 37);
CREATE TABLE bib_identifier_39 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 38);
CREATE TABLE bib_identifier_40 PARTITION OF bib_identifier FOR VALUES WITH (MODULUS 40, REMAINDER 39);



CREATE TABLE host_lms (
	id uuid PRIMARY KEY,
	code varchar(32),
	name varchar(200),
	lms_client_class varchar(200),
	ingest_source_class varchar(200),
	client_config JSONB
);

CREATE TABLE location (
	id uuid PRIMARY KEY,
	date_created timestamp,
	date_updated timestamp,
	code varchar(200),
	name varchar(255),
	type varchar(32),
	agency_fk uuid,
	host_system_id uuid,
	is_pickup boolean,
	parent_location_fk uuid,
	longitude real,
	latitude real,
	import_reference varchar(128),
	delivery_stops varchar(128),
	print_label varchar(128)
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
	resolved_agency_id uuid,
	canonical_ptype varchar(200)
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
	pickup_item_type varchar(32),
	pickup_request_id varchar(200),
	pickup_request_status varchar(200),
	status_code varchar(200),
	local_request_id varchar(200),
	local_request_status varchar(32),
	local_item_id varchar(200),
	local_item_status varchar(32),
	local_item_type varchar(32),
	local_bib_id varchar(200),
	requesting_identity_id uuid REFERENCES patron_identity (id),
	description varchar(256),
	error_message varchar(256),
	active_workflow varchar(32),
	pickup_location_context varchar(200),
	pickup_location_code_context varchar(200),
	requester_note text,
	protocol varchar(10),
	requested_volume_designation varchar(32)
);

CREATE INDEX idx_pr_patron ON patron_request(patron_id);

CREATE TABLE supplier_request (
	id uuid PRIMARY KEY,
	patron_request_id uuid REFERENCES patron_request(id),
	local_item_id varchar(200),
	local_item_barcode varchar(200),
	local_item_location_code varchar(200),
	local_item_status varchar(32),
	local_item_type varchar(32),
	canonical_item_type varchar(32),
	host_lms_code varchar(200),
	status_code varchar(200),
	local_id varchar(200),
	local_status varchar(32),
	virtual_identity_id uuid REFERENCES patron_identity (id),
	local_bib_id varchar(256),
	local_agency varchar(256),
	resolved_agency_id uuid,
	date_created timestamp,
	date_updated timestamp,
	is_active boolean,
	protocol varchar(10)
);

CREATE INDEX idx_lender_hold on supplier_request(host_lms_code, local_id);

CREATE TABLE agency (
	id uuid PRIMARY KEY,
	date_created timestamp,
	date_updated timestamp,
	code varchar(32) UNIQUE,
	name varchar(200),
	auth_profile VARCHAR(64),
	idp_url VARCHAR(200),
	longitude real,
	latitude real,
	host_lms_id uuid REFERENCES host_lms(id)
);




CREATE INDEX idx_agency_host_lms_id ON agency (host_lms_id);

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
) partition by hash(id);

CREATE TABLE raw_source_1 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 0);
CREATE TABLE raw_source_2 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 1);
CREATE TABLE raw_source_3 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 2);
CREATE TABLE raw_source_4 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 3);
CREATE TABLE raw_source_5 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 4);
CREATE TABLE raw_source_6 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 5);
CREATE TABLE raw_source_7 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 6);
CREATE TABLE raw_source_8 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 7);
CREATE TABLE raw_source_9 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 8);
CREATE TABLE raw_source_10 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 9);
CREATE TABLE raw_source_11 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 10);
CREATE TABLE raw_source_12 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 11);
CREATE TABLE raw_source_13 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 12);
CREATE TABLE raw_source_14 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 13);
CREATE TABLE raw_source_15 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 14);
CREATE TABLE raw_source_16 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 15);
CREATE TABLE raw_source_17 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 16);
CREATE TABLE raw_source_18 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 17);
CREATE TABLE raw_source_19 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 18);
CREATE TABLE raw_source_20 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 19);
CREATE TABLE raw_source_21 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 20);
CREATE TABLE raw_source_22 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 21);
CREATE TABLE raw_source_23 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 22);
CREATE TABLE raw_source_24 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 23);
CREATE TABLE raw_source_25 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 24);
CREATE TABLE raw_source_26 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 25);
CREATE TABLE raw_source_27 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 26);
CREATE TABLE raw_source_28 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 27);
CREATE TABLE raw_source_29 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 28);
CREATE TABLE raw_source_30 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 29);
CREATE TABLE raw_source_31 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 30);
CREATE TABLE raw_source_32 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 31);
CREATE TABLE raw_source_33 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 32);
CREATE TABLE raw_source_34 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 33);
CREATE TABLE raw_source_35 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 34);
CREATE TABLE raw_source_36 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 35);
CREATE TABLE raw_source_37 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 36);
CREATE TABLE raw_source_38 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 37);
CREATE TABLE raw_source_39 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 38);
CREATE TABLE raw_source_40 PARTITION OF raw_source FOR VALUES WITH (MODULUS 40, REMAINDER 39);


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
	label varchar(200),
	last_imported timestamp,
	reciprocal boolean,
	deleted boolean
);

CREATE INDEX idx_rvm_mapping on reference_value_mapping(from_context,from_category,from_value,to_context);

CREATE TABLE match_point (
	id uuid PRIMARY KEY,
	bib_id uuid NOT NULL,
	value uuid NOT NULL,
	CONSTRAINT fk_bib_id FOREIGN KEY (bib_id) REFERENCES public.bib_record (id)
) partition by hash(id);

CREATE INDEX idx_fk_bib_id ON match_point (bib_id);
CREATE INDEX idx_value ON match_point (value);

CREATE TABLE match_point_1 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 0);
CREATE TABLE match_point_2 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 1);
CREATE TABLE match_point_3 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 2);
CREATE TABLE match_point_4 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 3);
CREATE TABLE match_point_5 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 4);
CREATE TABLE match_point_6 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 5);
CREATE TABLE match_point_7 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 6);
CREATE TABLE match_point_8 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 7);
CREATE TABLE match_point_9 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 8);
CREATE TABLE match_point_10 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 9);
CREATE TABLE match_point_11 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 10);
CREATE TABLE match_point_12 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 11);
CREATE TABLE match_point_13 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 12);
CREATE TABLE match_point_14 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 13);
CREATE TABLE match_point_15 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 14);
CREATE TABLE match_point_16 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 15);
CREATE TABLE match_point_17 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 16);
CREATE TABLE match_point_18 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 17);
CREATE TABLE match_point_19 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 18);
CREATE TABLE match_point_20 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 19);
CREATE TABLE match_point_21 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 20);
CREATE TABLE match_point_22 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 21);
CREATE TABLE match_point_23 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 22);
CREATE TABLE match_point_24 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 23);
CREATE TABLE match_point_25 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 24);
CREATE TABLE match_point_26 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 25);
CREATE TABLE match_point_27 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 26);
CREATE TABLE match_point_28 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 27);
CREATE TABLE match_point_29 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 28);
CREATE TABLE match_point_30 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 29);
CREATE TABLE match_point_31 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 30);
CREATE TABLE match_point_32 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 31);
CREATE TABLE match_point_33 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 32);
CREATE TABLE match_point_34 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 33);
CREATE TABLE match_point_35 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 34);
CREATE TABLE match_point_36 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 35);
CREATE TABLE match_point_37 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 36);
CREATE TABLE match_point_38 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 37);
CREATE TABLE match_point_39 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 38);
CREATE TABLE match_point_40 PARTITION OF match_point FOR VALUES WITH (MODULUS 40, REMAINDER 39);



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

CREATE TABLE numeric_range_mapping (
	id uuid PRIMARY KEY,
	context varchar(128) NOT NULL,
	domain varchar(128) NOT NULL,
	lower_bound integer NOT NULL,
	upper_bound integer NOT NULL,
	target_context varchar(128),
	mapped_value varchar(128)
);

CREATE INDEX idx_numeric_range_mapping ON numeric_range_mapping(context,domain,lower_bound,upper_bound);

CREATE TABLE agency_group (
	id uuid PRIMARY KEY,
	code varchar(32) NOT NULL,
	name varchar(128) NOT NULL
);

CREATE TABLE agency_group_member (
	id uuid PRIMARY KEY,
	agency_id uuid,
	group_id uuid,
	CONSTRAINT fk_agency FOREIGN KEY (agency_id) REFERENCES agency(id),
	CONSTRAINT fk_agency_group FOREIGN KEY (group_id) REFERENCES agency_group(id)
);

CREATE TABLE dcb_grant (
	id uuid PRIMARY KEY,
	grant_resource_owner varchar(128),
	grant_resource_type varchar(128),
	grant_resource_id varchar(128),
	granted_perm varchar(128),
	grantee_type varchar(128),
	grantee varchar(128),
	grant_option boolean
);

CREATE INDEX all_grant_fields ON dcb_grant (grant_resource_owner,grant_resource_type,grant_resource_id,granted_perm,grantee_type,grantee);

CREATE TABLE IF NOT EXISTS shared_index_queue_entry (
	id uuid NOT NULL,
	cluster_id uuid NOT NULL,
	cluster_date_updated timestamp without time zone NOT NULL,
	date_created timestamp without time zone NOT NULL,
	date_updated timestamp without time zone NOT NULL,
	CONSTRAINT shared_index_queue_entry_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS shared_index_queue_entry_cluster_id_idx
	ON public.shared_index_queue_entry(cluster_id);

CREATE INDEX IF NOT EXISTS shared_index_queue_entry_cluster_date_updated_idx
	ON shared_index_queue_entry(cluster_date_updated);

CREATE TABLE delivery_network_edge (
	id uuid PRIMARY KEY,
	date_created timestamp,
	date_updated timestamp,
	from_location_fk uuid,
	to_location_fk uuid,
	active boolean,
	planning_cost integer,
	route_code varchar(32),
	segment_code varchar(32),
	CONSTRAINT fk_loc_from FOREIGN KEY (from_location_fk) REFERENCES public.location (id),
	CONSTRAINT fk_loc_to FOREIGN KEY (to_location_fk) REFERENCES public.location (id)
);


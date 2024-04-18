CREATE TABLE Person (
        id uuid PRIMARY KEY,
        first_name varchar(128),
        last_name varchar(128),
        role varchar(128),
        email varchar(255),
        is_primary_contact boolean
);

CREATE TABLE library (
        id uuid PRIMARY KEY,
        agency_code varchar(32) NOT NULL,
        full_name varchar(200) NOT NULL,
        abbreviated_name varchar(128) NOT NULL,
        short_name varchar(32) NOT NULL,
        training boolean,
        site_designation varchar(200),
        backup_downtime_schedule varchar(200),
        support_hours varchar(200),
				discovery_system varchar(200),
				patron_website varchar(200),
				host_lms_configuration varchar(200),
				longitude real,
				latitude real,
				address varchar(200),
				agency_id uuid REFERENCES agency(id),
				second_host_lms_id uuid REFERENCES host_lms(id),
				type varchar(32)
);

CREATE TABLE library_contact (
		id uuid,
    library_id uuid REFERENCES library(id),
    person_id uuid REFERENCES person(id),
    PRIMARY KEY (library_id, person_id)
);

CREATE TABLE library_group (
	id uuid PRIMARY KEY,
	code varchar(32) NOT NULL,
	name varchar(128) NOT NULL,
	type varchar(32) NOT NULL
);

CREATE TABLE consortium (
  id uuid PRIMARY KEY,
	name varchar(128),
	library_group_id uuid,
	FOREIGN KEY (library_group_id) REFERENCES library_group(id)
);


CREATE TABLE library_group_member (
	id uuid PRIMARY KEY,
	library_id uuid,
	library_group_id uuid,
	CONSTRAINT fk_library FOREIGN KEY (library_id) REFERENCES library(id),
	CONSTRAINT fk_library_group FOREIGN KEY (library_group_id) REFERENCES library_group(id)
);



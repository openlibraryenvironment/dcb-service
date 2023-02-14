create table bib_record (
	id uuid primary key,
	title varchar(1024)
);

create table bib_identifier (
	id uuid primary key,
	value varchar(255),
	namespace varchar(255)
);

create table agency (
	id uuid primary key,
	name varchar(200)
);

create table location (
	id uuid primary key,
	code varchar(200),
	name varchar(255)
);

create table patron_request (
	id uuid primary key,
	patron_id varchar(200),
	patron_agency_code varchar(200),
	bib_cluster_id uuid varchar(200),
	pickup_location_code varchar(200)
);

create table bib_record (
	id uuid primary key,
        date_created timestamp,
        date_updated timestamp,
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
	bib_cluster_id uuid,
	pickup_location_code varchar(200)
);

create table supplier_request (
	id uuid primary key,
	patron_request_id uuid,
	holdings_item_id uuid,
	holdings_agency_code varchar(200),
	CONSTRAINT fk_patron_request FOREIGN KEY (patron_request_id) REFERENCES patron_request(id)
);

--create table patron_request_supplier_request (
--	supplier_request_id uuid primary key,
--	patron_request_id uuid,
--	holdings_item_id uuid,
--	holdings_agency_code varchar(200),
--	CONSTRAINT fk_patron_request
--  FOREIGN KEY(patron_request_id)
--  REFERENCES patron_request(id)
--);

alter table consortium add last_edited_by varchar(100);
alter table consortium add reason varchar(100);
alter table consortium add change_reference_url varchar(200);
alter table consortium add change_category varchar(200);

alter table consortium add display_name varchar(200);
alter table consortium add website_url varchar(200);
alter table consortium add catalogue_search_url varchar(200);
alter table consortium add description varchar(400);

alter table consortium add header_image_url varchar(400);
alter table consortium add header_image_uploader varchar(200);
alter table consortium add header_image_uploader_email varchar(200);

alter table consortium add about_image_url varchar(400);
alter table consortium add about_image_uploader varchar(200);
alter table consortium add about_image_uploader_email varchar(200);

CREATE TABLE functional_setting (
	id uuid PRIMARY KEY,
	name varchar(200),
	enabled boolean,
	description varchar(200),
	last_edited_by varchar(100),
	reason varchar(100),
	change_reference_url varchar(200),
	change_category varchar(200)
);

CREATE TABLE consortium_contact (
		id uuid,
    consortium_id uuid REFERENCES consortium(id),
    person_id uuid REFERENCES person(id),
		last_edited_by varchar(100),
		reason varchar(100),
		change_reference_url varchar(200),
		change_category varchar(200),
		PRIMARY KEY (consortium_id, person_id)
);

CREATE TABLE consortium_functional_setting (
		id uuid,
    consortium_id uuid REFERENCES consortium(id),
    functional_setting_id uuid REFERENCES functional_setting(id),
		last_edited_by varchar(100),
		reason varchar(100),
		change_reference_url varchar(200),
		change_category varchar(200),
		PRIMARY KEY (consortium_id, functional_setting_id)
);

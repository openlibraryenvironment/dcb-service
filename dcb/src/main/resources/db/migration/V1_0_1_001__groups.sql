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

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


CREATE TABLE numeric_range_mapping (
        id uuid PRIMARY KEY,
        context varchar(128) NOT NULL,
        domain varchar(128) NOT NULL,
        lowerBound integer NOT NULL,
        upperBound integer NOT NULL,
        mapped_value varchar(128)
);

CREATE INDEX idx_numeric_range_mapping ON numeric_range_mapping(context,domain,lowerBound,upperBound);


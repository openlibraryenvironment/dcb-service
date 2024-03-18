CREATE TABLE object_ruleset
(
    name character varying(128) NOT NULL,
    type character varying(128) NOT NULL,
    conditions jsonb NOT NULL,
    CONSTRAINT object_ruleset_pkey PRIMARY KEY (name)
);

ALTER TABLE host_lms add suppression_ruleset_name varchar(128);

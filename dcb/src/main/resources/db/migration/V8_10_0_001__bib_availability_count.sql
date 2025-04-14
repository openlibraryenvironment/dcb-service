CREATE TABLE IF NOT EXISTS bib_availability_count
(
    id uuid NOT NULL,
    bib_id uuid NOT NULL,
    host_lms uuid NOT NULL,
    remote_location_code character varying(20),
    internal_location_code character varying(20),
    count integer NOT NULL,
    status character varying(25) NOT NULL,
    mapping_result character varying(255),
    last_updated timestamp without time zone NOT NULL,
    CONSTRAINT bib_availability_count_pkey PRIMARY KEY (id, bib_id)
) partition by hash(bib_id);

CREATE INDEX IF NOT EXISTS bib_availability_count_bib_id_idx
    ON bib_availability_count USING btree (bib_id);

CREATE INDEX IF NOT EXISTS bib_availability_count_internal_location_code_idx
    ON bib_availability_count USING btree(internal_location_code ASC NULLS FIRST);

CREATE INDEX IF NOT EXISTS bib_availability_count_remote_location_code_idx
    ON bib_availability_count USING btree (remote_location_code ASC NULLS FIRST);

CREATE INDEX IF NOT EXISTS bib_availability_count_status_idx
    ON bib_availability_count USING btree(status);
    
CREATE TABLE bib_availability_count_1 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 0);
CREATE TABLE bib_availability_count_2 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 1);
CREATE TABLE bib_availability_count_3 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 2);
CREATE TABLE bib_availability_count_4 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 3);
CREATE TABLE bib_availability_count_5 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 4);
CREATE TABLE bib_availability_count_6 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 5);
CREATE TABLE bib_availability_count_7 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 6);
CREATE TABLE bib_availability_count_8 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 7);
CREATE TABLE bib_availability_count_9 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 8);
CREATE TABLE bib_availability_count_10 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 9);
CREATE TABLE bib_availability_count_11 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 10);
CREATE TABLE bib_availability_count_12 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 11);
CREATE TABLE bib_availability_count_13 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 12);
CREATE TABLE bib_availability_count_14 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 13);
CREATE TABLE bib_availability_count_15 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 14);
CREATE TABLE bib_availability_count_16 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 15);
CREATE TABLE bib_availability_count_17 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 16);
CREATE TABLE bib_availability_count_18 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 17);
CREATE TABLE bib_availability_count_19 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 18);
CREATE TABLE bib_availability_count_20 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 19);
CREATE TABLE bib_availability_count_21 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 20);
CREATE TABLE bib_availability_count_22 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 21);
CREATE TABLE bib_availability_count_23 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 22);
CREATE TABLE bib_availability_count_24 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 23);
CREATE TABLE bib_availability_count_25 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 24);
CREATE TABLE bib_availability_count_26 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 25);
CREATE TABLE bib_availability_count_27 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 26);
CREATE TABLE bib_availability_count_28 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 27);
CREATE TABLE bib_availability_count_29 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 28);
CREATE TABLE bib_availability_count_30 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 29);
CREATE TABLE bib_availability_count_31 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 30);
CREATE TABLE bib_availability_count_32 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 31);
CREATE TABLE bib_availability_count_33 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 32);
CREATE TABLE bib_availability_count_34 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 33);
CREATE TABLE bib_availability_count_35 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 34);
CREATE TABLE bib_availability_count_36 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 35);
CREATE TABLE bib_availability_count_37 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 36);
CREATE TABLE bib_availability_count_38 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 37);
CREATE TABLE bib_availability_count_39 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 38);
CREATE TABLE bib_availability_count_40 PARTITION OF bib_availability_count FOR VALUES WITH (MODULUS 40, REMAINDER 39);

    
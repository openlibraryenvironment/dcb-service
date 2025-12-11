CREATE TABLE IF NOT EXISTS process_audit_log_entry
(
    id uuid NOT NULL,
    process_type character varying(15) NOT NULL,
    process_id uuid NOT NULL,
    subject_id uuid NOT NULL,
    message text NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    CONSTRAINT process_audit_log_entry_pkey PRIMARY KEY (id)
	
) partition by hash(id);

CREATE INDEX IF NOT EXISTS process_audit_log_entry_id_idx
    ON process_audit_log_entry USING btree
    (id ASC NULLS LAST)
    WITH (deduplicate_items=True);

CREATE INDEX IF NOT EXISTS process_audit_log_entry_idx
    ON process_audit_log_entry USING btree
    (subject_id ASC NULLS LAST, process_type ASC NULLS LAST, process_id ASC NULLS LAST, "timestamp" ASC NULLS LAST)
    WITH (deduplicate_items=True);

CREATE TABLE process_audit_log_entry_1 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 0);
CREATE TABLE process_audit_log_entry_2 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 1);
CREATE TABLE process_audit_log_entry_3 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 2);
CREATE TABLE process_audit_log_entry_4 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 3);
CREATE TABLE process_audit_log_entry_5 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 4);
CREATE TABLE process_audit_log_entry_6 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 5);
CREATE TABLE process_audit_log_entry_7 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 6);
CREATE TABLE process_audit_log_entry_8 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 7);
CREATE TABLE process_audit_log_entry_9 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 8);
CREATE TABLE process_audit_log_entry_10 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 9);
CREATE TABLE process_audit_log_entry_11 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 10);
CREATE TABLE process_audit_log_entry_12 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 11);
CREATE TABLE process_audit_log_entry_13 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 12);
CREATE TABLE process_audit_log_entry_14 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 13);
CREATE TABLE process_audit_log_entry_15 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 14);
CREATE TABLE process_audit_log_entry_16 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 15);
CREATE TABLE process_audit_log_entry_17 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 16);
CREATE TABLE process_audit_log_entry_18 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 17);
CREATE TABLE process_audit_log_entry_19 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 18);
CREATE TABLE process_audit_log_entry_20 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 19);
CREATE TABLE process_audit_log_entry_21 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 20);
CREATE TABLE process_audit_log_entry_22 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 21);
CREATE TABLE process_audit_log_entry_23 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 22);
CREATE TABLE process_audit_log_entry_24 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 23);
CREATE TABLE process_audit_log_entry_25 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 24);
CREATE TABLE process_audit_log_entry_26 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 25);
CREATE TABLE process_audit_log_entry_27 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 26);
CREATE TABLE process_audit_log_entry_28 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 27);
CREATE TABLE process_audit_log_entry_29 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 28);
CREATE TABLE process_audit_log_entry_30 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 29);
CREATE TABLE process_audit_log_entry_31 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 30);
CREATE TABLE process_audit_log_entry_32 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 31);
CREATE TABLE process_audit_log_entry_33 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 32);
CREATE TABLE process_audit_log_entry_34 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 33);
CREATE TABLE process_audit_log_entry_35 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 34);
CREATE TABLE process_audit_log_entry_36 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 35);
CREATE TABLE process_audit_log_entry_37 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 36);
CREATE TABLE process_audit_log_entry_38 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 37);
CREATE TABLE process_audit_log_entry_39 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 38);
CREATE TABLE process_audit_log_entry_40 PARTITION OF process_audit_log_entry FOR VALUES WITH (MODULUS 40, REMAINDER 39);

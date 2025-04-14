CREATE TABLE if not exists match_point_v2
(
    id uuid NOT NULL,
    bib_id uuid NOT NULL,
    value uuid NOT NULL,
    domain varchar(200),
    CONSTRAINT fk_bib_id FOREIGN KEY (bib_id) REFERENCES public.bib_record (id),
    CONSTRAINT pk_match_point_v2 PRIMARY KEY (value, bib_id)
) partition by hash(value);

CREATE TABLE match_point_v2_p00 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE match_point_v2_p01 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE match_point_v2_p02 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE match_point_v2_p03 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE match_point_v2_p04 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE match_point_v2_p05 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE match_point_v2_p06 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE match_point_v2_p07 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE match_point_v2_p08 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE match_point_v2_p09 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE match_point_v2_p10 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE match_point_v2_p11 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE match_point_v2_p12 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE match_point_v2_p13 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE match_point_v2_p14 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE match_point_v2_p15 PARTITION OF match_point_v2 FOR VALUES WITH (MODULUS 16, REMAINDER 15);

CREATE INDEX IF NOT EXISTS idx_fk_bib_id_2 ON match_point_v2 using btree (bib_id);
CREATE INDEX IF NOT EXISTS idx_mp_id_2 ON match_point_v2 using btree (id);

select now();
insert into match_point_v2 ( select * from match_point );
select now();

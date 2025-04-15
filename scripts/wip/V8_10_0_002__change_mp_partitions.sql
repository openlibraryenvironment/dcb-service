CREATE TABLE if not exists match_point2
(
  id uuid NOT NULL,
  bib_id uuid NOT NULL,
  value uuid NOT NULL,
  domain varchar(200),
  CONSTRAINT fk_bib_id FOREIGN KEY (bib_id) REFERENCES public.bib_record (id),
  CONSTRAINT pk_match_point2 PRIMARY KEY (value, bib_id)
) partition by hash(value);

CREATE TABLE match_point_p00 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE match_point_p01 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE match_point_p02 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE match_point_p03 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE match_point_p04 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE match_point_p05 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE match_point_p06 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE match_point_p07 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE match_point_p08 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE match_point_p09 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE match_point_p10 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE match_point_p11 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE match_point_p12 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE match_point_p13 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE match_point_p14 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE match_point_p15 PARTITION OF match_point2 FOR VALUES WITH (MODULUS 16, REMAINDER 15);

CREATE INDEX IF NOT EXISTS idx_fk_bib_id_2 ON match_point2 using btree (bib_id);
CREATE INDEX IF NOT EXISTS idx_mp_id_2 ON match_point2 using btree (id);

select now();
insert into match_point2 ( select * from match_point );
select now();

alter table match_point rename to old_match_point;
alter table match_point2 rename to match_point;

drop table if exists match_point_1;
drop table if exists match_point_2;
drop table if exists match_point_3;
drop table if exists match_point_4;
drop table if exists match_point_5;
drop table if exists match_point_6;
drop table if exists match_point_7;
drop table if exists match_point_8;
drop table if exists match_point_9;
drop table if exists match_point_10;
drop table if exists match_point_11;
drop table if exists match_point_12;
drop table if exists match_point_13;
drop table if exists match_point_14;
drop table if exists match_point_15;
drop table if exists match_point_16;
drop table if exists match_point_17;
drop table if exists match_point_18;
drop table if exists match_point_19;
drop table if exists match_point_20;
drop table if exists match_point_21;
drop table if exists match_point_22;
drop table if exists match_point_23;
drop table if exists match_point_24;
drop table if exists match_point_25;
drop table if exists match_point_26;
drop table if exists match_point_27;
drop table if exists match_point_28;
drop table if exists match_point_29;
drop table if exists match_point_30;
drop table if exists match_point_31;
drop table if exists match_point_32;
drop table if exists match_point_33;
drop table if exists match_point_34;
drop table if exists match_point_35;
drop table if exists match_point_36;
drop table if exists match_point_37;
drop table if exists match_point_38;
drop table if exists match_point_39;
drop table if exists match_point_40;
drop table if exists old_match_point cascade;

CREATE TABLE if not exists match_point_v2
(
    id uuid PRIMARY KEY,
    bib_id uuid NOT NULL,
    value uuid NOT NULL,
    domain varchar(200),
    CONSTRAINT fk_bib_id FOREIGN KEY (bib_id) REFERENCES public.bib_record (id)
);

CREATE INDEX IF NOT EXISTS idx_fk_bib_id_2 ON match_point_v2 using btree (bib_id);
CREATE INDEX IF NOT EXISTS idx_value_2 ON match_point_v2 using btree (value);

CREATE OR REPLACE FUNCTION auto_partition_match_points()
RETURNS TRIGGER AS $$
  	DECLARE
	  val text;
	  sourceTableFull text;
    tableName text;
	BEGIN
    -- Partition into 16 buckets. Important to add bib_Id into hash to prevent collisions
	  val := mod(abs(hashtext(NEW.value::text||NEW.bib_id::text)::int),16);
    sourceTableFull := TG_TABLE_SCHEMA::text || '.' || TG_TABLE_NAME::text;
    tableName := sourceTableFull || '_' || regexp_replace(val, '\W+', '_', 'g');
	  EXECUTE format('CREATE TABLE IF NOT EXISTS %s (
		LIKE %s INCLUDING ALL
	  ) INHERITS ( %s );', tableName, sourceTableFull, sourceTableFull);

	  EXECUTE 'INSERT INTO ' || tableName || ' SELECT $1.*' USING NEW;
	  RETURN NULL; -- Suppresses original query as we've inserted into the correct target.
	END;
$$
LANGUAGE plpgsql;

-- Add the insert trigger
CREATE OR REPLACE TRIGGER partition_mp_insert_trigger
  BEFORE INSERT ON match_point_v2
  FOR EACH ROW EXECUTE FUNCTION auto_partition_match_points();


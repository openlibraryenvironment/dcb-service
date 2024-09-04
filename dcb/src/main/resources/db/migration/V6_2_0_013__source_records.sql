CREATE TABLE source_record
(
    id uuid NOT NULL,
    host_lms_id uuid NOT NULL,
    remote_id character varying(128) NOT NULL,
    last_fetched timestamp without time zone NOT NULL,
    last_processed timestamp without time zone,
    processing_state character varying(32),
    processing_information text,
    source_record_data jsonb,
    CONSTRAINT source_record_pkey PRIMARY KEY (id),
    CONSTRAINT source_record_remote_id_key UNIQUE (remote_id)
);

CREATE INDEX IF NOT EXISTS source_record_last_fetched_idx
    ON source_record USING btree (last_fetched);

CREATE INDEX IF NOT EXISTS source_record_last_processed_idx
    ON source_record USING btree (last_fetched ASC NULLS FIRST);

CREATE INDEX IF NOT EXISTS source_record_processing_state_idx
    ON source_record USING hash (processing_state);

CREATE UNIQUE INDEX IF NOT EXISTS source_record_remote_id_idx
    ON source_record USING btree (remote_id);
    
CREATE OR REPLACE FUNCTION auto_partition_source_records()
RETURNS TRIGGER AS $$
  	DECLARE
	  sourceId text;
	  sourceTableFull text;
    tableName text;
	BEGIN
	  sourceId := NEW.host_lms_id::text;
      sourceTableFull := TG_TABLE_SCHEMA::text || '.' || TG_TABLE_NAME::text;
      tableName := sourceTableFull || '_' || regexp_replace(sourceId, '\W+', '_', 'g');
	  EXECUTE format('CREATE TABLE IF NOT EXISTS %s (
		LIKE %s INCLUDING ALL,
	    CHECK ( host_lms_id = %L::uuid )
	  ) INHERITS ( %s );', tableName, sourceTableFull, sourceId, sourceTableFull);

	  EXECUTE 'INSERT INTO ' || tableName || ' SELECT $1.*' USING NEW;
	  RETURN NULL; -- Suppresses original query as we've inserted into the correct target.
	END;
$$
LANGUAGE plpgsql;

-- Add the insert trigger
CREATE TRIGGER partition_source_insert_trigger
  BEFORE INSERT ON source_record
  FOR EACH ROW EXECUTE FUNCTION auto_partition_source_records();


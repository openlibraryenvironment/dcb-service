CREATE INDEX IF NOT EXISTS bib_record_process_version_idx
	ON bib_record USING btree (process_version ASC NULLS FIRST);

CREATE INDEX IF NOT EXISTS bib_record_source_record_uuid_idx
    ON bib_record USING btree (source_record_uuid ASC NULLS LAST);

DO $$
DECLARE
  r record;
BEGIN
  FOR r IN SELECT inhrelid::regclass::text AS child_table
           FROM pg_inherits
           WHERE inhparent = 'public.bib_record'::regclass
  LOOP
    EXECUTE format( 'CREATE INDEX IF NOT EXISTS %I ON %I USING btree (process_version ASC NULLS FIRST)', r.child_table || '_process_version_idx', r.child_table );
	EXECUTE format( 'CREATE INDEX IF NOT EXISTS %I ON %I USING btree (source_record_uuid ASC NULLS LAST)', r.child_table || '_source_record_uuid_idx', r.child_table );
  END LOOP;
END$$;
ALTER TABLE bib_availability_count add grace_period_end timestamp without time zone;

CREATE INDEX IF NOT EXISTS grace_period_end_idx
	ON bib_availability_count USING btree (grace_period_end ASC NULLS FIRST);

DO $$
DECLARE
  r record;
BEGIN
  FOR r IN SELECT inhrelid::regclass::text AS child_table
           FROM pg_inherits
           WHERE inhparent = 'public.bib_availability_count'::regclass
  LOOP
    EXECUTE format( 'CREATE INDEX IF NOT EXISTS %I ON %I USING btree (grace_period_end ASC NULLS FIRST)', r.child_table || '_grace_period_end_idx', r.child_table );
  END LOOP;
END$$;



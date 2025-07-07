CREATE INDEX IF NOT EXISTS sr_date_and_state_idx ON source_record (last_processed, processing_state);
ALTER TABLE source_record add date_created timestamp;
ALTER TABLE source_record add date_updated timestamp;
CREATE INDEX IF NOT EXISTS sr_idx_dlc ON source_record (date_created);
CREATE INDEX IF NOT EXISTS sr_idx_dlu ON source_record (date_updated);

DO $$
DECLARE
  r record;
BEGIN
  FOR r IN SELECT inhrelid::regclass::text AS child_table
           FROM pg_inherits
           WHERE inhparent = 'public.source_record'::regclass
  LOOP
    EXECUTE format(
      'CREATE INDEX IF NOT EXISTS %I ON %I (last_processed, processing_state)',
      'sr_date_and_state_' || r.child_table,
      r.child_table
    );
    EXECUTE format( 'ALTER TABLE %I ADD COLUMN IF NOT EXISTS date_created timestamp', r.child_table);
    EXECUTE format( 'ALTER TABLE %I ADD COLUMN IF NOT EXISTS date_updated timestamp', r.child_table);
    EXECUTE format( 'CREATE INDEX IF NOT EXISTS %I ON %I (date_created)', 'sr_idx_dc_' || r.child_table, r.child_table );
    EXECUTE format( 'CREATE INDEX IF NOT EXISTS %I ON %I (date_updated)', 'sr_idx_du_' || r.child_table, r.child_table );
  END LOOP;
END$$;

ALTER TABLE cluster_record ADD COLUMN last_indexed timestamp without time zone;
CREATE INDEX IF NOT EXISTS cluster_record_last_indexed_idx
  ON cluster_record USING btree(last_indexed ASC NULLS FIRST);
DROP TABLE IF EXISTS shared_index_queue_entry CASCADE;
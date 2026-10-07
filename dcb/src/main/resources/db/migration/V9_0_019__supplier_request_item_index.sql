-- Serves the lookups of active supplier requests for one supplier copy (resolution's same-copy filter, walk-up)
CREATE INDEX IF NOT EXISTS idx_supplier_request_item ON supplier_request (host_lms_code, local_item_id) WHERE is_active;

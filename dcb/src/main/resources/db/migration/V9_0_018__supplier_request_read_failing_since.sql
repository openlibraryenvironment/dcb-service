-- When the current run of failed reads of the lender's hold began; null while reads succeed
ALTER TABLE supplier_request ADD COLUMN local_read_failing_since timestamp;

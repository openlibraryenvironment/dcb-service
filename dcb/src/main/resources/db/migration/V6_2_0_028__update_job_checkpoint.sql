update job_checkpoint
set value = '{"updatedDate": {"fromDate": "2024-11-01T00:00:00"}}'::JSONB
where value::text = '{"updatedDate": {}}';

-- remote_id is not globally unique without host_lms_id to go with it
alter table source_record drop constraint source_record_remote_id_key;
drop index if exists source_record_remote_id_idx;
-- Unlike some other constraints, postgres DOES create indexes on unique constraints, so don't do double work
-- create unique index source_record_remote_id_idx on source_record(remote_id, host_lms_id);
alter table source_record add constraint source_record_remote_id_key UNIQUE(remote_id,host_lms_id);

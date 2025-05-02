CREATE TABLE alarm (
  id uuid PRIMARY KEY,
  code varchar(64),
  created timestamp,
  last_seen timestamp,
  repeat_count integer,
  expires timestamp,
  alarm_details JSONB
);

create index alarm_idx on alarm(code, last_seen);

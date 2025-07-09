create table syslog (
  id        BIGSERIAL PRIMARY KEY,
  category  VARCHAR(32),
  message   TEXT,
  details   JSONB,
  ts        TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX idx_syslog_ts_brin ON syslog USING BRIN (ts);

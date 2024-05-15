CREATE TABLE IF NOT EXISTS job_checkpoint
(
    id uuid NOT NULL,
    value jsonb,
    CONSTRAINT job_checkpoint_pkey PRIMARY KEY (id)
);
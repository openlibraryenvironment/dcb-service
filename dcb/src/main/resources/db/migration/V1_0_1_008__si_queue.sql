CREATE TABLE IF NOT EXISTS shared_index_queue_entry
(
    id uuid NOT NULL,
    cluster_id uuid NOT NULL,
    cluster_date_updated timestamp without time zone NOT NULL,
    date_created timestamp without time zone NOT NULL,
    date_updated timestamp without time zone NOT NULL,
    CONSTRAINT shared_index_queue_entry_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS shared_index_queue_entry_cluster_id_idx
    ON public.shared_index_queue_entry(cluster_id);

CREATE INDEX IF NOT EXISTS shared_index_queue_entry_cluster_date_updated_idx
    ON shared_index_queue_entry(cluster_date_updated);
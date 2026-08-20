-- Indexes for the request and collection statistics endpoints (/patrons/requests/stats/**).
--

-- === patron_request: the statistics endpoints ===============================================
-- Borrower- and supplier-side rollups all filter by host LMS code, then status, then a
-- date_created window - so the composite order matches the predicate order.
CREATE INDEX IF NOT EXISTS pr_stats_borrower_idx
	ON patron_request (patron_hostlms_code, status_code, date_created);

CREATE INDEX IF NOT EXISTS pr_stats_supplier_idx
	ON patron_request (local_item_hostlms_code, status_code, date_created);

-- Collection analysis joins requests to clusters; demand-by-pickup-location groups on code.
CREATE INDEX IF NOT EXISTS pr_stats_cluster_idx
	ON patron_request (bib_cluster_id);

CREATE INDEX IF NOT EXISTS pr_stats_pickup_idx
	ON patron_request (pickup_location_code);

-- === patron_request_audit ===================================================================
-- The status-flow time series counts transitions INTO a status, per bucket.
CREATE INDEX IF NOT EXISTS pra_to_status_idx
	ON patron_request_audit (to_status, patron_request_id);

-- THE single audit_date index. Three things lean on it: the stats flow time-series range scan,
-- the Audit Explorer grid's "newest first" page, and the incidence chart's window bound on
-- feat/audit-incidence. There was no index on audit_date at all before this, so an unindexed
-- ORDER BY audit_date DESC LIMIT 50 sorted the whole table - measured at 5M rows,
-- 245ms -> 0.61ms. Builds in ~1s and costs 107 MB. That regression is on main today, which is
-- why the index ships with whichever of the two features lands first rather than waiting.
--
-- Do not add a second one under a different name. See the header.
CREATE INDEX IF NOT EXISTS pra_audit_date_idx
	ON patron_request_audit (audit_date);

-- N-1.3 — the library level of the patron-facing brand chain.
--
-- `library` carried no branding at all before this: full_name, short_name,
-- abbreviated_name and patron_website were the lot. The names already exist, so no
-- name column is added here, and patron_website is the logo's link target rather
-- than a second URL to keep in step with it.
--
-- No patron_welcome at this level. Welcome copy resolves outward through the chain,
-- so a library that supplies none keeps the consortium's — and asking several
-- hundred member libraries each to write a paragraph produces several hundred empty
-- paragraphs.
--
-- Additive and nullable. See V8_73_001 for why there is no colour column.

alter table library add brand_logo_url varchar(400);
alter table library add brand_logo_alt varchar(255);
alter table library add default_theme_name varchar(64);

-- The anonymous /discovery/libraries directory now reaches library from agency, once
-- per agency row, through a LATERAL join. The table is bounded by the consortium's
-- membership (hundreds, not millions) so the sequential scans it replaces would not
-- have hurt — but the join is on an unindexed FK on an unauthenticated endpoint, and
-- an index is cheaper than the argument about whether it matters.
create index if not exists idx_library_agency_id on library (agency_id);

-- Patron-facing brand for a library (N-1.3), the second level of the brand chain.
-- Additive and nullable: rewrites no rows.
-- Why a library has a mark but no background: developer:branding.adoc, "Migration choices".

alter table library add brand_logo_url varchar(400);
alter table library add brand_logo_alt varchar(255);
alter table library add default_theme_name varchar(64);

-- Serves the LATERAL join AgencyRepository.findLibraryDirectory adds, which reaches
-- library from agency once per agency row on an anonymous endpoint. See
-- developer:branding.adoc, "Migration choices".
create index if not exists idx_library_agency_id on library (agency_id);

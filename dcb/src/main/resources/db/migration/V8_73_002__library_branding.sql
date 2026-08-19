-- Patron-facing brand for a library (N-1.3), the second level of the brand chain.
-- Additive and nullable: rewrites no rows.
-- Why a library has a mark but no background: docs/branding.md, "Migration choices".

alter table library add brand_logo_url varchar(400);
alter table library add brand_logo_alt varchar(255);
alter table library add default_theme_name varchar(64);

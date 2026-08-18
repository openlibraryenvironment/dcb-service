
-- DCB needs to support consortial branding so that consortia can customise DCB apps accordingly
-- This rewrites no rows, and every consumer already tolerates the fields being absent.
-- The intention is to make sure we support consortial branding now we can't rely on Vercel blob

alter table consortium add brand_logo_url varchar(400);
alter table consortium add brand_logo_alt varchar(255);

-- This is patron-facing copy under the search box in a discovery system
alter table consortium add patron_welcome varchar(500);

-- Validated on write against the theme registry, tolerated on read: an unrecognised
-- name must fall back to the default
alter table consortium add default_theme_name varchar(64);

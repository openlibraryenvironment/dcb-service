
-- Now we have DCB Admin for Libraries, libraries may also want to customise their own DCB apps
-- It is important that we can ship a custom OpenRS experience without relying on vercel blob

alter table library add brand_logo_url varchar(400);
alter table library add brand_logo_alt varchar(255);
alter table library add default_theme_name varchar(64);

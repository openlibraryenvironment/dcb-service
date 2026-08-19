-- Patron-facing brand for the consortium (N-1.3, R-17d).
-- Additive and nullable throughout: rewrites no rows.
-- Design notes and open questions: docs/branding.md, "Migration choices".

alter table consortium add brand_logo_url varchar(400);
alter table consortium add brand_logo_alt varchar(255);
alter table consortium add patron_welcome varchar(500);
alter table consortium add default_theme_name varchar(64);

-- A SQUARE mark, not a smaller logo: a logo is a lockup, and a lockup in a 32px box is an
-- illegible smear. Hence its own column rather than a rendering hint.
alter table consortium add brand_header_icon_url varchar(400);

-- Consortium level only. There is deliberately no library equivalent.
alter table consortium add brand_background_image_url varchar(400);

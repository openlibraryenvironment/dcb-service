-- R-17d — the two brand images that are not the logo.
--
-- brand_logo_url already exists (V8_73_001). These are different assets, not sizes of
-- the same one:
--
--   brand_header_icon_url    a SQUARE mark, for the app bar and the favicon. The logo is
--                            a lockup that needs horizontal room; putting it in a 32px
--                            box produces a squashed lockup, which is why this is its own
--                            column rather than a rendering hint on the logo.
--
--   brand_background_image_url  the canvas behind the landing hero. Consortium level
--                            ONLY, deliberately — there is no library equivalent. A mark
--                            identifies an organisation and belongs at every level of the
--                            brand chain; a canvas does not. A per-library background
--                            would repaint the whole page every time a patron changed
--                            scope, which is motion rather than identity.
--
-- Both accept an absolute http(s) URL (a consortium's own CDN) or a path under this
-- service's own asset prefix (an upload). BrandingValidator learns that one new form and
-- keeps every existing rejection: data:, javascript:, protocol-relative, and any other
-- site-relative path.
--
-- 400 characters to match brand_logo_url. Long enough for a signed CDN URL and short
-- enough that the column is not a place to put something else.
--
-- Additive and nullable: dcb-service is in production, this rewrites no rows, and every
-- consumer already tolerates the fields being absent.

alter table consortium add brand_header_icon_url varchar(400);
alter table consortium add brand_background_image_url varchar(400);

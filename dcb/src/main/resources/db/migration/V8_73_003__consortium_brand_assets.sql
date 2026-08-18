-- Header icon and "hero" image for consortial branding
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

alter table consortium add brand_header_icon_url varchar(400);
alter table consortium add brand_background_image_url varchar(400);

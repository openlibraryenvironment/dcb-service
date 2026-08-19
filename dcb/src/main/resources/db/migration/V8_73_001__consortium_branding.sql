-- N-1.3 / R-17d — the consortium level of the patron-facing brand chain.
--
-- DCB is the system of record for BOTH brand levels. The static branding file the
-- discovery app reads is a generated cache and an offline fallback, never a hand-authored
-- source: a second copy of the consortium's name in a ConfigMap diverges from this one,
-- and the first person to notice is a buyer reading a screenshot.
--
-- Additive and nullable throughout: dcb-service is in production, this rewrites no rows,
-- and every consumer already tolerates the fields being absent.
--
-- WHY THESE ARE NOT header_image_url AND about_image_url, WHICH ALREADY EXIST.
--
-- Not because of size. header_image_url renders at 36x36 in DCB Admin's app bar and
-- symposia-ui renders its square mark at 28-32px in the same slot; that difference is CSS,
-- not a different asset, and a consortium wanting its mark to differ between its own apps
-- is not a real requirement. An earlier version of this comment claimed otherwise and was
-- wrong.
--
-- The actual reason is VALIDATION AND AUDIENCE. header_image_url is written with no
-- validation at all - UpdateConsortiumDataFetcher sets it straight from the input - and
-- that is survivable only because it is rendered in DCB Admin, behind authentication, to
-- staff. The brand_* columns below are served ANONYMOUSLY to patrons by
-- DiscoveryConsortiumController, so every one of them passes through BrandingValidator,
-- which refuses javascript:, data:, protocol-relative //host/x and any site-relative path
-- that is not a real asset key. Pointing the patron app at an unvalidated, staff-writable
-- column would make it the src of an <img> on an anonymous page.
--
-- Collapsing the two families is the right end state and is worth doing: validate
-- header_image_url and about_image_url the same way, then merge. That is a behaviour
-- change on live production columns - a consortium holding a value the validator would now
-- reject finds its next save failing - so it needs its own change with a check of what is
-- actually stored, not a fold into this one.

alter table consortium add brand_logo_url varchar(400);
alter table consortium add brand_logo_alt varchar(255);

-- NOT `description`. That is prose about the consortium, written for staff and shown in
-- dcb-admin-ui. This is patron-facing copy under the search box.
alter table consortium add patron_welcome varchar(500);

-- No colour column. An operator-typed hex can fail WCAG contrast and nothing would catch
-- it; default_theme_name names a theme from a registry whose every brand x mode pairing is
-- measured by a failing test. Choosing from a tested list removes the failure mode rather
-- than mitigating it. Validated on write against the registry and TOLERATED ON READ: a
-- name this deployment no longer ships falls back to the default rather than white-screening
-- the patron app.
alter table consortium add default_theme_name varchar(64);

-- A SQUARE mark, for the app bar and the favicon. Its own column rather than a rendering
-- hint on brand_logo_url because a logo is a lockup - a mark and a wordmark set side by
-- side - and a lockup dropped into a 32px square is an illegible smear. That distinction is
-- about SHAPE and survives; the one about pixel size did not.
alter table consortium add brand_header_icon_url varchar(400);

-- The canvas behind the landing hero. Consortium level ONLY, deliberately - there is no
-- library equivalent. A mark identifies an organisation and belongs at every level of the
-- brand chain; a canvas does not. A per-library background would repaint the whole page
-- every time a patron changed scope, which is motion rather than identity.
alter table consortium add brand_background_image_url varchar(400);

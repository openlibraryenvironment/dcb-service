-- N-1.3 — the consortium level of the patron-facing brand chain.
--
-- DCB is the system of record for BOTH brand levels. The static branding file the
-- discovery app reads is a generated cache and an offline fallback, never a
-- hand-authored source: a second copy of the consortium's name in a ConfigMap
-- diverges from this one, and the first person to notice is a buyer reading a
-- screenshot.
--
-- header_image_url (36x36) and about_image_url (48x48) already exist and are NOT
-- reused. They are admin-chrome icons sized for dcb-admin-ui; a patron-facing mark
-- is a different asset at a different size, and sharing the column would mean an
-- administrator could not have both.
--
-- No colour column. An operator-typed hex can fail WCAG contrast and nothing would
-- catch it; default_theme_name names a theme from a registry whose every brand x mode
-- pairing is measured by a failing test. Choosing from a tested list removes the
-- failure mode rather than mitigating it.
--
-- Additive and nullable throughout: dcb-service is in production, this rewrites no
-- rows, and every consumer already tolerates the fields being absent.

alter table consortium add brand_logo_url varchar(400);
alter table consortium add brand_logo_alt varchar(255);

-- NOT `description`. That is prose about the consortium, written for staff and shown
-- in dcb-admin-ui. This is patron-facing copy under the search box.
alter table consortium add patron_welcome varchar(500);

-- Validated on write against the theme registry, tolerated on read: an unrecognised
-- name must fall back to the default brand, never white-screen the patron app.
alter table consortium add default_theme_name varchar(64);

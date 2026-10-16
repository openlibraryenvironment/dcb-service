-- The support desk a patron reaches when discovery itself is wrong: a broken record, a
-- result that will not request, a card that will not sign in. Distinct from the website
-- (library.patron_website, consortium.website_url), which answers "opening hours and
-- joining" and is the wrong destination for "your search is broken".
--
-- Nullable and additive at both levels, with no backfill and no index: read only by the
-- directory query, which already selects the row.
alter table library    add support_url varchar(200);
alter table consortium add support_url varchar(200);

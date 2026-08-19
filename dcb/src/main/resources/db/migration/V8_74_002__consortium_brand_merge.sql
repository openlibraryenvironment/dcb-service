-- Merge the admin-chrome image columns into the patron-facing brand columns, and stop
-- storing a member of staff's name and email address on the consortium row.
-- Rationale and the data-loss decision: docs/branding.md, "Migration choices".

-- Preserved BEFORE the columns go, so the provenance survives in the place provenance
-- belongs: a role-checked audit record rather than a column any authenticated principal
-- can read. Only for rows that actually hold something.
insert into data_change_log (
	id, entity_id, entity_type, action_info, last_edited_by,
	timestamp_logged, reason, change_category, changes)
select
	gen_random_uuid(),
	c.id,
	'consortium',
	'UPDATE',
	'DCB migration V8_74_002',
	current_timestamp,
	'Preserve brand image provenance before the uploader columns are dropped',
	'Schema migration',
	jsonb_build_object(
		'new_values', '{}'::jsonb,
		'old_values', jsonb_strip_nulls(jsonb_build_object(
			'header_image_url',            c.header_image_url,
			'header_image_uploader',       c.header_image_uploader,
			'header_image_uploader_email', c.header_image_uploader_email,
			'about_image_url',             c.about_image_url,
			'about_image_uploader',        c.about_image_uploader,
			'about_image_uploader_email',  c.about_image_uploader_email)))
from consortium c
where coalesce(c.header_image_url, c.header_image_uploader, c.header_image_uploader_email,
	c.about_image_url, c.about_image_uploader, c.about_image_uploader_email) is not null;

-- header is the square mark every app puts in its header; about is the larger mark shown
-- with more room. coalesce so a brand value already set wins over the admin-chrome one.
-- last_edited_by is set in the same statement so the audit trigger this fires attributes
-- the change to the migration rather than to whoever last edited the consortium by hand.
update consortium
set brand_header_icon_url = coalesce(brand_header_icon_url, header_image_url),
    brand_logo_url        = coalesce(brand_logo_url, about_image_url),
    last_edited_by        = 'DCB migration V8_74_002',
    reason                = 'Merge admin-chrome image columns into the brand columns',
    change_category       = 'Schema migration'
where header_image_url is not null
	or about_image_url is not null;

alter table consortium drop column header_image_url;
alter table consortium drop column header_image_uploader;
alter table consortium drop column header_image_uploader_email;
alter table consortium drop column about_image_url;
alter table consortium drop column about_image_uploader;
alter table consortium drop column about_image_uploader_email;

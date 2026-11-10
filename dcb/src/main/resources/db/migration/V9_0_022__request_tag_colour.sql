-- A theme palette name the admin apps map to a theme token, never a colour value, so a tag
-- follows the theme and keeps its contrast. Null is the app's default. PatronRequestTagService
-- validates on write; this CHECK is the backstop.
ALTER TABLE request_tag ADD COLUMN colour varchar(16);

ALTER TABLE request_tag ADD CONSTRAINT ck_request_tag_colour CHECK (
	colour IS NULL OR colour IN ('default', 'primary', 'secondary', 'info', 'success', 'warning', 'error'));

-- The colours the admin apps showed for the starting tags before a tag carried one
UPDATE request_tag SET colour = CASE code
		WHEN 'DAMAGED' THEN 'error'
		WHEN 'MISSING_PIECES' THEN 'warning'
		WHEN 'PATRON_CONTACTED' THEN 'info'
		WHEN 'RETURN_DISPUTED' THEN 'secondary'
		WHEN 'SLOW_TO_SHIP' THEN 'primary'
	END
WHERE code IN ('DAMAGED', 'MISSING_PIECES', 'PATRON_CONTACTED', 'RETURN_DISPUTED', 'SLOW_TO_SHIP');

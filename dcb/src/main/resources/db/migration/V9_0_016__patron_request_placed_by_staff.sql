-- True when staff placed the request, false when a patron did, null when DCB cannot tell
ALTER TABLE patron_request ADD COLUMN placed_by_staff boolean;

-- Before this column, the staff applications prefixed the note or description with "Staff Request:",
-- and walk-ups and on-site checkouts are expedited. Nothing marked a patron's request, so the rest stay null.
UPDATE patron_request
SET placed_by_staff = true
WHERE is_expedited_checkout = true
	OR requester_note LIKE 'Staff Request:%'
	OR description LIKE 'Staff Request:%';

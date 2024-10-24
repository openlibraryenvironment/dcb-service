UPDATE patron_request
SET previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY'
WHERE previous_status_code = 'NO_ITEMS_AVAILABLE_AT_ANY_AGENCY';

UPDATE patron_request
SET next_expected_status = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY'
WHERE next_expected_status = 'NO_ITEMS_AVAILABLE_AT_ANY_AGENCY'

UPDATE patron_request_audit
SET from_status = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY'
WHERE from_status = 'NO_ITEMS_AVAILABLE_AT_ANY_AGENCY'

UPDATE patron_request_audit
SET to_status = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY'
WHERE to_status = 'NO_ITEMS_AVAILABLE_AT_ANY_AGENCY'

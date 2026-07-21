ALTER TABLE patron_request
    ADD COLUMN outcome_code varchar(32);

UPDATE patron_request pr
SET outcome_code = 'SUPPLIED'
WHERE pr.outcome_code IS NULL
  AND pr.status_code IN ('COMPLETED', 'FINALISED')
  AND EXISTS (
      SELECT 1
      FROM patron_request_audit pra
      WHERE pra.patron_request_id = pr.id
        AND (pra.from_status IN ('LOANED', 'RETURN_TRANSIT')
          OR pra.to_status IN ('LOANED', 'RETURN_TRANSIT'))
  );

UPDATE patron_request pr
SET outcome_code = 'CANCELLED'
WHERE pr.outcome_code IS NULL
  AND (pr.status_code = 'CANCELLED'
    OR pr.previous_status_code = 'CANCELLED'
    OR EXISTS (
        SELECT 1
        FROM patron_request_audit pra
        WHERE pra.patron_request_id = pr.id
          AND pra.to_status = 'CANCELLED'
    ));

UPDATE patron_request pr
SET outcome_code = 'NOT_SUPPLIED'
WHERE pr.outcome_code IS NULL
  AND (pr.status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY'
    OR pr.previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY'
    OR EXISTS (
        SELECT 1
        FROM patron_request_audit pra
        WHERE pra.patron_request_id = pr.id
          AND pra.to_status = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY'
    ));

UPDATE patron_request
SET outcome_code = 'UNKNOWN'
WHERE outcome_code IS NULL
  AND status_code IN ('COMPLETED', 'FINALISED');

UPDATE patron_request
SET resolution_count = 1
WHERE id IN (
    SELECT DISTINCT patron_request_id
    FROM supplier_request
    WHERE patron_request_id IS NOT NULL
);

DO $$
BEGIN
    RAISE NOTICE 'Migrating all mappings where deleted is null to instead have a deleted value of false';
END $$;

UPDATE reference_value_mapping
SET deleted = FALSE
WHERE deleted IS NULL;

UPDATE numeric_range_mapping
SET deleted = FALSE
WHERE deleted IS NULL;

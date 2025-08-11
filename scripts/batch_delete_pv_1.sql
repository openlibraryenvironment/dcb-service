DO $$
DECLARE
    v_rows int;
BEGIN
    LOOP
        -- Delete a batch
        DELETE FROM bib_record
        WHERE id IN (
            SELECT id
            FROM bib_record
            WHERE process_version = 1
            LIMIT 10000
        );

        GET DIAGNOSTICS v_rows = ROW_COUNT;

        COMMIT;  -- Commit this batch

        RAISE NOTICE 'Deleted % rows in this batch', v_rows;

        EXIT WHEN v_rows = 0;

        START TRANSACTION; -- Start the next batch transaction
    END LOOP;
END$$;

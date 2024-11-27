DO $$ 
BEGIN 
    RAISE NOTICE 'Starting role migration process...';
END $$;

CREATE OR REPLACE FUNCTION normalize_role_name(input_role varchar) RETURNS varchar AS $$
BEGIN
    RETURN UPPER(REPLACE(TRIM(input_role), ' ', '_'));
END;
$$ LANGUAGE plpgsql;

DO $$ 
BEGIN 
    RAISE NOTICE 'Creating role table...';
END $$;

CREATE TABLE role (
    id uuid PRIMARY KEY,
    display_name varchar(128) NOT NULL,
    name varchar(128) NOT NULL,
    description varchar(512),
    keycloak_role varchar(128),
    last_edited_by varchar(100),
    reason varchar(100),
    change_reference_url varchar(200),
    change_category varchar(200)
);

-- Insert roles before the migration process
INSERT INTO role (id, name, display_name, description, keycloak_role, reason, change_category, last_edited_by) VALUES
('53f1e7ed-583e-412c-95a9-758400f5cddc', 'SPONSOR', 'Sponsor', 'Sponsorship role', 'Default', 'Roles setup', 'Initial setup', 'system'),
('7e78a211-4b08-4638-9b8d-da90fd66b9db', 'LIBRARY_SERVICES_ADMINISTRATOR', 'Library Services Administrator', 'Manages library services', 'CONSORTIUM_ADMIN', 'Roles setup', 'Initial setup', 'system'),
('7ce45ea4-f399-45c9-b753-f0fcdf11560e', 'SIGN_OFF_AUTHORITY', 'Sign-off Authority', 'Authorized to sign off on actions', 'Default', 'Roles setup', 'Initial setup', 'system'),
('ed95ebf0-aa96-4299-b173-f48ba34b3989', 'SUPPORT', 'Support', 'Provides support services', 'Default', 'Roles setup', 'Initial setup', 'system'),
('c407a5ca-c085-436f-a3d7-d0da91290bf5', 'OPERATIONS_CONTACT', 'Operations Contact', 'Point of contact for operations', 'Default', 'Roles setup', 'Initial setup', 'system'),
('31b7d683-847d-4cc0-b3a5-f0cf10f72026', 'IMPLEMENTATION_CONTACT', 'Implementation Contact', 'Manages implementation', 'Default', 'Roles setup', 'Initial setup', 'system'),
('59522bbc-684c-4d81-a2b8-dce3f273b873', 'TECHNICAL_CONTACT', 'Technical Contact', 'Technical support role', 'Default', 'Roles setup', 'Initial setup', 'system');

DO $$ 
BEGIN 
    RAISE NOTICE 'Creating audit triggers for role table...';
END $$;

CREATE TRIGGER data_change_log_trigger_role_insert_update
    AFTER INSERT OR UPDATE ON role
    FOR EACH ROW
    EXECUTE FUNCTION audit_trigger();

CREATE TRIGGER data_change_log_trigger_role_delete
    AFTER DELETE ON role
    FOR EACH ROW
    EXECUTE FUNCTION audit_trigger();

DO $$ 
BEGIN 
    RAISE NOTICE 'Creating temporary mapping table...';
END $$;

-- Create temporary mapping and check for unmapped roles
CREATE TEMPORARY TABLE role_mapping AS
SELECT DISTINCT
    p.role as old_role_string,
    r.id::uuid as new_role_id  -- Explicitly cast to uuid
FROM person p
JOIN role r ON r.name = normalize_role_name(p.role);

-- Verify all existing roles have mappings
DO $$
DECLARE
    unmapped_roles TEXT;
BEGIN
    SELECT string_agg(DISTINCT role, ', ')
    INTO unmapped_roles
    FROM person p
    WHERE NOT EXISTS (
        SELECT 1 FROM role_mapping rm WHERE rm.old_role_string = p.role
    );
    
    IF unmapped_roles IS NOT NULL THEN
        RAISE EXCEPTION 'Found roles in person table with no mapping: %', unmapped_roles;
    END IF;
END $$;

DO $$ 
BEGIN 
    RAISE NOTICE 'Adding role_id column to person table...';
END $$;

-- Add the new role_id column to person table
ALTER TABLE person ADD COLUMN role_id uuid;

DO $$ 
BEGIN 
    RAISE NOTICE 'Updating person records with new role_id...';
END $$;

-- Update person records with new role_id
UPDATE person p
SET 
    role_id = rm.new_role_id::uuid,  -- Explicitly cast to uuid
    last_edited_by = 'system'
FROM role_mapping rm
WHERE p.role = rm.old_role_string;

DO $$
DECLARE
    unmigrated_count INTEGER;
    total_records INTEGER;
    migrated_records INTEGER;
BEGIN
    -- Count total records needing migration
    SELECT COUNT(*) INTO total_records FROM person WHERE role IS NOT NULL;
    
    -- Count successfully migrated records
    SELECT COUNT(*) INTO migrated_records FROM person WHERE role_id IS NOT NULL;
    
    -- Count unmigrated records
    SELECT COUNT(*) 
    INTO unmigrated_count 
    FROM person 
    WHERE role_id IS NULL AND role IS NOT NULL;
    
    RAISE NOTICE 'Migration status:';
    RAISE NOTICE 'Total records: %', total_records;
    RAISE NOTICE 'Successfully migrated: %', migrated_records;
    RAISE NOTICE 'Failed to migrate: %', unmigrated_count;
    
    IF unmigrated_count > 0 THEN
        RAISE EXCEPTION 'Migration incomplete: % person records have no role_id', unmigrated_count;
    END IF;
END $$;

DO $$ 
BEGIN 
    RAISE NOTICE 'Adding foreign key constraint...';
END $$;

-- Add foreign key constraint and drop old column
ALTER TABLE person 
    ADD CONSTRAINT fk_person_role FOREIGN KEY (role_id) REFERENCES role(id);

DO $$ 
BEGIN 
    RAISE NOTICE 'Dropping old role column...';
END $$;

ALTER TABLE person DROP COLUMN role;

DO $$ 
BEGIN 
    RAISE NOTICE 'Cleaning up temporary tables...';
END $$;

-- Clean up
DROP TABLE role_mapping;

DO $$ 
BEGIN 
    RAISE LOG 'Migration completed successfully!';
END $$;

-- Final verification after commit
DO $$
DECLARE
    person_count INTEGER;
    role_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO person_count FROM person;
    SELECT COUNT(*) INTO role_count FROM role;
    
    RAISE LOG 'Final role status:';
    RAISE LOG 'Total person records: %', person_count;
    RAISE LOG 'Total role records: %', role_count;
    RAISE LOG 'All person records should now have a valid role_id reference';
END $$;

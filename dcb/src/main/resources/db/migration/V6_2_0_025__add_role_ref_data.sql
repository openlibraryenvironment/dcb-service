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
('63c89a76-f81f-51f1-937c-5b3a4130d587', 'SPONSOR', 'Sponsor', 'Sponsorship role', 'Default', 'Roles setup', 'Initial setup', 'system'),
('161c9356-a86f-5eab-811f-039794fa7c6e', 'LIBRARY_SERVICES_ADMINISTRATOR', 'Library Services Administrator', 'Manages library services', 'CONSORTIUM_ADMIN', 'Roles setup', 'Initial setup', 'system'),
('f608afb9-c054-5df0-a042-94ef242e9c51', 'SIGN_OFF_AUTHORITY', 'Sign-off Authority', 'Authorized to sign off on actions', 'Default', 'Roles setup', 'Initial setup', 'system'),
('c04e65f0-f936-5a28-8319-3ae83b1a4630', 'SUPPORT', 'Support', 'Provides support services', 'Default', 'Roles setup', 'Initial setup', 'system'),
('00092fa7-fda3-53d7-ae94-2f88b077d8d5', 'OPERATIONS_CONTACT', 'Operations Contact', 'Point of contact for operations', 'Default', 'Roles setup', 'Initial setup', 'system'),
('d38de66a-1745-5598-a7f7-50b2c6bddd81', 'IMPLEMENTATION_CONTACT', 'Implementation Contact', 'Manages implementation', 'Default', 'Roles setup', 'Initial setup', 'system'),
('24bddbb5-0e33-5c2f-88e3-e4c6f6f7eda2', 'TECHNICAL_CONTACT', 'Technical Contact', 'Technical support role', 'Default', 'Roles setup', 'Initial setup', 'system');

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

DO $$
BEGIN
    RAISE NOTICE 'Adding role_id column to person table...';
END $$;

-- Add the new role_id column to person table
ALTER TABLE person ADD COLUMN role_id uuid;

-- Verify all existing roles have mappings
DO $$
DECLARE
    unmapped_roles TEXT;
    support_role_id UUID;
BEGIN
    -- Get the ID of the 'Support' role
    SELECT id INTO support_role_id
    FROM role
    WHERE name = 'SUPPORT';

    -- Select unmapped roles
    SELECT string_agg(DISTINCT role, ', ')
    INTO unmapped_roles
    FROM person p
    WHERE NOT EXISTS (
        SELECT 1 FROM role r WHERE r.name = normalize_role_name(p.role)
    );

    IF unmapped_roles IS NOT NULL THEN
        RAISE NOTICE 'Found roles in person table with no mapping: %', unmapped_roles;
    END IF;

    -- Update person records with unmapped roles to use 'Support' role as a default
    -- If this is erroneous it can be changed via DCB Admin.
    UPDATE person p
    SET
        role_id = support_role_id,
        last_edited_by = 'system'
    WHERE NOT EXISTS (
        SELECT 1 FROM role r WHERE r.name = normalize_role_name(p.role)
    );
END $$;



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
    total_records INTEGER;
    migrated_records INTEGER;
    unmigrated_count INTEGER;
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
        RAISE NOTICE 'All unmapped roles have been set to Support role';
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

-- Final verification
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

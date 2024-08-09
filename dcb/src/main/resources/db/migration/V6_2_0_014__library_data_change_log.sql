alter table library add last_edited_by varchar(100);
alter table library add reason varchar(100);
alter table library add change_reference_url varchar(200);
alter table library add change_category varchar(200);

alter table location add last_edited_by varchar(100);
alter table location add reason varchar(100);
alter table location add change_reference_url varchar(200);
alter table location add change_category varchar(200);

-- This needed some changes to handle delete properly, which is why it's re-defined here.
CREATE OR REPLACE FUNCTION audit_trigger() RETURNS TRIGGER AS $$
DECLARE
    new_data jsonb;
    old_data jsonb;
    new_values jsonb;
    old_values jsonb;
    changes jsonb;
    key text;
    last_edited_by_value varchar(100);
    reason_value text;
    change_category_value varchar(100);
    change_reference_url_value varchar(200);
BEGIN
    new_values := '{}';
    old_values := '{}';


   IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
        last_edited_by_value := NEW.last_edited_by;
				reason_value := NEW.reason;
				change_category_value := NEW.change_category;
				change_reference_url_value := NEW.change_reference_url;
    ELSIF TG_OP = 'DELETE' THEN
        last_edited_by_value := OLD.last_edited_by;
        reason_value := OLD.reason;
        change_reference_url_value := OLD.change_reference_url;
        change_category_value := OLD.change_category;
    END IF;

    IF TG_OP = 'INSERT' THEN
         changes := to_jsonb(NEW);
         new_data :=to_jsonb(NEW);

 		ELSIF TG_OP = 'UPDATE' THEN
        new_data := to_jsonb(NEW);
        old_data := to_jsonb(OLD);

        FOR key IN SELECT jsonb_object_keys(new_data) INTERSECT SELECT jsonb_object_keys(old_data)
        LOOP
            IF new_data ->> key IS DISTINCT FROM old_data ->> key THEN
                new_values := new_values || jsonb_build_object(key, new_data ->> key);
                old_values := old_values || jsonb_build_object(key, old_data ->> key);
            END IF;
        END LOOP;

        changes := jsonb_build_object('new_values', new_values, 'old_values', old_values);

     ELSIF TG_OP = 'DELETE' THEN
         changes := to_jsonb(OLD);
         new_data :=to_jsonb(OLD);
     END IF;

     -- Ensure changes is a valid JSON object
     IF changes IS NULL OR changes = '{}'::jsonb OR (changes ->> 'new_values' = '{}' AND changes ->> 'old_values' = '{}') THEN
         changes := '{"no_changes": true}'::jsonb;
     END IF;

     -- Log the changes for debugging
  	IF changes <> '{"no_changes": true}'::jsonb THEN
        -- Log the changes for debugging
        RAISE NOTICE 'Audit changes: %', changes;

        BEGIN
         INSERT INTO data_change_log (
             id,
             entity_id,
             entity_type,
             action_info,
             last_edited_by,
             timestamp_logged,
             reason,
             changes,
             change_category,
						 change_reference_url
         ) VALUES (
             gen_random_uuid(),
             CASE
                 WHEN TG_OP = 'DELETE' THEN OLD.id
                 ELSE NEW.id
             END,
             TG_TABLE_NAME,
             TG_OP,
             last_edited_by_value,
             current_timestamp,
             reason_value,
             changes,
             change_category_value,
             change_reference_url_value
         );
     EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Error inserting data change log: %', SQLERRM;
        END;
    END IF;

     IF TG_OP = 'DELETE' THEN
         RETURN OLD;
     ELSE
         RETURN NEW;
     END IF;
 END;
 $$ LANGUAGE plpgsql;


-- Providing the backing for tracking all library entities in the Data Change Log.
-- As well as locations.

DROP TRIGGER IF EXISTS audit_trigger ON agency;
DROP TRIGGER IF EXISTS audit_trigger ON reference_value_mapping;
DROP TRIGGER IF EXISTS data_change_log_trigger_library ON library;

CREATE TRIGGER data_change_log_trigger_insert_update_library
AFTER INSERT OR UPDATE ON library
FOR EACH ROW EXECUTE FUNCTION audit_trigger();

CREATE TRIGGER data_change_log_trigger_delete_library
BEFORE DELETE ON library
FOR EACH ROW EXECUTE FUNCTION audit_trigger();

CREATE TRIGGER data_change_log_trigger_insert_update_agency
AFTER INSERT OR UPDATE ON agency
FOR EACH ROW EXECUTE FUNCTION audit_trigger();

CREATE TRIGGER data_change_log_trigger_delete_agency
BEFORE DELETE ON agency
FOR EACH ROW EXECUTE FUNCTION audit_trigger();

CREATE TRIGGER data_change_log_trigger_insert_update_rvm
AFTER INSERT OR UPDATE ON reference_value_mapping
FOR EACH ROW EXECUTE FUNCTION audit_trigger();

CREATE TRIGGER data_change_log_trigger_delete_rvm
BEFORE DELETE ON reference_value_mapping
FOR EACH ROW EXECUTE FUNCTION audit_trigger();

CREATE TRIGGER data_change_log_trigger_location
AFTER INSERT OR UPDATE ON location
FOR EACH ROW EXECUTE FUNCTION audit_trigger();

CREATE TRIGGER data_change_log_trigger_delete_location
BEFORE DELETE ON location
FOR EACH ROW EXECUTE FUNCTION audit_trigger();

-- Probably only want to catch UPDATES on here.
--CREATE TRIGGER data_change_log_trigger_library_contact
--AFTER INSERT OR UPDATE ON library_contact
--FOR EACH ROW EXECUTE FUNCTION audit_trigger();
--
--CREATE TRIGGER data_change_log_trigger_delete_library_contact
--BEFORE DELETE ON library_contact
--FOR EACH ROW EXECUTE FUNCTION audit_trigger();
--
--CREATE TRIGGER data_change_log_trigger_library_group_member
--AFTER INSERT OR UPDATE ON library_group_member
--FOR EACH ROW EXECUTE FUNCTION audit_trigger();
--
--CREATE TRIGGER data_change_log_trigger_delete_library_group_member
--BEFORE DELETE ON library_group_member
--FOR EACH ROW EXECUTE FUNCTION audit_trigger();
--
--CREATE TRIGGER data_change_log_trigger_library_group
--AFTER INSERT OR UPDATE ON library_group
--FOR EACH ROW EXECUTE FUNCTION audit_trigger();
--
--CREATE TRIGGER data_change_log_trigger_delete_library_group
--BEFORE DELETE ON library_group
--FOR EACH ROW EXECUTE FUNCTION audit_trigger();
--


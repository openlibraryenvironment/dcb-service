CREATE TABLE data_change_log (
    id uuid PRIMARY KEY,
    entity_id uuid,
    last_edited_by varchar(100),
    entity_type varchar(100),
    action_info varchar(50),
    changes jsonb,
    timestamp_logged timestamp,
    reason text,
    old_data jsonb,
    new_data jsonb
);

alter table agency add last_edited_by varchar(100);
alter table reference_value_mapping add last_edited_by varchar(100);
alter table agency add reason varchar(100);
alter table reference_value_mapping add reason varchar(100);

CREATE OR REPLACE FUNCTION audit_trigger() RETURNS TRIGGER AS $$
DECLARE
    new_data jsonb;
    old_data jsonb;
    new_values jsonb;
    old_values jsonb;
    changes jsonb;
    key text;
    last_edited_by_value varchar(100);
    reason_value varchar(100);
BEGIN
    new_values := '{}';
    old_values := '{}';


   IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
        last_edited_by_value := NEW.last_edited_by;
    ELSIF TG_OP = 'DELETE' THEN
        last_edited_by_value := OLD.last_edited_by;
    END IF;

    reason_value := NEW.reason;


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
     IF changes IS NULL OR changes = '{}'::jsonb THEN
         changes := '{"no_changes": true}'::jsonb;
     END IF;

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
             old_data,
             new_data,
             changes
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
             old_data,
             new_data,
             changes
         );
     EXCEPTION WHEN OTHERS THEN
         RAISE NOTICE 'Error inserting data change log: %', SQLERRM;
     END;

     IF TG_OP = 'DELETE' THEN
         RETURN OLD;
     ELSE
         RETURN NEW;
     END IF;
 END;
 $$ LANGUAGE plpgsql;


CREATE TRIGGER data_change_log_trigger
AFTER INSERT OR UPDATE OR DELETE ON agency
FOR EACH ROW EXECUTE FUNCTION audit_trigger();

CREATE TRIGGER data_change_log_trigger
AFTER INSERT OR UPDATE OR DELETE ON reference_value_mapping
FOR EACH ROW EXECUTE FUNCTION audit_trigger();


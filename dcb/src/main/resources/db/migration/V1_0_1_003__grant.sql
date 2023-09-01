CREATE TABLE dcb_grant (
        id uuid PRIMARY KEY,
        grant_resource_owner varchar(128),
        grant_resource_type varchar(128),
        grant_resource_id varchar(128),
        granted_perm varchar(128),
        grantee_type varchar(128),
        grantee varchar(128),
        grant_option boolean
);

CREATE INDEX all_grant_fields ON dcb_grant (grant_resource_owner,grant_resource_type,grant_resource_id,granted_perm,grantee_type,grantee);

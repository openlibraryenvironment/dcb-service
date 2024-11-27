#!/bin/bash

# This is a demonstration of the create/update contact roles feature
# It can be used to either create a new role (provided it has a valid name - see RoleName.java)
# Or to update the information DCB holds about an existing role.
# If you are adding an entirely new role to DCB, you must add its name to RoleName.java first.
# You will then need to edit a contact in DCB to give it the new role
prompt_with_default() {
    local prompt="$1"
    local default="$2"
    local input
    read -p "$prompt [$default]: " input
    echo "${input:-$default}"
}

login_and_get_token() {
	# Source credentials
	source ~/.dcb.sh
	# Get token
	TOKEN=$(curl -s \
		-d "client_id=$KEYCLOAK_CLIENT" \
		-d "client_secret=$KEYCLOAK_SECRET" \
		-d "username=$DCB_ADMIN_USER" \
		-d "password=$DCB_ADMIN_PASS" \
		-d "grant_type=password" \
		"$KEYCLOAK_BASE/protocol/openid-connect/token" | jq -r '.access_token')
	# Check token
 if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
	 echo "Error: Login failed. Unable to retrieve access token. Please check the supplied Keycloak config" >&2
	 exit 1
 fi
	echo "$TOKEN"
}

# Function to confirm yes/no
confirm() {
    local prompt="$1"
    local default="${2:-Y}"
    local input
    read -p "$prompt [$default]: " input
    input=${input:-$default}
    [[ "$input" =~ ^[Yy]$ ]]
}

create_role() {
			local TOKEN="$1"
  		local TARGET="$2"

			ROLE_NAME=$(prompt_with_default "Enter role name. This must be a valid role name as defined in RoleName.java." "e.g. LIBRARY_SERVICES_ADMINISTRATOR")
			ROLE_DISPLAY_NAME=$(prompt_with_default "Enter role display name" "e.g. LIBRARY_SERVICES_ADMINISTRATOR")
#			PERSON_ID=$(prompt_with_default "Enter person UUID" "The UUID of the person this role shou")
			DESCRIPTION=$(prompt_with_default "Enter description" "")
			KEYCLOAK_ROLE=$(prompt_with_default "What Keycloak role should this role have?" "Default")
			REASON=$(prompt_with_default "Enter reason for creating role" "Initial setup")
			CHANGE_CATEGORY=$(prompt_with_default "Enter category for data change log" "Configuration")
			PAYLOAD=$(jq -n \
					--arg name "$ROLE_NAME" \
					--arg displayName "$ROLE_DISPLAY_NAME" \
					--arg description "$DESCRIPTION" \
					--arg keycloakRole "$KEYCLOAK_ROLE" \
					--arg reason "$REASON" \
					--arg changeCategory "$CHANGE_CATEGORY" \
					'{
							query: "mutation { createRole(input: {
									name: \(name),
									description: \"\($description)\",
									displayName: \"\(displayName)\",
									changeCategory: \"\($changeCategory)\",
									reason: \"\($reason)\"
							}) { id, name, description, displayName, description } }"
					}')
		 RESPONSE=$(curl -s \
					-H "Authorization: Bearer $TOKEN" \
					-H "Content-Type: application/json" \
					-X POST "$TARGET/graphql" \
					-d "$PAYLOAD")

			ERRORS=$(echo "$RESPONSE" | jq '.errors')
			if [[ "$ERRORS" != "null" ]]; then
					echo "Role creation failed. Errors:"
					echo "$ERRORS"
					return 1
			else
					echo "Role created successfully!"
					return 0
			fi
}

update_role() {
		local TOKEN="$1"
		local TARGET="$2"
		local UPDATE_PAYLOAD="{}"
		local UPDATES_MADE=0
		# These are the fields that can potentially be updated
		local fields=(
				"displayName"
				"keycloakRole"
				"description"

		)
		ROLE_NAME=$(prompt_with_default "Enter role name" "LIBRARY_SERVICES_ADMINISTRATOR")
	# Dynamic update payload generation
    for field in "${fields[@]}"; do
        value=$(prompt_with_default "Enter new $field (leave blank to skip)" "")
        if [[ -n "$value" ]]; then
            UPDATE_PAYLOAD=$(echo "$UPDATE_PAYLOAD" | jq --arg val "$value" --arg field "$field" '. + {($field): $val}')
            ((UPDATES_MADE++))
        fi
    done
		# For the data change log.
		REASON=$(prompt_with_default "Enter reason for update" "Consortium details update")
		CHANGE_CATEGORY=$(prompt_with_default "Enter change category" "Minor update")
		CHANGE_REFERENCE_URL=$(prompt_with_default "Enter change reference URL (optional)" "")
    UPDATE_PAYLOAD=$(echo "$UPDATE_PAYLOAD" | jq \
        --arg name "$ROLE_NAME" \
        --arg reason "$REASON" \
        --arg changeCategory "$CHANGE_CATEGORY" \
        --arg changeReferenceUrl "$CHANGE_REFERENCE_URL" \
        '. + {
            name: $name,
            reason: $reason,
            changeCategory: $changeCategory,
            changeReferenceUrl: $changeReferenceUrl
        }')

    # Prepare GraphQL mutation
    MUTATION_PAYLOAD=$(jq -n \
        --argjson input "$UPDATE_PAYLOAD" \
        '{
            query: "mutation UpdateRole($input: UpdateRoleInput!) {
                updateRole(input: $input) {
                    id
                    name
                    displayName
                    keycloakRole
                    description
                }
            }",
            variables: {
                input: $input
            }
        }')

    # Send update request
    RESPONSE=$(curl -s \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -X POST "$TARGET/graphql" \
        -d "$MUTATION_PAYLOAD")
    # Check for errors
    ERRORS=$(echo "$RESPONSE" | jq '.errors')
    if [[ "$ERRORS" != "null" ]]; then
        echo "Role update failed. Errors:"
        echo "$ERRORS"
        return 1
    else
        echo "Role updated successfully!"
        return 0
    fi
}


main() {
		echo "This is a script for creating or updating a role in DCB."
		TARGET=$(prompt_with_default "Please enter the URL of the target DCB instance" "http://localhost:8080")
		TOKEN=$(login_and_get_token)
    # Menu for actions
    while true; do
        echo -e "\nChoose an action:"
        echo "1. Create a role"
        echo "2. Update a role"
        echo "3. Exit"
        read -p "Enter your choice (1-3): " CHOICE
        case $CHOICE in
            1)
							 while true; do
													 create_role "$TOKEN" "$TARGET"
													 if ! confirm "Do you want to create another role?"; then
															 break
													 fi
											 done
											 ;;
            2)
                while true; do
                    update_role "$TOKEN" "$TARGET"
                    if ! confirm "Do you want to update another role?"; then
                        break
                    fi
                done
                ;;
            3)
                echo "Exiting..."
                exit 0
                ;;
            *)
                echo "Invalid choice. Please try again."
                ;;
        esac
    done
}
main



#echo Create our standard roles
#curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "$TARGET/graphql" -d '{ "query": "mutation { createRole(input: { name: \"LIBRARY_SERVICES_ADMINISTRATOR\", displayName: \"Library Services Administrator\", keycloakRole: \"LIBRARY_ADMIN\", description: \"Authorised and able to administer DCB services, including update configuration settings and reference data, and suspend services.\"}) { id, name, displayName, keycloakRole } }" }'
#
#echo Update a role
#curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "$TARGET/graphql" -d '{ "query": "mutation { updateRole(input: { name: \"LIBRARY_SERVICES_ADMINISTRATOR\", displayName: \"Library Services Administrator - MEGA MOBIUS\", keycloakRole: \"LIBRARY_BADMIN\", description: \"NOT Authorised and able to administer DCB services, including update configuration settings and reference data, and suspend services.\"}) { id, name, displayName, keycloakRole } }" }'

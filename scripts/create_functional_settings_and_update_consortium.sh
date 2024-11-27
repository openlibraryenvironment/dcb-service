#!/bin/bash
# A script for creating functional settings and updating a consortium.
# The functional settings created by this script will be linked to the consortium provided.
# You will need jq installed to run this.

# Function to prompt user with a default value
prompt_with_default() {
    local prompt="$1"
    local default="$2"
    local input
    read -p "$prompt [$default]: " input
    echo "${input:-$default}"
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

# Function for logging in and getting token with pr
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

# Function to update existing consortium
update_consortium() {
    local TOKEN="$1"
    local TARGET="$2"
    # Prompt for consortium name
    CONSORTIUM_NAME=$(prompt_with_default "Enter consortium name" "")
    if [[ -z "$CONSORTIUM_NAME" ]]; then
        echo "Consortium name is required."
        return 1
    fi
		 QUERY=$(jq -n \
				 --arg name "name:$CONSORTIUM_NAME" \
				 '{
						 query: "query($lq: String) {
								 consortia(query: $lq) {
										 totalSize
										 content {
												 id
												 name
										 }
								 }
						 }",
						 variables: {
								 lq: $name
						 }
				 }')
		 # Execute the query
		 RESPONSE=$(curl -s \
				 -H "Authorization: Bearer $TOKEN" \
				 -H "Content-Type: application/json" \
				 -X POST "$TARGET/graphql" \
				 -d "$QUERY")
		 # Check for errors in the response
		 ERRORS=$(echo "$RESPONSE" | jq '.errors')
		 if [[ "$ERRORS" != "null" ]]; then
				 echo "ERROR" "Error finding consortium: $ERRORS"
				 return 1
		 fi
		 # Extract consortium ID
		 TOTAL_SIZE=$(echo "$RESPONSE" | jq -r '.data.consortia.totalSize')
		 # There should never be multiple consortia, but it's worth checking
		 # Especially on legacy systems.
		 if [[ "$TOTAL_SIZE" -eq 0 ]]; then
				 echo "ERROR" "No consortium found with name: $CONSORTIUM_NAME"
				 return 1
		 elif [[ "$TOTAL_SIZE" -gt 1 ]]; then
				 echo "WARN" "Multiple consortia found with name: $CONSORTIUM_NAME. Using the first result."
		 fi
		CONSORTIUM_ID=$(echo "$RESPONSE" | jq -r '.data.consortia.content[0].id')
		 if [[ -z "$CONSORTIUM_ID" || "$CONSORTIUM_ID" == "null" ]]; then
				 echo "ERROR" "Could not extract consortium ID"
				 return 1
		 fi
    # Prepare update input
    local UPDATE_PAYLOAD="{}"
    local UPDATES_MADE=0
    # These are the fields that can potentially be updated
    local fields=(
        "headerImageUrl"
        "headerImageUploader"
        "headerImageUploaderEmail"
        "aboutImageUrl"
        "aboutImageUploader"
        "aboutImageUploaderEmail"
        "description"
        "catalogueSearchUrl"
        "websiteUrl"
        "displayName"
    )
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
        --arg id "$CONSORTIUM_ID" \
        --arg reason "$REASON" \
        --arg changeCategory "$CHANGE_CATEGORY" \
        --arg changeReferenceUrl "$CHANGE_REFERENCE_URL" \
        '. + {
            id: $id,
            reason: $reason,
            changeCategory: $changeCategory,
            changeReferenceUrl: $changeReferenceUrl
        }')

    # Prepare GraphQL mutation
    MUTATION_PAYLOAD=$(jq -n \
        --argjson input "$UPDATE_PAYLOAD" \
        '{
            query: "mutation UpdateConsortium($input: UpdateConsortiumInput!) {
                updateConsortium(input: $input) {
                    id
                    headerImageUrl
                    headerImageUploader
                    headerImageUploaderEmail
                    aboutImageUrl
                    aboutImageUploader
                    aboutImageUploaderEmail
                    description
                    catalogueSearchUrl
                    websiteUrl
                    displayName
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
        echo "Update Failed. Errors:"
        echo "$ERRORS"
        return 1
    else
        echo "Consortium updated successfully!"
        return 0
    fi
}

# Function to create functional settings
create_functional_setting() {
    local TOKEN="$1"
    local TARGET="$2"

    CONSORTIUM_NAME=$(prompt_with_default "Enter Consortium Name" "e.g. MOBIUS")
    SETTING_NAME=$(prompt_with_default "Enter Functional Setting Name (e.g., PICKUP_ANYWHERE, RE_RESOLUTION)" "")
    DESCRIPTION=$(prompt_with_default "Enter Setting Description" "A functional setting for $SETTING_NAME")
    ENABLED=$(prompt_with_default "Enable this setting?" "true")
    REASON=$(prompt_with_default "Enter Reason for Creating Setting" "Initial setup")
    CHANGE_CATEGORY=$(prompt_with_default "Enter Change Category" "Configuration")
    PAYLOAD=$(jq -n \
        --arg consortiumName "$CONSORTIUM_NAME" \
        --arg settingName "$SETTING_NAME" \
        --arg description "$DESCRIPTION" \
				--argjson enabled "$ENABLED" \
        --arg reason "$REASON" \
        --arg changeCategory "$CHANGE_CATEGORY" \
        '{
            query: "mutation { createFunctionalSetting(input: {
                name: \($settingName),
                consortiumName: \"\($consortiumName)\",
                description: \"\($description)\",
                enabled: \($enabled | @json),
                changeCategory: \"\($changeCategory)\",
                reason: \"\($reason)\"
            }) { id, name, description, enabled } }"
        }')
    RESPONSE=$(curl -s \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -X POST "$TARGET/graphql" \
        -d "$PAYLOAD")

    ERRORS=$(echo "$RESPONSE" | jq '.errors')
    if [[ "$ERRORS" != "null" ]]; then
        echo "Functional Setting Creation Failed. Errors:"
        echo "$ERRORS"
        return 1
    else
        echo "Functional Setting created successfully!"
        return 0
    fi
}

# Main script
main() {
    TARGET=$(prompt_with_default "Enter DCB Target URL" "http://localhost:8080")
    TOKEN=$(login_and_get_token)
    # Menu for actions
    while true; do
        echo -e "\nChoose an action:"
        echo "1. Update consortium"
        echo "2. Create functional settings"
        echo "3. Update Consortium and create functional settings"
        echo "4. Exit"
        read -p "Enter your choice (1-4): " CHOICE
        case $CHOICE in
            1)
                update_consortium "$TOKEN" "$TARGET"
                ;;
            2)
                while true; do
                    create_functional_setting "$TOKEN" "$TARGET"
                    if ! confirm "Do you want to create another functional setting?"; then
                        break
                    fi
                done
                ;;
            3)
                update_consortium "$TOKEN" "$TARGET"
                while true; do
                    create_functional_setting "$TOKEN" "$TARGET"
                    if ! confirm "Do you want to create another functional setting?"; then
                        break
                    fi
                done
                ;;
            4)
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

#!/bin/bash

# This is a script to set up Libraries on a new DCB environment.
# In other words, this script will:
# import libraries from a .tsv file into DCB
# Create a consortium and associated library group from input
# Add all the imported libraries into the consortium group (if you have more than 100 libraries, you will need to change the pagesize variable)
# Due to the one-consortium restriction, this script cannot be used to update an existing consortium.
# If you are wanting to just import libraries, comment out the consortium creation part of this script.

# Function to prompt for input with a default value
prompt_with_default() {
    local prompt="$1"
    local default="$2"
    local input

    read -p "$prompt [$default]: " input
    echo "${input:-$default}"
}

# Prompt for target environment
TARGET=$(prompt_with_default "Enter the target DCB environment URL" "http://localhost:8080")

# Prompt for file path
FILE_PATH=$(prompt_with_default "Enter the path to the TSV file containing your libraries to add" "./scripts/libraries.tsv")

# Prompt for Keycloak information
KEYCLOAK_CLIENT=$(prompt_with_default "Enter Keycloak Client ID" "dcb")
KEYCLOAK_SECRET=$(prompt_with_default "Enter Keycloak client secret" "dcb-keycloak-secret")
KEYCLOAK_BASE=$(prompt_with_default "Enter Keycloak server URL" "dcb-server-url")
DCB_ADMIN_USER=$(prompt_with_default "Enter the username for your Keycloak account" "testadmin")
DCB_ADMIN_PASS=$(prompt_with_default "Enter the password for your Keycloak account" "examplepassword")

# Prompt for consortium details
echo "Configuring Consortium Details:"
CONSORTIUM_NAME=$(prompt_with_default "Enter consortium name" "MOBIUS")
CONSORTIUM_GROUP_NAME=$(prompt_with_default "Enter consortium group name" "MOBIUS_CONSORTIUM")
CONSORTIUM_DISPLAY_NAME=$(prompt_with_default "Enter consortium display Name" "$CONSORTIUM_NAME")
CONSORTIUM_WEBSITE=$(prompt_with_default "Enter consortium website URL" "https://${CONSORTIUM_NAME,,}.org")
CONSORTIUM_CATALOG_URL=$(prompt_with_default "Enter consortium search catalogue URL" "https://search${CONSORTIUM_NAME,,}.org/")
CONSORTIUM_DESCRIPTION=$(prompt_with_default "Enter consortium description" "")
CONSORTIUM_HEADER_IMAGE_URL=$(prompt_with_default "Enter URL for app header image (consortium icon, displayed in the top left)" "")
CONSORTIUM_ABOUT_IMAGE_URL=$(prompt_with_default "Enter URL for 'About' section image (consortium logo, on the login/logout screen)" "")

# Prompt for consortium contact details
echo "Configuring consortium contact:"
CONTACT_FIRST_NAME=$(prompt_with_default "Contact first name" "Jane")
CONTACT_LAST_NAME=$(prompt_with_default "Contact last name" "Doe")
CONTACT_ROLE=$(prompt_with_default "Contact role" "Library Services Administrator")
CONTACT_EMAIL=$(prompt_with_default "Contact email" "jane.doe@${CONSORTIUM_NAME,,}.com")

# Prompt for functional settings
echo "Configuring Functional Settings:"
echo "Enter functional settings (comma-separated, no spaces). Available types:"
echo "- OWN_LIBRARY_BORROWING"
echo "- PICKUP_ANYWHERE"
echo "- RE_RESOLUTION"
echo "- SELECT_UNAVAILABLE_ITEMS"
echo "- TRIGGER_SUPPLIER_RENEWAL"
echo "- DENY_LIBRARY_MAPPING_EDIT"
FUNCTIONAL_SETTINGS=$(prompt_with_default "Functional settings" "OWN_LIBRARY_BORROWING:false,RE_RESOLUTION:false,SELECT_UNAVAILABLE_ITEMS:false,TRIGGER_SUPPLIER_RENEWAL:false,DENY_LIBRARY_MAPPING_EDIT:false")

echo "Logging in"
TOKEN=$(curl -s \
  -d "client_id=$KEYCLOAK_CLIENT" \
  -d "client_secret=$KEYCLOAK_SECRET" \
  -d "username=$DCB_ADMIN_USER" \
  -d "password=$DCB_ADMIN_PASS" \
  -d "grant_type=password" \
  "$KEYCLOAK_BASE/protocol/openid-connect/token" | jq -r '.access_token')
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "Error: Login failed. Unable to retrieve access token. Please check the supplied Keycloak config - you must provide the Keycloak values for your DCB environment" >&2
  exit 1
fi
echo "Logged in successfully with token."
export LN=0
# Roles should already have been created by the migration
# Load data from a local TSV file. Tabs are replaced because of the issue with blank columns outlined here
# https://stackoverflow.com/questions/46997610/read-tsv-file-line-by-line-with-blank-columns
# As not all fields are required, blank columns are a possibility (especially with backupDowntimeSchedule and supportHours).
while IFS=$'\a' read -r AGENCYCODE FULLNAME SHORTNAME ABBREVIATEDNAME ADDRESS LON LAT TYPE BACKUPDOWNTIMESCHEDULE SUPPORTHOURS DISCOVERYSYSTEM PATRONWEBSITE HOSTLMSCONFIGURATION FIRSTNAME LASTNAME ROLE ISPRIMARYCONTACT EMAIL FIRSTNAME2 LASTNAME2 ROLE2 ISPRIMARYCONTACT2 EMAIL2 || [ -n "$AGENCYCODE" ]; do
    if [ $LN -eq 0 ]; then
        echo "Skip header"
    else
        echo "Process data for library $LN"
        # This gets the boolean values in the acceptable lower-case format for JSON
				ISPRIMARYCONTACT=$(echo "$ISPRIMARYCONTACT" | awk '{print tolower($0)}')
        ISPRIMARYCONTACT2=$(echo "$ISPRIMARYCONTACT2" | awk '{print tolower($0)}')

        # If you have more than 2 contacts per library, just add the necessary extra variables and number them appropriately.
        # Constructing the JSON payload using jq
        PAYLOAD=$(jq -n \
                        --arg agencyCode "$AGENCYCODE" \
                        --arg fullName "$FULLNAME" \
                        --arg shortName "$SHORTNAME" \
                        --arg abbreviatedName "$ABBREVIATEDNAME" \
                        --arg address "$ADDRESS" \
                        --argjson lon "$LON" \
                        --argjson lat "$LAT" \
                        --arg type "$TYPE" \
                        --arg backupDowntimeSchedule "$BACKUPDOWNTIMESCHEDULE" \
                        --arg supportHours "$SUPPORTHOURS" \
                        --arg discoverySystem "$DISCOVERYSYSTEM" \
                        --arg patronWebsite "$PATRONWEBSITE" \
                        --arg hostLmsConfiguration "$HOSTLMSCONFIGURATION" \
                        --arg firstName "$FIRSTNAME" \
                        --arg lastName "$LASTNAME" \
                        --arg role "$ROLE" \
                        --argjson isPrimaryContact "$ISPRIMARYCONTACT" \
                        --arg email "$EMAIL" \
                        --arg firstName2 "$FIRSTNAME2" \
                        --arg lastName2 "$LASTNAME2" \
                        --arg role2 "$ROLE2" \
                        --argjson isPrimaryContact2 "$ISPRIMARYCONTACT2" \
                        --arg email2 "$EMAIL2" \
                        '{
                            query: "mutation { createLibrary(input: { agencyCode: \($agencyCode | @json), fullName: \($fullName | @json), shortName: \($shortName | @json), abbreviatedName: \($abbreviatedName | @json), address: \($address | @json), longitude: \($lon | @json), latitude: \($lat | @json), type: \($type | @json), backupDowntimeSchedule: \($backupDowntimeSchedule | @json), supportHours: \($supportHours | @json), discoverySystem: \($discoverySystem | @json), patronWebsite: \($patronWebsite | @json), hostLmsConfiguration: \($hostLmsConfiguration | @json), contacts: [ { firstName: \($firstName | @json), lastName: \($lastName | @json), role: \($role | @json), isPrimaryContact: \($isPrimaryContact | @json), email: \($email | @json) }, { firstName: \($firstName2 | @json), lastName: \($lastName2 | @json), role: \($role2 | @json), isPrimaryContact: \($isPrimaryContact2 | @json), email: \($email2 | @json) } ]} ) { id, fullName, agencyCode, shortName, abbreviatedName, address, secondHostLms { id, code, name, clientConfig, lmsClientClass }, agency { id, code, name, hostLms { id, code, clientConfig, lmsClientClass } } }}"
                        }')
          # Uncomment the below lines if you are debugging this script.
					# echo "Generated payload:"
					# echo "$PAYLOAD"
				# Send the mutation request to the GraphQL endpoint
				curl -X POST "$TARGET/graphql" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$PAYLOAD"
    fi
    ((LN=LN+1))
done < <(tr '\t' '\a' < "$FILE_PATH")


echo "Create the consortium group" $CONSORTIUM_GROUP_NAME
				PAYLOAD=$(jq -n \
								--arg code "$CONSORTIUM_GROUP_NAME" \
								--arg name "$CONSORTIUM_GROUP_NAME" \
								--arg type "CONSORTIUM" \
								'{
										query: "mutation { createLibraryGroup(input: { code: \($code | @json), name: \($name | @json), type: \"\($type)\" }) { id, name, code, type } }"
								}')
          # Uncomment the below lines if you are debugging this script.
					# echo "Generated payload:"
					# echo "$PAYLOAD"
GROUP_RESPONSE=$(curl -s -X POST "$TARGET/graphql" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$PAYLOAD")
# Then extract the group ID
GROUP_ID=$(echo "$GROUP_RESPONSE" | jq -r '.data.createLibraryGroup.id')
echo "Group Response: $GROUP_RESPONSE"
echo "Group ID: $GROUP_ID"

if [[ -z "$GROUP_ID" || "$GROUP_ID" == "null" ]]; then
  echo "Error: Failed to create consortium group" >&2
  exit 1
fi

# Prepare functional settings JSON
FUNCTIONAL_SETTINGS_JSON=$(echo "$FUNCTIONAL_SETTINGS" | tr ',' '\n' | while IFS=':' read -r NAME ENABLED; do
    echo "{\"name\": \"$NAME\", \"enabled\": $ENABLED, \"description\": \"A setting for $NAME\"}"
done | jq -s '.')

## Debug print the generated JSON - commented out by default
#echo "Generated Functional Settings JSON:"
#echo "$FUNCTIONAL_SETTINGS_JSON"

# Validate JSON
echo "$FUNCTIONAL_SETTINGS_JSON" | jq empty && echo "JSON is valid" || echo "JSON is invalid"

# Create the associated consortium, providing the name of the consortium group you just created.
echo "Creating Consortium with the following details:"
echo "Consortium Name: $CONSORTIUM_NAME"
echo "Group Name: $CONSORTIUM_GROUP_NAME"
echo "Display Name: $CONSORTIUM_DISPLAY_NAME"
echo "Website: $CONSORTIUM_WEBSITE"
echo "Catalog URL: $CONSORTIUM_CATALOG_URL"
echo "Contact: $CONTACT_FIRST_NAME $CONTACT_LAST_NAME ($CONTACT_EMAIL)"
echo "Functional Settings: $FUNCTIONAL_SETTINGS"
CONSORTIUM_PAYLOAD=$(jq -n \
    --arg name "$CONSORTIUM_NAME" \
    --arg groupName "$CONSORTIUM_GROUP_NAME" \
    --arg displayName "$CONSORTIUM_DISPLAY_NAME" \
    --arg dateOfLaunch "2024-05-22" \
    --arg websiteUrl "$CONSORTIUM_WEBSITE" \
    --arg catalogueSearchUrl "$CONSORTIUM_CATALOG_URL" \
    --arg description "$CONSORTIUM_DESCRIPTION" \
    --arg firstName "$CONTACT_FIRST_NAME" \
    --arg lastName "$CONTACT_LAST_NAME" \
    --arg headerImageUrl "$CONSORTIUM_HEADER_IMAGE_URL" \
    --arg aboutImageUrl "$CONSORTIUM_ABOUT_IMAGE_URL" \
    --arg reason "Adding the consortium" \
    --arg changeCategory "Initial setup" \
    --arg role "$CONTACT_ROLE" \
    --arg email "$CONTACT_EMAIL" \
    --argjson functionalSettings "$FUNCTIONAL_SETTINGS_JSON" \
    '{
        query: "mutation CreateConsortium($input: ConsortiumInput!) {
            createConsortium(input: $input) {
                id, name, displayName
            }
        }",
        variables: {
            input: {
                name: $name,
                groupName: $groupName,
                displayName: $displayName,
                websiteUrl: $websiteUrl,
                catalogueSearchUrl: $catalogueSearchUrl,
                dateOfLaunch: $dateOfLaunch,
                description: $description,
                headerImageUrl: $headerImageUrl,
                aboutImageUrl: $aboutImageUrl,
                reason: $reason,
                changeCategory: $changeCategory,
                contacts: [{
                    firstName: $firstName,
                    lastName: $lastName,
                    role: $role,
                    isPrimaryContact: true,
                    email: $email
                }],
                functionalSettings: $functionalSettings
            }
        }
    }')

## Print the exact payload for debugging - uncomment if debugging this script
#echo "Consortium Payload:"
#echo "$CONSORTIUM_PAYLOAD"
# Validate the payload
echo "$CONSORTIUM_PAYLOAD" | jq empty && echo "Payload JSON is valid" || echo "Payload JSON is invalid"

# Send the request and capture full response
CONSORTIUM_RESPONSE=$(curl -v -s \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -X POST "$TARGET/graphql" \
  -d "$CONSORTIUM_PAYLOAD")

## Print full response for debugging
#echo "Full Consortium Creation Response:"
#echo "$CONSORTIUM_RESPONSE"

# Check for GraphQL errors
GRAPHQL_ERRORS=$(echo "$CONSORTIUM_RESPONSE" | jq '.errors')
if [[ "$GRAPHQL_ERRORS" != "null" ]]; then
  echo "Consortium Creation GraphQL Errors:" >&2
  echo "$GRAPHQL_ERRORS" >&2
  exit 1
fi

# Extract consortium details
CONSORTIUM_ID=$(echo "$CONSORTIUM_RESPONSE" | jq -r '.data.createConsortium.id')
if [[ -z "$CONSORTIUM_ID" || "$CONSORTIUM_ID" == "null" ]]; then
  echo "Error: Failed to create consortium" >&2
  exit 1
fi

echo "Consortium created successfully with ID: $CONSORTIUM_ID"


MUTATION_TEMPLATE='mutation { addLibraryToGroup(input: { library: \"%s", libraryGroup: \"%s" }) { id } }'

add_library_to_group() {
    local library_id="$1"
    local group_id="$2"

    local mutation=$(printf "$MUTATION_TEMPLATE" "$library_id" "$group_id")
    local payload=$(jq --null-input --arg query "$mutation" '{"query": $query}')

    curl -s -H "Authorization: Bearer $TOKEN" \
         -H "Content-Type: application/json" \
         -X POST "$TARGET/graphql" \
         -d "$payload"
}

# This will get the first 100 libraries and add them to your consortium group. If you want more, just change pageSize in the query below.
# And if you want to choose the libraries you want adding to the consortium, please use DCB Admin and go to 'Add Libraries to Group' on the libraries page.

QUERY='{ "query": "query($lq: String) { libraries(query: $lq, pagesize:100) { totalSize, pageable { number, offset }, content { id } } }",
   "variables": {
     "lq" : "agencyCode:*"
   }}'

RESPONSE=$(curl -s -X POST "$TARGET/graphql" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$QUERY")

# Array to store library IDs
LIBRARY_IDS=()

# Extract library IDs from the response so we can supply them to add_library_to_group
TOTAL_SIZE=$(echo "$RESPONSE" | jq -r '.data.libraries.totalSize')
echo $TOTAL_SIZE;

if [ $TOTAL_SIZE -gt 0 ]; then
    CONTENT=$(echo "$RESPONSE" | jq -r '.data.libraries.content[] | .id')
    while read -r ID; do
        LIBRARY_IDS+=("$ID")
    done <<< "$CONTENT"
fi

# Iterate over each library ID and add it to the group
for id in "${LIBRARY_IDS[@]}"; do
    echo "Adding library $id to group..."
    add_library_to_group "$id" "$GROUP_ID"
    echo "Library $id added to group successfully."
done

## OPTIONAL : List groups and libraries to verify setup has completed successfully (or just check DCB Admin)
# Commented out by default

#echo List groups
#curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{
#  "query": "query($lq: String) { libraryGroups(query: $lq) { content { id, code, name, consortium { id, name}, type, members { library { shortName } } } } }",
#  "variables": {
#    "lq" : "name:*"
#  }
#}'
#
#echo
#
#echo List libraries
## lq=shorthand for lucene query
#curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{
#  "query": "query($lq: String) { libraries(query: $lq) { totalSize, pageable { number, offset }, content { id, fullName, shortName,abbreviatedName,address, agencyCode, longitude, latitude, membership { libraryGroup {id, code, name} }, contacts { id, firstName, lastName, role, isPrimaryContact, email }, agency { id,code,name,hostLms{id, code, clientConfig, lmsClientClass}  }, secondHostLms { id, code, clientConfig, lmsClientClass } } } }",
#  "variables": {
#    "lq" : "agencyCode:*"
#  }
#}'

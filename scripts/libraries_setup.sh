#!/bin/bash

# This is a script to set up Libraries on a DCB system.
# In other words, this script will:
# import libraries from a .tsv file into DCB
# Create a consortium group and associated library group (currently hardcoded to be MOBIUS)
# Add all the imported libraries into the MOBIUS consortium group (if you have more than 100 libraries, you will need to change the pagesize variable)

# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"
# Change as necessary - you wil need a libraries.tsv file to import.
FILE_PATH="./scripts/libraries.tsv"
echo "Logging in"
source ~/.dcb.sh
TOKEN=$(curl -s \
  -d "client_id=$KEYCLOAK_CLIENT" \
  -d "client_secret=$KEYCLOAK_SECRET" \
  -d "username=$DCB_ADMIN_USER" \
  -d "password=$DCB_ADMIN_PASS" \
  -d "grant_type=password" \
  "$KEYCLOAK_BASE/protocol/openid-connect/token" | jq -r '.access_token')
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "Error: Login failed. Unable to retrieve access token. Please check the supplied Keycloak config" >&2
  exit 1
fi

echo "Logged in successfully with token."

export LN=0

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

echo
echo Create the consortium group
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { createLibraryGroup(input: { code:\"MOBIUS\", name:\"MOBIUS_CONSORTIUM\", type:\"CONSORTIUM\"} ) { id,name,code,type } }" }'
echo

# Create the associated consortium, providing the name of the consortium group you just created.
echo Create a new consortium
echo "Create a new consortium"
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "$TARGET/graphql" -d '{ "query": "mutation { createConsortium(input: { name: \"MOBIUS\", groupName: \"MOBIUS_CONSORTIUM\", displayName: \"MOBIUS_CONSORTIUM\", dateOfLaunch: \"2024-05-22\", isPrimaryConsortium: true, contacts: [ { firstName: \"Jane\", lastName: \"Doe\", role: \"Consortium admin\", isPrimaryContact: true, email: \"jane.doe@mobius.com\" } ],functionalSettings: [ { name: \"Pickup anywhere\", enabled: true, description: \"Pickup anywhere policy\" } ], reason: \"Libraries setup script\", changeCategory: \"Initial setup\" }) { id, name, libraryGroup { id, name }, contacts { id, firstName, lastName }, functionalSettings { id, name } } }" }'
# Add libraries to the consortium group.

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

# This group UUID is for the MOBIUS_CONSORTIUM we created earlier.
# If you're using a different group, this will need updating to reflect that.
GROUP_ID="5ae585b4-993b-5585-8a88-961855a0b253"

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

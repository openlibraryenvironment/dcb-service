#!/bin/bash

# This is a script to set up a consortium on a DCB system. This process involves also establishing a consortium group, and optionally adding functional settings and consortium contacts.
# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"
echo "Logging in"
source ~/.dcb.sh
TOKEN=$(curl -s \
  -d "client_id=$KEYCLOAK_CLIENT" \
  -d "client_secret=$KEYCLOAK_SECRET" \
  -d "username=$DCB_ADMIN_USER" \
  -d "password=$DCB_ADMIN_PASS" \
  -d "grant_type=password" \
  "$KEYCLOAK_BASE/protocol/openid-connect/token" | jq -r '.access_token')

# Check if the token was retrieved successfully
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "Error: Login failed. Unable to retrieve access token. Please check the supplied Keycloak config" >&2
  exit 1
fi

echo "Logged in successfully with token."
echo
echo Create the consortium group
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { createLibraryGroup(input: { code:\"MOBIUS\", name:\"MOBIUS_CONSORTIUM\", type:\"CONSORTIUM\"} ) { id,name,code,type } }" }'
echo

# Create the associated consortium, providing the name of the consortium group you just created.
# Copy this across to libraries_setup.sh as part of the work
echo "Create a new consortium"
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "$TARGET/graphql" -d '{ "query": "mutation { createConsortium(input: { name: \"MOBIUS\", groupName: \"MOBIUS_CONSORTIUM\", displayName: \"MOBIUS_CONSORTIUM\", dateOfLaunch: \"2024-05-22\", isPrimaryConsortium: true, contacts: [ { firstName: \"Jane\", lastName: \"Doe\", role: \"Consortium admin\", isPrimaryContact: true, email: \"jane.doe@mobius.com\" } ],functionalSettings: [ { name: \"Pickup anywhere\", enabled: true, description: \"Pickup anywhere policy\" } ], reason: \"Libraries setup script\", changeCategory: \"Initial setup\" }) { id, name, libraryGroup { id, name }, contacts { id, firstName, lastName }, functionalSettings { id, name } } }" }'


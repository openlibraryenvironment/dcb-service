#!/bin/bash
#TARGET="https://dcb-service.sph-aws.k-int.com"
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
# Supply your ID as relevant. You can grab this from the request for consortia in DCB Admin.
echo
echo Delete the consortium
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { deleteConsortium(input: { id:\"07badcb5-8e8e-5d27-ba87-e4eeb42e0c01\"} ) { success, message } }" }'
echo

#!/bin/bash

# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"


echo Libraries feature test
echo Logging in
source ~/.dcb.sh
TOKEN=`curl -s \
  -d "client_id=$KEYCLOAK_CLIENT" \
  -d "client_secret=$KEYCLOAK_SECRET" \
  -d "username=$DCB_ADMIN_USER" \
  -d "password=$DCB_ADMIN_PASS" \
  -d "grant_type=password" \
  "$KEYCLOAK_BASE/protocol/openid-connect/token" | jq -r '.access_token'`
echo confirm

echo List libraries
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{
  "query": "query($lq: String) { libraries(query: $lq) { totalSize, pageable { number, offset }, content { id, membership { libraryGroup { code, name, id} }, fullName, shortName,abbreviatedName,address,supportHours,backupDowntimeSchedule, hostLmsConfiguration, type, training, patronWebsite, discoverySystem, agencyCode, longitude, latitude, contacts { id, firstName, lastName, role, isPrimaryContact, email }, agency { id,code,name,hostLms{id, code, clientConfig, lmsClientClass}  }, secondHostLms { id, code, clientConfig, lmsClientClass } } } }",
  "variables": {
    "lq" : "agencyCode:*"
  }
}'
echo


echo List groups

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{
  "query": "query($lq: String) { libraryGroups(query: $lq) { content { id, code, name, type, consortium { name },members { library { shortName } } } } }",
  "variables": {
    "lq" : "name:*"
  }
}'


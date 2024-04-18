#!/bin/bash

# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"
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
curl $TARGET/health
curl $TARGET/info

echo Create new library
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ "query": "mutation { createLibrary(input: { agencyCode:\"6davn\", fullName:\"Davenport Test Library\", shortName:\"DAVN\", abbreviatedName:\"DVN\", address:\"Test Davenport Street\", longitude: -92.5892101168437, latitude: 40.1934344622184, type: \"Academic\" training: true, contacts: [ { id: \"7b7a3a2b-f2fc-4f0e-b1d9-14d98c62aa9b\", firstName: \"John\", lastName: \"Doe\", role: \"Librarian\", isPrimaryContact: false }, { id: \"7b7a3a2b-f2fc-4f0e-b1d9-14d98c62aa9c\", firstName: \"Jane\", lastName: \"Smith\", role: \"Chief Librarian\", isPrimaryContact: true } ]} ) { id,fullName,agencyCode,shortName,abbreviatedName,address,secondHostLms {id,code,name,clientConfig, lmsClientClass},agency { id,code,name,hostLms{id, code, clientConfig, lmsClientClass} }}}" }'
echo Creation complete

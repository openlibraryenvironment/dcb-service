#!/bin/bash

#TARGET="https://dcb-service.sph-aws.k-int.com"
# Example queries for editing entities via GraphQL.
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

echo Edit location
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "http://localhost:8080/graphql" -d '{ "query": "mutation { updateLocation(input: { id:\"1cebb615-55ea-5177-b8cf-a0facd24f34f\", type:\"Edited2\", reason:\"Edited\", changeCategory:\"Edited\", changeReferenceUrl:\"Edited\"} ) { id, name, type} }" }'
echo Edit complete

echo Edit library
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "http://localhost:8080/graphql" -d '{ "query": "mutation { updateLibrary(input: { id:\"f5c0a59b-64bf-5279-87d4-5647fec158a0\", supportHours:\"Edited2\", fullName:\"Edited\", shortName: \"Edited\", abbreviatedName:\"EDIT\",reason:\"Edited\", changeCategory:\"Edited\", changeReferenceUrl:\"Edited\"} ) { id, shortName, type} }" }'
echo Edit complete


echo Edit person
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "http://localhost:8080/graphql" -d '{ "query": "mutation { updatePerson(input: { id:\"079143fb-01b6-5017-8dfe-86fb56a024f4\", firstName:\"Edited2\", reason:\"Edited\", changeCategory:\"Edited\", changeReferenceUrl:\"Edited\"} ) { id, firstName, lastName} }" }'
echo Edit complete

#!/bin/bash

#TARGET="https://dcb-dev.sph.k-int.com"
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
curl $TARGET/health
curl $TARGET/info

# Pre-requisites: Make sure that DCB has the necessary Host LMS and agencies for the libraries you want to create.
# At a minimum it will need an agency of the library's 'agencyCode' and whichever Host LMS are associated with it.

echo

echo Create a new library
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { createLibrary(input: { agencyCode:\"6davn\", fullName:\"Davenport Public Library\", shortName:\"Davenport\", abbreviatedName:\"DAVN\", address:\"321 Main Street, Davenport, IA 52801\", longitude: -90.5750057702084, latitude: 41.5235178164979, type: \"Public\" training: true, ,backupDowntimeSchedule: \"9-5\", supportHours: \"Weekdays: 9-5\", discoverySystem: \"Locate\", patronWebsite: \"https://example.com\", hostLmsConfiguration: \"Standalone\",  contacts: [ { firstName: \"John\", lastName: \"Doe\", role: \"Librarian\", isPrimaryContact: false, email: \"john.doe@test1library.com\"  }, { firstName: \"Jane\", lastName: \"Smith\", role: \"Chief Librarian\", isPrimaryContact: true, email: \"jane.smith@test1ibrary.com\" } ]} ) { id,fullName,agencyCode,shortName,abbreviatedName,address,secondHostLms {id,code,name,clientConfig, lmsClientClass},agency { id,code,name,hostLms{id, code, clientConfig, lmsClientClass} }}}" }'

echo
echo Create new group
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { createLibraryGroup(input: { code:\"MOBIUS\", name:\"MOBIUS_CONSORTIUM\", type:\"CONSORTIUM\"} ) { id,name,code,type } }" }'
echo


echo Add library to group
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { addLibraryToGroup(input: { library:\"0b29bb3a-abf0-544b-9fd5-be9fef8952f7\", libraryGroup:\"5ae585b4-993b-5585-8a88-961855a0b253\" } ) { id } }" }'

echo

echo Create a new consortium
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "$TARGET/graphql" -d '{ "query": "mutation { createConsortium(input: { name: \"MOBIUS\", groupName: \"MOBIUS_CONSORTIUM\" }) { id, name, libraryGroup { id, name } } }" }'

echo List groups
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{
  "query": "query($lq: String) { libraryGroups(query: $lq) { content { id, code, name, consortium { id, name}, type, members { library { shortName } } } } }",
  "variables": {
    "lq" : "name:*"
  }
}'

echo

echo List libraries
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{
  "query": "query($lq: String) { libraries(query: $lq) { totalSize, pageable { number, offset }, content { id, fullName, shortName,abbreviatedName,address, agencyCode, longitude, latitude, membership { libraryGroup {id, code, name} }, contacts { id, firstName, lastName, role, isPrimaryContact, email }, agency { id,code,name,hostLms{id, code, clientConfig, lmsClientClass}  }, secondHostLms { id, code, clientConfig, lmsClientClass } } } }",
  "variables": {
    "lq" : "agencyCode:*"
  }
}'
echo
echo Create a second library
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { createLibrary(input: { agencyCode:\"6slou\", fullName:\"A different Test Library\", shortName:\"DUMMYLIBRARY\", abbreviatedName:\"DUMMYLIBRARY\", address:\"Test Louis Street\", longitude: -92.5892101168437, latitude: 40.1934344622184, type: \"Consortium\" training: true, backupDowntimeSchedule: \"9-6\", supportHours: \"Weekdays: 9-6\", discoverySystem: \"Discovery\", patronWebsite: \"https://example2.com\", hostLmsConfiguration: \"Not Standalone\",contacts: [ { firstName: \"John\", lastName: \"Doe\", role: \"Librarian\", isPrimaryContact: false, email:\"john.doe@library2.com\"  }, { firstName: \"Jane\", lastName: \"Smith\", role: \"Chief Librarian\", isPrimaryContact: true, email:\"jane.doe@library2.com\" } ]} ) { id,fullName,agencyCode,shortName,abbreviatedName,address,secondHostLms {id,code,name,clientConfig, lmsClientClass},agency { id,code,name,hostLms{id, code, clientConfig, lmsClientClass} }}}" }'

echo
echo Create new group
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { createLibraryGroup(input: { code:\"NMOBIUS\", name:\"NOT_MOBIUS_CONSORTIUM\", type:\"Academic\" } ) { id,name,code,type } }" }'
echo
echo List groups
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{
  "query": "query($lq: String) { libraryGroups(query: $lq) { content { id, code, name, type, members { library { shortName } } } } }",
  "variables": {
    "lq" : "name:*"
  }
}'

echo
echo Add first library to second group
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { addLibraryToGroup(input: { library:\"0b29bb3a-abf0-544b-9fd5-be9fef8952f7\", libraryGroup:\"ffd53b35-3c63-5e94-a5d8-61d27d6733b3\" } ) { id } }" }'


echo
echo Add second library to second group
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { addLibraryToGroup(input: { library:\"eb39bc73-29bf-5f50-8ecb-78128fc39a09\", libraryGroup:\"ffd53b35-3c63-5e94-a5d8-61d27d6733b3\" } ) { id } }" }'
echo


echo List libraries
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{
  "query": "query($lq: String) { libraries(query: $lq) { totalSize, pageable { number, offset }, content { id, fullName, shortName,abbreviatedName,address, agencyCode, longitude, latitude, membership { libraryGroup {id, code, name} }, contacts { id, firstName, lastName }, agency { id,code,name,hostLms{id, code, clientConfig, lmsClientClass}  }, secondHostLms { id, code, clientConfig, lmsClientClass } } } }",
  "variables": {
    "lq" : "agencyCode:*"
  }
}'

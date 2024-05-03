#!/bin/bash

#TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"

echo Libraries intro - shows all available commands and does a very basic initial library setup.

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

# Pre-requisites: Make sure that DCB has or will have the necessary Host LMS and agencies for the libraries you want to create.
# At a minimum it will need an agency of the library's 'agencyCode' and whichever Host LMS are associated with it.
# Before creating a consortia, you will need to have created its associated group, which must be of type "consortium".
# Please refer to guidance here for more information https://openlibraryfoundation.atlassian.net/wiki/x/LABHrQ

echo Create the libraries


# Create your libraries here with this command - repeat as necessary

echo Create a new library
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { createLibrary(input: { agencyCode:\"6davn\", fullName:\"Davenport Public Library\", shortName:\"Davenport\", abbreviatedName:\"DAVN\", address:\"321 Main Street, Davenport, IA 52801\", longitude: -90.5750057702084, latitude: 41.5235178164979, type: \"Public\" training: true, ,backupDowntimeSchedule: \"9-5\", supportHours: \"Weekdays: 9-5\", discoverySystem: \"Locate\", patronWebsite: \"https://example.com\", hostLmsConfiguration: \"Standalone\",  contacts: [ { firstName: \"John\", lastName: \"Doe\", role: \"Librarian\", isPrimaryContact: false, email: \"john.doe@test1library.com\"  }, { firstName: \"Jane\", lastName: \"Smith\", role: \"Chief Librarian\", isPrimaryContact: true, email: \"jane.smith@test1ibrary.com\" } ]} ) { id,fullName,agencyCode,shortName,abbreviatedName,address,secondHostLms {id,code,name,clientConfig, lmsClientClass},agency { id,code,name,hostLms{id, code, clientConfig, lmsClientClass} }}}" }'


# Create the group for your consortium here. You can also create other groups if desired.
echo
echo Create the consortium group
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { createLibraryGroup(input: { code:\"MOBIUS\", name:\"MOBIUS_CONSORTIUM\", type:\"CONSORTIUM\"} ) { id,name,code,type } }" }'
echo

# Create the associated consortium, providing the name of the consortium group you just created.
echo Create a new consortium
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "$TARGET/graphql" -d '{ "query": "mutation { createConsortium(input: { name: \"MOBIUS\", groupName: \"MOBIUS_CONSORTIUM\" }) { id, name, libraryGroup { id, name } } }" }'


# Add all the libraries to your consortium group here
echo Add library to group
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ "query": "mutation { addLibraryToGroup(input: { library:\"0b29bb3a-abf0-544b-9fd5-be9fef8952f7\", libraryGroup:\"5ae585b4-993b-5585-8a88-961855a0b253\" } ) { id } }" }'

echo

# OPTIONAL : List groups and libraries to verify setup has completed successfully (or just check DCB Admin)
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

# Library setup should now be complete.

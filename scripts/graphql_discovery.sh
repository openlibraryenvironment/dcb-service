#!/bin/bash


TOKEN=`./login`

RESHARE_ROOT_UUID=`uuidgen --sha1 -n @dns --name org.olf.dcb`
AGENCIES_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name Agency`
HOSTLMS_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name HostLms`
LOCATION_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name Location`


echo
echo Archway
echo

curl -X POST http://localhost:8080/hostlmss -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $HOSTLMS_NS_UUID --name TEST1`'", 
  "code":"TEST1", 
  "name":"Test1", 
  "lmsClientClass": "org.olf.dcb.core.interaction.sierra.SierraLmsClient", 
  "clientConfig": { 
    "base-url": "https://test1.somedomain",
    "key": "khjsdhkfs",
    "page-size": "100",
    "ingest": "false"
  } 
}'

echo Completed 


echo
echo agencies
echo
curl -X POST http://localhost:8080/agencies -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $AGENCIES_NS_UUID --name testa1`'",    "code":"testa1",      "name":"Test agency 1",           "hostLMSCode": "TEST1" }'

# curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "http://localhost:8080/graphql" -d '{"query":"query IntrospectionQuery { __schema { queryType { name } mutationType { name } subscriptionType { name } types { ...FullType } directives { name description locations args { ...InputValue } } } } fragment FullType on __Type { kind name description fields(includeDeprecated: true) { name description args { ...InputValue } type { ...TypeRef } isDeprecated deprecationReason } inputFields { ...InputValue } interfaces { ...TypeRef } enumValues(includeDeprecated: true) { name description isDeprecated deprecationReason } possibleTypes { ...TypeRef } } fragment InputValue on __InputValue { name description type { ...TypeRef } defaultValue } fragment TypeRef on __Type { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name } } } } } } } } ","operationName":"IntrospectionQuery"}'

echo
echo
echo Create new agency group
echo

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ "query": "mutation { createAgencyGroup(input: { code:\"MOBIUS\", name:\"MOBIUS\" } ) { id,name,code } }" }'

echo
echo
echo List agency groups
echo

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ "query": "query { agencyGroups { id, code, name, members { id }  } }" }'

# curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ "query": "query { hello(name:\"fred\") }" }'

echo
echo List agencies
echo
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ "query": "query { agencies { id, code, name } }" }'

echo
echo Add agency to group
echo
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ "query": "mutation { addAgencyToGroup(input: { agency:\"906237d8-8850-50b4-9883-34cc49167ff1\", group:\"26ccba70-f119-5f7d-968e-3388d0a5cdcf\" } ) { id } }" }'

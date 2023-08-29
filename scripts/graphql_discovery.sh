#!/bin/bash


TOKEN=`./login`


# curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "http://localhost:8080/graphql" -d '{"query":"query IntrospectionQuery { __schema { queryType { name } mutationType { name } subscriptionType { name } types { ...FullType } directives { name description locations args { ...InputValue } } } } fragment FullType on __Type { kind name description fields(includeDeprecated: true) { name description args { ...InputValue } type { ...TypeRef } isDeprecated deprecationReason } inputFields { ...InputValue } interfaces { ...TypeRef } enumValues(includeDeprecated: true) { name description isDeprecated deprecationReason } possibleTypes { ...TypeRef } } fragment InputValue on __InputValue { name description type { ...TypeRef } defaultValue } fragment TypeRef on __Type { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name } } } } } } } } ","operationName":"IntrospectionQuery"}'

echo
echo Create new agency group
echo

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ "query": "mutation { createAgencyGroup(code:\"MOBIUS\", name:\"MOBIUS\" ) { id } }" }'

echo
echo List agency groups
echo

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ "query": "query { agencyGroups { id, code, name } }" }'

# curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ "query": "query { hello(name:\"fred\") }" }'

echo
echo List agencies
echo
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ "query": "query { agencies { id, code, name } }" }'

echo
echo


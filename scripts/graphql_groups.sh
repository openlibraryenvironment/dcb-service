#!/bin/bash


TOKEN=`./login`

echo List agencies
echo
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ 
  "query": "query($lq: String) { agencies(query: $lq) { id, code, name } }",
  "variables": {
    "lq" : "name:T*"
  }
}'
echo

# find no records?
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ 
  "query": "query($lq: String) { agencies(query: $lq) { id, code, name } }",
  "variables": {
    "lq" : "name:NOTHERE"
  }
}'
echo

# find no records?
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ 
  "query": "query { agencyGroups { id, code, name } }"
}'
echo




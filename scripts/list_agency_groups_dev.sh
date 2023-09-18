#!/bin/bash

TARGET="https://dcb-dev.sph.k-int.com"
TOKEN=`./login`
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { agencyGroups(query: $lq) { id, code, name } }",
  "variables": {
    "lq" : "code:a*"
  }
}'

echo
echo
echo

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { agencyGroups(query: $lq) { id, code, name } }",
  "variables": {
    "lq" : "code:G*"
  }
}'

echo
echo
echo

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { agencies(query: $lq) { id, code, name } }",
  "variables": {
    "lq" : "code:D*"
  }
}'

echo
echo
echo

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { hello(name: $lq) }",
  "variables": {
    "lq" : "code:D*"
  }
}'


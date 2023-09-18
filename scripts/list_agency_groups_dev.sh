#!/bin/bash

TARGET="https://dcb-dev.sph.k-int.com"
TOKEN=`./login`
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { agencyGroups(query: $lq) { id, code, name } }",
  "variables": {
    "lq" : "code:a*"
  }
}'

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { agencyGroups(query: $lq) { id, code, name } }",
  "variables": {
    "lq" : "code:a*"
  }
}'

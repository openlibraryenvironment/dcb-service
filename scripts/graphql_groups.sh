#!/bin/bash

# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"

TOKEN=`./login`

echo List agencies
echo
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { agencies(query: $lq) { content { id, code, name } } }",
  "variables": {
    "lq" : "name:*"
  }
}'
echo

# find no records?
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { agencyGroups(query: $lq) { content { id, code, name, members { agency { name } } } } }",
  "variables": {
    "lq" : "name:*"
  }
}'
echo




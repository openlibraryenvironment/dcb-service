#!/bin/bash

# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"

TOKEN=`./login`

echo List hostlms
echo
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { agencies(query: $lq) { totalSize, pageable { number, offset }, content { id, name, code, authProfile, longitude, latitude, hostLms { id } } } }",
  "variables": {
    "lq" : "code:*"
  }
}'
echo

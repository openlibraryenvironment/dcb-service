#!/bin/bash

# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"

TOKEN=`./login`

echo List locations
echo
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { locations(query: $lq) { totalSize, pageable { number, offset }, content { id, code, name, type, longitude, latitude, hostSystem { id }, isPickup,  parentLocation { id }, agency { id } } } }",
  "variables": {
    "lq" : "code:*"
  }
}'
echo

#!/bin/bash

# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"

TOKEN=`./login`

echo List source bibs
echo
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { sourceBibs(query: $lq, pagesize:20) { totalSize, pageable { number, offset }, content { id, title, dateCreated, dateUpdated } } }",
  "variables": {
    "lq" : "title:*"
  }
}'
echo

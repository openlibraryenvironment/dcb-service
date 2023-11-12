#!/bin/bash

# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"

TOKEN=`./login`

echo List PatronRequests
echo
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { patronRequests(query: $lq) { totalSize, pageable { number, offset }, content { id, description, suppliers { id }, audit { id }  }  } }",
  "variables": {
    "lq" : "description:*"
  }
}'
echo

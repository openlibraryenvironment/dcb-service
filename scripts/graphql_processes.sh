#!/bin/bash

# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"

TOKEN=`./login`

echo List hostlms
echo
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "{ processStates { totalSize, pageable { number, offset }, content { id, context, processName, processState } } }"
}' | jq
echo

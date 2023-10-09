#!/bin/bash

TARGET="https://dcb-dev.sph.k-int.com"
# TARGET="http://localhost:8080"

TOKEN=`./login`

echo List clusters
echo
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { instanceClusters(query: $lq) { id, title } }",
  "variables": {
    "lq" : "title:brain"
  }
}'
echo

#!/bin/bash

# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"

TOKEN=`./login`

echo List PatronRequests
echo
# lq=shorthand for lucene query
# Query with a few more fields to help diagnosing local issues - add as necessary
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($lq: String) { patronRequests(query: $lq) { totalSize, pageable { number, offset }, content { id, description, status, dateCreated, dateUpdated, errorMessage, suppliers { id }, clusterRecord {id, title }, audit { id, auditDate, briefDescription }  }} }",
  "variables": {
    "lq" : "description:*"
  }
}'
echo

# Original query
#   "query": "query($lq: String) { patronRequests(query: $lq) { totalSize, pageable { number, offset }, content { id, description, suppliers { id }, audit { id }  }  } }",

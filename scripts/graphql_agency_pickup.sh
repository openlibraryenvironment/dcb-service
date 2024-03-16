#!/bin/bash

# TARGET="https://dcb-dev.sph.k-int.com"
TARGET="http://localhost:8080"

TOKEN=`./login`

echo List locations
echo
# lq=shorthand for lucene query
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/graphql" -d '{ 
  "query": "query($agency: String) { pickupLocations(forAgency: $agency) { id, code, name, type, longitude, latitude, hostSystem { id }, isPickup,  parentLocation { id }, agency { id } } }",
  "variables": {
    "agency" : "6cals"
  }
}'
echo

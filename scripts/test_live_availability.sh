#!/bin/bash

TARGET="http://localhost:8080"
#TARGET="https://dcb-uat.sph.k-int.com"

TOKEN=$(./login)

sampleSize=3

# Fetch cluster IDs from the /clusters endpoint
clusterIds=$(curl -s "$TARGET/clusters?number=$sampleSize" -H "Accept-Language: en" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" | jq -r '.content[].clusterId')

# Check if clusterIds is empty or null
if [ -z "$clusterIds" ] || [ "$clusterIds" == "null" ]; then
  echo "Failed to get clusterIds"
  exit 1
fi

# Iterate through clusterIds and make requests to live availability
for clusterId in $clusterIds; do
  # Use the clusterId in the request
  echo
  echo
  echo
  echo live availability for cluster bib id: $clusterId
  echo
  curl -X GET "$TARGET/items/availability?clusteredBibId=$clusterId" -H "Accept-Language: en" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN"
done

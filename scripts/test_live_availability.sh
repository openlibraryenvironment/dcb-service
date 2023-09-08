#!/bin/bash

TARGET="https://dcb-uat.sph.k-int.com"
TOKEN=`./login`

# First, get a cluster id
clusterId=$(curl -s GET "$TARGET/clusters?number=1&size=10" -H "Accept-Language: en" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" | jq -r '.content[0].clusterId')

# Check if clusterId is empty or null
if [ -z "$clusterId" ] || [ "$clusterId" == "null" ]; then
  echo "Failed to get clusterId"
  exit 1
fi

# Now, use the clusterId in the next request
curl -X GET "$TARGET/items/availability?clusteredBibId=$clusterId" -H "Accept-Language: en" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN"

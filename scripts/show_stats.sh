#!/bin/bash

TARGET="http://localhost:8080"

TOKEN=`./login`

echo show statistics
echo
# curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X GET "$TARGET/admin/statistics"
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X GET "$TARGET/admin/recordCounts" | jq

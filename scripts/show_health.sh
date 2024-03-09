#!/bin/bash

TARGET="http://localhost:8080"

TOKEN=`./login`

echo show health
echo
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X GET "$TARGET/health"

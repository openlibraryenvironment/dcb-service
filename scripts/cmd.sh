#!/bin/bash


TOKEN=`./login`

#  cmd="validateClusters"
cmd="reprocess"
TARGET="http://localhost:8080"

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "$TARGET/admin/$cmd" -d "{}"

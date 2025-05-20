#!/bin/bash

TARGET="http://localhost:8080"
TOKEN=`./login`
HOSTLMS="georgia-institute-of-tech-alma"

echo $TOKEN
echo

curl -s \
  -H "Authorization: Bearer $TOKEN" \
  "$TARGET/imps/interopTest?code=$HOSTLMS&forceCleanup=true" | json_pp

#!/bin/bash

# TARGET="https://dcb-uat.sph.k-int.com"
TARGET="http://localhost:8080"
TOKEN=`./login`
echo
curl -X GET "$TARGET/agencies?type=PICKUP" -H "Accept-Language: en" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN"
echo
curl -X GET "$TARGET/agencies" -H "Accept-Language: en" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN"

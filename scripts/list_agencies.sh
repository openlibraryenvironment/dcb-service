#!/bin/bash

TARGET="https://dcb-uat.sph.k-int.com"
TOKEN=`./login`
curl -X GET $TARGET/agencies -H "Accept-Language: en" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN"

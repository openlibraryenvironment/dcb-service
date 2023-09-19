#!/bin/bash

TARGET="https://dcb-dev.sph.k-int.com"
TOKEN=`./login`
curl -X GET "$TARGET/locations?type=PICKUP" -H "Accept-Language: en" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN"

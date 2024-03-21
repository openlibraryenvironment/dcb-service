#!/bin/bash

TARGET="http://localhost:8080"

RESHARE_ROOT_UUID=`uuidgen --sha1 -n @dns --name org.olf.dcb`
AGENCIES_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name Agency`
HOSTLMS_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name HostLms`
LOCATION_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name Location`

echo Logging in
TOKEN=`../login`

sleep 1
echo
echo "Patron auth: "
auth_request_data='{"agencyCode": "DA-3-1", "patronPrinciple": "1234", "secret": "1234"}'
curl -s -X POST "$TARGET/patron/auth" -H "Accept-Language: en" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d "${auth_request_data}" | json_pp


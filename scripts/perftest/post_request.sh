#!/bin/bash


OPENRS_ROOT_UUID=`uuidgen --sha1 -n @dns --name org.olf.dcb`

TARGET="http://localhost:8080"
echo Logging in
TOKEN=`../login`

ES_CONTAINER=`docker container ls --format "table {{.ID}} {{.Names}} {{.Image}} {{.Ports}}" | grep elastic | cut -f1 -d' '`
ES_PORT=`docker port $ES_CONTAINER | grep 9200 | grep 0.0.0.0 | cut -f2 -d:`

FIRST_RECORD_ID=`curl -s "localhost:$ES_PORT/mobius-si/_search?q=*" | jq -r '.hits.hits[0]._id'`
PICKUP_LOCATION=`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name PU-DA-1-1-KI`

JSON_PAYLOAD=`printf '{ "citation":{"bibClusterId":"%s"},"requestor":{"localSystemCode":"DUMMY1","localId":"1380112","homeLibraryCode":"DA-1-1"},"pickupLocation":{"code":"%s"},"description":"A test request", "requesterNote":"TESTCASE000A"}' "$FIRST_RECORD_ID" "$PICKUP_LOCATION"`

curl -v -X POST $TARGET/patrons/requests/place -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d "$JSON_PAYLOAD"

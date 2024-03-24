#!/bin/bash

TARGET="http://localhost:8080"
echo Logging in
TOKEN=`../login`

ES_CONTAINER=`docker container ls --format "table {{.ID}} {{.Names}} {{.Image}} {{.Ports}}" | grep elastic | cut -f1 -d' '`
ES_PORT=`docker port $ES_CONTAINER | grep 9200 | grep 0.0.0.0 | cut -f2 -d:`

FIRST_RECORD_ID=`curl -s "localhost:$ES_PORT/mobius-si/_search?q=*" | jq -r '.hits.hits[0]._id'`

JSON_PAYLOAD=`printf '{ "citation":{"bibClusterId":"%s"},"requestor":{"localSystemCode":"DUMMY3","localId":"1380112","homeLibraryCode":"DA-1-1"},"pickupLocation":{"code":"6c669866-a2a0-54aa-8cea-4e9437dec30c"},"description":"Ian testing SLCL Test Item"}' "$FIRST_RECORD_ID"`

curl -v -X POST $TARGET/patrons/requests/place -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d "$JSON_PAYLOAD"

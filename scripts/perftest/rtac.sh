#!/bin/bash

TARGET="http://localhost:8080"

echo Logging in
TOKEN=`../login`



ES_CONTAINER=`docker container ls --format "table {{.ID}} {{.Names}} {{.Image}} {{.Ports}}" | grep elastic | cut -f1 -d' '`
ES_PORT=`docker port $ES_CONTAINER | grep 9200 | grep 0.0.0.0 | cut -f2 -d:`

echo indices
curl "localhost:$ES_PORT/_cat/indices"

echo search
FIRST_RECORD_ID=`curl -s "localhost:$ES_PORT/mobius-si/_search?q=*" | jq -r '.hits.hits[0]._id'`

echo first record id $FIRST_RECORD_ID


curl -X GET -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" "$TARGET/items/availability?clusteredBibId=$FIRST_RECORD_ID" | jq

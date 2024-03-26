#!/bin/bash

ES_CONTAINER=`docker container ls --format "table {{.ID}} {{.Names}} {{.Image}} {{.Ports}}" | grep elastic | cut -f1 -d' '`
ES_PORT=`docker port $ES_CONTAINER | grep 9200 | grep 0.0.0.0 | cut -f2 -d:`

echo indices
curl "localhost:$ES_PORT/_cat/indices"

echo search
curl "localhost:$ES_PORT/mobius-si/_search?q=*" | jq

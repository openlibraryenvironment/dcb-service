#!/bin/bash

PORT=32781

echo indices
curl --user elastic:elastic "localhost:$PORT/_cat/indices"

echo search
curl --user elastic:test "localhost:$PORT/mobius-si/_search?q=*"


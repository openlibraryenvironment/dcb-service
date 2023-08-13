#!/bin/bash


TOKEN=`./login`

curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"  -X POST "http://localhost:8080/graphql" -d '{ "query": "query { hello }" }'


#!/bin/bash

source ~/.dcb.sh

export TOKEN=`curl -s \
  -d "client_id=$KEYCLOAK_CLIENT" \
  -d "client_secret=$KEYCLOAK_SECRET" \
  -d "username=$DEVTESTUSER1" \
  -d "password=$DEVTESTPASS1" \
  -d "grant_type=password" \
  "$KEYCLOAK_BASE/protocol/openid-connect/token" | jq -r '.access_token'`

echo $TOKEN


curl -X POST http://localhost:8080/hostlmss -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "citation" : {
    "bibClusterId": "1eea30f8-83f1-4780-8c0d-620179a9267e"
  },
  "requestor": {
    "localSystemCode": "DUMMY1",
    "localId": "dp0014",
    "homeLibraryCode": "PHS"
  },
  "pickupLocation": {
    "code": "PU-DA-1-1-KI"
  },
  "description": "Brain of the Firm 2e: 10 (Classic Beer Series)"

}'

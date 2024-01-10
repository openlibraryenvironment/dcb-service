#!/bin/bash


RESHARE_ROOT_UUID=`uuidgen --sha1 -n @dns --name org.olf.dcb`
AGENCIES_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name Agency`
HOSTLMS_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name HostLms`
LOCATION_NS_UUID=`uuidgen --sha1 -n $RESHARE_ROOT_UUID --name Location`

echo Logging in
TOKEN=`./login`

echo token: $TOKEN

TARGET="http://localhost:8080"

# curl -X POST http://localhost:8080/hostlmss -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
#   "citation" : {
#     "bibClusterId": "1eea30f8-83f1-4780-8c0d-620179a9267e"
#   },
#   "requestor": {
#     "localSystemCode": "DUMMY1",
#     "localId": "dp0014",
#     "homeLibraryCode": "PHS"
#   },
#   "pickupLocation": {
#     "code": "PU-DA-1-1-KI"
#   },
#   "description": "Brain of the Firm 2e: 10 (Classic Beer Series)"
# 
# }'

echo FOLIO ECSTEST
echo
curl -X POST $TARGET/hostlmss -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $HOSTLMS_NS_UUID --name cs00000int_0004`'", 
  "code":"cs00000int_0004", 
  "name":"cs00000int_0004", 
  "lmsClientClass": "org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient",
  "ingestSourceClass": "org.olf.dcb.core.interaction.folio.FolioOaiPmhIngestSource",
  "clientConfig": { 
    "folio-tenant": "cs00000int_0004",
    "user-base-url": "https://ecs-testing-consortium-intg.int.aws.folio.org/",
    "ingest": "true",
    "base-url": "https://edge-ecs-testing-special-integration.int.aws.folio.org",
    "metadata-prefix": "marc21_withholdings",
    "record-syntax": "oai_dc",
    "apikey": "eyJzIjoidTBxaEZjWFd1YiIsInQiOiJjczAwMDAwaW50XzAwMDQiLCJ1IjoiRUJTQ09FZGdlIn0="
  } 
}'

echo

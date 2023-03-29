#!/bin/bash

# This script creates a host LMS

source ~/.dcb.sh
source ./reshare_uuids.sh
TOKEN=`./login`

TARGET_DCB_SYSTEM="https://dcb.libsdev.k-int.com"

curl -X GET $TARGET_DCB_SYSTEM/hostlmss -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN"

curl -X POST $TARGET_DCB_SYSTEM/hostlmss -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $HOSTLMS_NS_UUID --name MYTESTLMS`'", 
  "code":"MYTESTLMS", 
  "name":"My Test Lms Name", 
  "lmsClientClass": "org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient", 
  "clientConfig": { 
    "some":"config",
    "values":"here",
    "ingest": "false"
  } 
}'


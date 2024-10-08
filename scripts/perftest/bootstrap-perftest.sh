#!/bin/bash

TARGET="http://localhost:8080"

OPENRS_ROOT_UUID=`uuidgen --sha1 -n @dns --name org.olf.dcb`
AGENCIES_NS_UUID=`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name Agency`
HOSTLMS_NS_UUID=`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name HostLms`
LOCATION_NS_UUID=`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name Location`

echo Logging in
TOKEN=`../login`

echo confirm
curl $TARGET/health
curl $TARGET/info


echo Dummy1
curl -X POST $TARGET/hostlmss -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $HOSTLMS_NS_UUID --name DUMMY1`'", 
  "code":"DUMMY1", 
  "name":"Dummy1", 
  "lmsClientClass": "org.olf.dcb.devtools.interaction.dummy.DummyLmsClient", 
  "clientConfig": { 
    "ingest": "true",
    "num-records-to-generate": 5000,
    "shelving-locations": "LM1-A1-SL1,LM1-A1-SL2,LM1-A2-SL1,LM1-A2-SL2,LM1-A2-SL3",
    "contextHierarchy": [ "DUMMY1", "PERFTEST", "GLOBAL" ]
  } 
}'

echo Dummy2
curl -X POST $TARGET/hostlmss -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $HOSTLMS_NS_UUID --name DUMMY2`'", 
  "code":"DUMMY2", 
  "name":"Dummy2", 
  "lmsClientClass": "org.olf.dcb.devtools.interaction.dummy.DummyLmsClient", 
  "clientConfig": { 
    "ingest": "true",
    "num-records-to-generate": 100,
    "shelving-locations": "LM2-A1-SL1,LM2-A2-SL1",
    "contextHierarchy": [ "DUMMY2", "PERFTEST", "GLOBAL" ]
  } 
}'

# Generate a system with 0 holdings - we will use this as our requester
echo Dummy3
curl -X POST $TARGET/hostlmss -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $HOSTLMS_NS_UUID --name DUMMY3`'", 
  "code":"DUMMY3", 
  "name":"Dummy3", 
  "lmsClientClass": "org.olf.dcb.devtools.interaction.dummy.DummyLmsClient", 
  "clientConfig": { 
    "ingest": "true",
    "num-records-to-generate": 0,
    "shelving-locations": "LM3-A1-SL1,LM3-A2-SL1",
    "contextHierarchy": [ "DUMMY3", "PERFTEST", "GLOBAL" ]
  } 
}'

# Generate a system with 0 holdings - we will use this as our requester
echo Dummy4
curl -X POST $TARGET/hostlmss -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $HOSTLMS_NS_UUID --name DUMMY4`'", 
  "code":"DUMMY4", 
  "name":"Dummy4", 
  "lmsClientClass": "org.olf.dcb.devtools.interaction.dummy.DummyLmsClient", 
  "clientConfig": { 
    "ingest": "true",
    "num-records-to-generate": 0,
    "shelving-locations": "LM4-A1-SL1,LM4-A2-SL1",
    "contextHierarchy": [ "DUMMY3", "PERFTEST", "GLOBAL" ]
  } 
}'



echo
echo Agency 1-1
echo
curl -X POST $TARGET/agencies -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $AGENCIES_NS_UUID --name DA-1-1`'",    "code":"DA-1-1",      "name":"Dummy1Agency1 (Sheffield, UK)",   "hostLMSCode": "DUMMY1", "authProfile": "BASIC/BARCODE+PIN", "latitude":53.383331, "longitude":-1.466667, "isSupplyingAgency":true, "isBorrowingAgency":true }'

echo
echo Agency 1-2
echo
curl -X POST $TARGET/agencies -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $AGENCIES_NS_UUID --name DA-1-2`'",    "code":"DA-1-2",      "name":"Dummy1Agency2 (Leeds, UK)",       "hostLMSCode": "DUMMY1", "authProfile": "BASIC/BARCODE+PIN", "latitude":53.801277, "longitude":-1.548567, "isSupplyingAgency":true, "isBorrowingAgency":true }'

echo
echo Agency 2-1
echo
curl -X POST $TARGET/agencies -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $AGENCIES_NS_UUID --name DA-2-1`'",    "code":"DA-2-1",      "name":"Dummy2Agency1 (Boston, USA)",     "hostLMSCode": "DUMMY2", "authProfile": "BASIC/BARCODE+PIN",  "latitude":42.361145, "longitude":-71.057083, "isSupplyingAgency":true, "isBorrowingAgency":true }'

echo
echo Agency 2-2
echo
curl -X POST $TARGET/agencies -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $AGENCIES_NS_UUID --name DA-2-2`'",    "code":"DA-2-2",      "name":"Dummy2Agency2 (Columbia, MO, USA)", "hostLMSCode": "DUMMY2", "authProfile": "BASIC/BARCODE+PIN", "latitude":38.951561, "longitude":-92.328636, "isSupplyingAgency":true, "isBorrowingAgency":true }'

echo
echo Agency 3-1
echo
curl -X POST $TARGET/agencies -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $AGENCIES_NS_UUID --name DA-3-1`'",    "code":"DA-3-1",      "name":"Dummy3Agency1 (Grand Junction, CO, USA)",               "hostLMSCode": "DUMMY3", "authProfile": "BASIC/BARCODE+PIN", "latitude":39.0588, "longitude": -108.5587, "isSupplyingAgency":true, "isBorrowingAgency":true }'

echo
echo Agency 3-2
echo
curl -X POST $TARGET/agencies -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $AGENCIES_NS_UUID --name DA-3-2`'", "code":"DA-3-2", "name":"Dummy3Agency2", "hostLMSCode": "DUMMY3", "authProfile": "BASIC/BARCODE+PIN", "isSupplyingAgency":true, "isBorrowingAgency":true }'

echo
echo Agency 4-1
echo
curl -X POST $TARGET/agencies -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id":"'`uuidgen --sha1 -n $AGENCIES_NS_UUID --name DA-4-1`'",    "code":"DA-4-1",      "name":"Dummy4Agency1 (Grand Junction, CO, USA)",               "hostLMSCode": "DUMMY4", "authProfile": "BASIC/BARCODE+PIN", "latitude":39.0588, "longitude": -108.5587, "isSupplyingAgency":true, "isBorrowingAgency":true }'


echo
echo Shelving location to agency mappings
echo

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name SL-DUMMY1-LM1-A1-SL1`'",
  "fromCategory":"Location", "fromContext":"DUMMY1", "fromValue":"LM1-A1-SL1", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-1-1" }'

echo

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name SL-DUMMY1-LM1-A1-SL2`'",
  "fromCategory":"Location", "fromContext":"DUMMY1", "fromValue":"LM1-A1-SL2", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-1-1" }'

echo

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name SL-DUMMY1-LM1-A2-SL1`'",
  "fromCategory":"Location", "fromContext":"DUMMY1", "fromValue":"LM1-A2-SL1", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-1-2" }'

echo

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name SL-DUMMY1-LM1-A2-SL2`'",
  "fromCategory":"Location", "fromContext":"DUMMY1", "fromValue":"LM1-A2-SL2", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-1-2" }'

echo

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name SL-DUMMY1-LM1-A2-SL3`'",
  "fromCategory":"Location", "fromContext":"DUMMY1", "fromValue":"LM1-A2-SL3", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-1-2" }'

echo


curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name SL-DUMMY2-LM2-A1-SL1`'",
  "fromCategory":"Location", "fromContext":"DUMMY2", "fromValue":"LM2-A1-SL1", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-2-1" }'

echo

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name SL-DUMMY2-LM2-A2-SL1`'",
  "fromCategory":"Location", "fromContext":"DUMMY2", "fromValue":"LM2-A2-SL1", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-2-2" }'

echo


curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name SL-DUMMY3-LM3-A1-SL1`'",
  "fromCategory":"Location", "fromContext":"DUMMY3", "fromValue":"LM3-A1-SL1", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-3-1" }'

echo

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name SL-DUMMY3-LM3-A2-SL1`'",
  "fromCategory":"Location", "fromContext":"DUMMY3", "fromValue":"LM3-A2-SL1", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-3-2" }'


echo
echo Item Type Mappings - to canonical
echo

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name IT-DUMMY1-FROM-BKM`'",
  "fromCategory":"ItemType", "fromContext":"DUMMY1", "fromValue":"BKM", "toCategory":"ItemType", "toContext":"DCB", "toValue":"CIRC" }'

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name IT-DUMMY2-FROM-BKM`'",
  "fromCategory":"ItemType", "fromContext":"DUMMY2", "fromValue":"BKM", "toCategory":"ItemType", "toContext":"DCB", "toValue":"CIRC" }'

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name IT-DUMMY3-FROM-BKM`'",
  "fromCategory":"ItemType", "fromContext":"DUMMY3", "fromValue":"BKM", "toCategory":"ItemType", "toContext":"DCB", "toValue":"CIRC" }'

echo
echo Item type mappings - to local
echo

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name IT-DUMMY1-TO-BKM`'",
  "fromCategory":"ItemType", "fromContext":"DCB", "fromValue":"CIRC", "toCategory":"ItemType", "toContext":"DUMMY1", "toValue":"BKM" }'

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name IT-DUMMY2-TO-BKM`'",
  "fromCategory":"ItemType", "fromContext":"DCB", "fromValue":"CIRC", "toCategory":"ItemType", "toContext":"DUMMY2", "toValue":"BKM" }'

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name IT-DUMMY3-TO-BKM`'",
  "fromCategory":"ItemType", "fromContext":"DCB", "fromValue":"CIRC", "toCategory":"ItemType", "toContext":"DUMMY3", "toValue":"BKM" }'


echo
echo Locations
echo

curl -X POST $TARGET/locations -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name PU-DA-1-1-KI`'",
  "code":"PU-DA-1-1-KI",
  "name":"K-Int Office",
  "type":"PICKUP",
  "agency":"'`uuidgen --sha1 -n $AGENCIES_NS_UUID --name DA-1-1`'",
  "isPickup":true,
  "latitude":53.383331,
  "longitude":-1.466667
}'

curl -X POST $TARGET/locations -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name PU-DA-4-1-KI`'",
  "code":"PU-DA-4-1-KI",
  "name":"PUA TEST LOC",
  "type":"PICKUP",
  "agency":"'`uuidgen --sha1 -n $AGENCIES_NS_UUID --name DA-4-1`'",
  "isPickup":true,
  "latitude":53.383331,
  "longitude":-1.466667
}'

echo
echo Location Mappings - from a User HomeLibrary to an agency
echo
# DUMMY1:PHS == DA-1-1
curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name MAP-Location-DUMMY1-PHS-AGENCY-DCB`'",
  "fromCategory":"Location", "fromContext":"DUMMY1", "fromValue":"PHS", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-1-1" }'

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name MAP-Location-DUMMY1-TR-AGENCY-DCB`'",
  "fromCategory":"Location", "fromContext":"DUMMY1", "fromValue":"TR", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-1-1" }'

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name MAP-Location-DUMMY1-PU-DA-1-1-KI-AGENCY-DCB`'",
  "fromCategory":"PickupLocation", "fromContext":"DCB", "fromValue":"PU-DA-1-1-KI", "toCategory":"AGENCY", "toContext":"DCB", "toValue":"DA-1-1" }'

# DUMMYn to SPINE

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name DUMMY1-patronType-STD-to-DCB`'",
  "fromCategory":"patronType", "fromContext":"DUMMY1", "fromValue":"STD", "toCategory":"patronType", "toContext":"DCB", "toValue":"STD" }'

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name DUMMY2-patronType-STD-to-DCB`'",
  "fromCategory":"patronType", "fromContext":"DUMMY2", "fromValue":"STD", "toCategory":"patronType", "toContext":"DCB", "toValue":"STD" }'

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name DUMMY3-patronType-STD-to-DCB`'",
  "fromCategory":"patronType", "fromContext":"DUMMY3", "fromValue":"STD", "toCategory":"patronType", "toContext":"DCB", "toValue":"STD" }'

# SPINE to DUMMYn

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name DCB-patronType-STD-to-DUMMY1`'",
  "fromCategory":"patronType", "fromContext":"DCB", "fromValue":"STD", "toCategory":"patronType", "toContext":"DUMMY1", "toValue":"STD" }'

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name DCB-patronType-STD-to-DUMMY2`'",
  "fromCategory":"patronType", "fromContext":"DCB", "fromValue":"STD", "toCategory":"patronType", "toContext":"DUMMY2", "toValue":"STD" }'

curl -X POST $TARGET/referenceValueMappings -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{ 
  "id": "'`uuidgen --sha1 -n $OPENRS_ROOT_UUID --name DCB-patronType-STD-to-DUMMY3`'",
  "fromCategory":"patronType", "fromContext":"DCB", "fromValue":"STD", "toCategory":"patronType", "toContext":"DUMMY3", "toValue":"STD" }'

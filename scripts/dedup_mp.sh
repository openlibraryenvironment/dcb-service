
#!/bin/bash

TARGET="http://localhost:8080"

DCB_ROOT_UUID=`uuidgen --sha1 -n @dns --name org.olf.dcb`
AGENCIES_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name Agency`
HOSTLMS_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name HostLms`
LOCATION_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name Location`

TOKEN=`./login`

curl -X POST "$TARGET/admin/threads" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d ""
curl -X POST "$TARGET/admin/dedupe/matchpoints" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d ""


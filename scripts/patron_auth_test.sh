#!/bin/bash

TARGET="http://localhost:8080"
#TARGET="https://dcb-uat.sph.k-int.com"

TOKEN=$(./login)

agency_id=$(uuidgen)
agency_code="stlouis"
agency_name="stlouis"
auth_profile="BASIC/BARCODE+PASSWORD"
idp_url=""
host_lms_code="stlouis"

patron_barcode="0088888888"
patron_password="1234"

longitude=1.2345
latitude=6.7890

echo "create new agency: "
agency_request_data='{"id": "'${agency_id}'", "code": "'${agency_code}'", "name": "'${agency_name}'", "authProfile": "'${auth_profile}'", "idpUrl": "'${idp_url}'", "hostLMSCode": "'${host_lms_code}'", "longitude": '${longitude}', "latitude": '${latitude}'}'
curl -s -X POST "$TARGET/agencies" -H "Accept-Language: en" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d "${agency_request_data}" | json_pp

sleep 3
echo
echo "check agency exists by id: $agency_id"
curl -s -X GET "$TARGET/agencies/${agency_id}" -H "Accept-Language: en" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" | json_pp

sleep 1
echo
echo "Patron auth: "
auth_request_data='{"agencyCode": "'${agency_code}'", "patronPrinciple": "'${patron_barcode}'", "secret": "'${patron_password}'"}'
curl -s -X POST "$TARGET/patron/auth" -H "Accept-Language: en" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d "${auth_request_data}" | json_pp

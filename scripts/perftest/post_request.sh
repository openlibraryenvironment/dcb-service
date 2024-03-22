#!/bin/bash

TARGET="http://localhost:8080"
echo Logging in
TOKEN=`../login`

curl -v -X POST $TARGET/patrons/requests/place -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d '{
  "citation":{"bibClusterId":"12c565f6-4f03-4b16-b29a-ddc302952969"},"requestor":{"localSystemCode":"DUMMY3","localId":"1380112","homeLibraryCode":"DA-1-1"},"pickupLocation":{"code":"6c669866-a2a0-54aa-8cea-4e9437dec30c"},"description":"Ian testing SLCL 
Test Item"}
'


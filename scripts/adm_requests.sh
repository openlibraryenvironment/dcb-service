#!/bin/bash

source ~/.dcb.sh

echo $KEYCLOAK_BASE

export TOKEN=`curl \
  -d "client_id=$KEYCLOAK_CLIENT" \
  -d "client_secret=$KEYCLOAK_SECRET" \
  -d "username=$DCB_ADMIN_USER" \
  -d "password=$DCB_ADMIN_PASS" \
  -d "grant_type=password" \
  "$KEYCLOAK_BASE/protocol/openid-connect/token" | jq -r '.access_token'`


# See https://micronaut-projects.github.io/micronaut-security/latest/guide/
# See https://github.com/tchiotludo/akhq/issues/556
# curl -v -v -H "Authorization: Bearer $TOKEN" "http://localhost:8080/secured"
echo Token: $TOKEN

curl -H "Authorization: Bearer $TOKEN" "https://dcb.libsdev.k-int.com/hostlmss"
curl -H "Authorization: Bearer $TOKEN" "https://dcb.libsdev.k-int.com/admin/patrons/requests"

#!/bin/bash


# ~/.dcb.sh needs to define
# KEYCLOAK_BASE
# KEYCLOAK_CERT_URL
# KEYCLOAK_CLIENT
# KEYCLOAK_SECRET
# DCB_ADMIN_USER
# DCB_ADMIN_PASS
#
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
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/secured"

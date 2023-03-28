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

export JWT=`curl \
  -d "client_id=$KEYCLOAK_CLIENT" \
  -d "client_secret=$KEYCLOAK_SECRET" \
  -d "username=$DCB_ADMIN_USER" \
  -d "password=$DCB_ADMIN_PASS" \
  -d "grant_type=password" \
  "$KEYCLOAK_BASE/protocol/openid-connect/token"`

echo $JWT | jq

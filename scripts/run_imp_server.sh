#!/bin/bash

# This script is predicated on a ~/.dcb.sh script which sets the following environment variables
# KEYCLOAK_CERT_URL - the url of the keycloak certificate
source ~/.dcb.sh

echo running with keycloak at ${KEYCLOAK_CERT_URL}

export DATASOURCE_HOST="localhost"
export DATASOURCE_PORT="5432"
export DATASOURCE_DB="mobius"
export R2DBC_DATASOURCES_DEFAULT_USERNAME="mobius"
export R2DBC_DATASOURCES_DEFAULT_PASSWORD="mobius"
export R2DBC_DATASOURCES_DEFAULT_HOST="localhost"
export R2DBC_DATASOURCES_DEFAULT_PORT="5432"
export R2DBC_DATASOURCES_DEFAULT_DATABASE="mobius"
export R2DBC_DATASOURCES_DEFAULT_URL="r2dbc:postgresql://localhost:5432/mobius"
export DATASOURCES_DEFAULT_URL="jdbc:postgresql://localhost:5432/mobius"
export DATASOURCES_DEFAULT_USERNAME="mobius"
export DATASOURCES_DEFAULT_PASSWORD="mobius"

export MICRONAUT_HTTP_CLIENT_READ_TIMEOUT="PT1M"
export MICRONAUT_HTTP_CLIENT_MAX_CONTENT_LENGTH="20971520"
export DCB_INGEST_INTERVAL="1m"

java -jar ./dcb/build/libs/dcb-all.jar

#!/bin/bash

# This script is predicated on a ~/.dcb.sh script which sets the following environment variables
# KEYCLOAK_CERT_URL - the url of the keycloak certificate
source ~/.dcb.sh

echo running with keycloak at ${KEYCLOAK_CERT_URL}

export REACTOR_DEBUG="true"
export MICRONAUT_HTTP_CLIENT_READ_TIMEOUT="PT1M"
export MICRONAUT_HTTP_CLIENT_MAX_CONTENT_LENGTH="20971520"
export DCB_INGEST_INTERVAL="1m"
export DCB_SCHEDULED_TASKS_ENABLED="true"

echo Access postgres with "psql -h localhost -p 49168 -U test" and the password test


export HOSTS_DUMMYA_CLIENT_INGEST="false"


./gradlew run
# ./gradlew nativeRun

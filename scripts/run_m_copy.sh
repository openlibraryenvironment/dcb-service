#!/bin/bash

# This script is predicated on a ~/.dcb.sh script which sets the following environment variables
# KEYCLOAK_CERT_URL - the url of the keycloak certificate
source ~/.dcb.sh

echo running with keycloak at ${KEYCLOAK_CERT_URL}

# export REACTOR_DEBUG="true"
export MICRONAUT_HTTP_CLIENT_READ_TIMEOUT="PT1M"
export MICRONAUT_HTTP_CLIENT_MAX_CONTENT_LENGTH="20971520"
export DCB_INGEST_INTERVAL="1m"
export DCB_SCHEDULED_TASKS_ENABLED="true"
# export LOGGER_LEVELS_ORG_OLF_DCB="DEBUG"
export LOGGER_LEVELS_ORG_OLF_DCB_INGEST_MARC_MARCINGESTSOURCE="INFO"
export LOGGER_LEVELS_ORG_OLF_DCB_CORE_INTERACTION_POLARIS="DEBUG"
export DCB_SHUTDOWN_MAXWAIT=60000

export DATASOURCE_HOST="localhost"
export DATASOURCE_PORT="5432"
export DATASOURCE_DB="mobius"
export R2DBC_DATASOURCES_DEFAULT_USERNAME="mobius"
export R2DBC_DATASOURCES_DEFAULT_PASSWORD="mobius"
export R2DBC_DATASOURCES_DEFAULT_HOST="localhost"
export R2DBC_DATASOURCES_DEFAULT_PORT="5432"
export R2DBC_DATASOURCES_DEFAULT_DATABASE="mobius"
export R2DBC_DATASOURCES_DEFAULT_URL="r2dbc:postgresql://localhost:5432/mobius"
export R2DBC_DATASOURCES_DEFAULT_OPTIONS_MAXSIZE="20"
export DATASOURCES_DEFAULT_URL="jdbc:postgresql://localhost:5432/mobius"
export DATASOURCES_DEFAULT_USERNAME="mobius"
export DATASOURCES_DEFAULT_PASSWORD="mobius"

export MICRONAUT_HTTP_CLIENT_READ_TIMEOUT="PT1M"
export MICRONAUT_HTTP_CLIENT_MAX_CONTENT_LENGTH="20971520"

# SourceRecordService - tracks the SourceRecord table looking for records that need to be processed

export DCB_SCHEDULED_TASKS_SKIPPED=TrackingServiceV3,AvailabilityCheckJob
# export DCB_SCHEDULED_TASKS_SKIPPED=TrackingServiceV3,IngestService,SourceRecordService,IngestJob,AvailabilityCheckJob
export DCB_TRACKING_DRYRUN=true

export DCB_INDEX_NAME="mobius"
export DCB_INDEX_USERNAME="elastic"
export DCB_INDEX_PASSWORD="elastic"
export ELASTICSEARCH_HTTP_HOSTS="http://localhost:9200"
export ELASTICSEARCH_INDEXES_INSTANCES="mobius"
export FEATURES_ENABLED="improved-clustering"
export FEATURES_INGEST_V2_ENABLED="true"

# export CONCURRENCY_GROUPS_DEFAULT_LIMIT=3
# export CONCURRENCY_GROUPS_FOLIO_OAI_LIMIT=3
#


export JAVA_OPTIONS="-server -Xmx2G -Xms512m -XX:+UseContainerSupport -XX:+AllowRedefinitionToAddDeleteMethods -XX:MinRAMPercentage=50.0 -XX:MaxRAMPercentage=80.0 -XX:InitialRAMPercentage=50.0 -XX:+PrintFlagsFinal -Dcom.sun.net.ssl.checkRevocation=false --add-exports=java.base/jdk.internal.ref=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.management/sun.management=ALL-UNNAMED --add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp"

java $JAVA_OPTIONS -jar ./dcb/build/libs/dcb-*-all.jar

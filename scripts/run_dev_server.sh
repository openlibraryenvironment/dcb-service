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
export DCB_ENV_CODE="LOCAL-DEV"
export DCB_ENV_DESCRIPTION="Local Dev"
export LOGGER_LEVELS_ORG_OLF_DCB="DEBUG"
export DCB_SHUTDOWN_MAXWAIT=60000
export DCB_INDEX_NAME=mobius-si
export R2DBC_DATASOURCES_DEFAULT_OPTIONS_MAX_SIZE=27
export R2DBC_DATASOURCES_DEFAULT_OPTIONS_MAX_SIZE=28
export FEATURES_INGEST_V2_ENABLED="true"
export DCB_GLOBAL_NOTIFICATIONS_SLACK_URL="https://hooks.slack.com/services/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"
export DCB_GLOBAL_NOTIFICATIONS_SLACK_PROFILE="slack"


# export DCB_SCHEDULED_TASKS_SKIPPED=IngestService,IngestJob,SourceRecordService,TrackingServiceV3
# export DCB_TRACKING_DRYRUN=true


echo Access postgres with "psql -h localhost -p 49168 -U test" and the password test


#export HOSTS_KCTOWERS_CLIENT_INGEST="false"
#export HOSTS_SANDBOX_CLIENT_INGEST="false"
#export HOSTS_DUMMYA_CLIENT_INGEST="true"


./gradlew run
# ./gradlew nativeRun

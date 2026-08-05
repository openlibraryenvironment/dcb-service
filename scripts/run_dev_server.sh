#!/bin/bash
#
# Verbose dev server: heavier logging, faster ingest, scheduled tasks on.
#
# This does NOT start any dependencies. Use scripts/local_dev.sh for that (it
# starts Postgres and a search backend), or bring them up yourself first --
# Micronaut Test Resources no longer provisions the database.
#
# Predicated on a ~/.dcb.sh which sets, at minimum:
#   KEYCLOAK_CERT_URL - the url of the keycloak certificate
# and optionally:
#   DCB_GLOBAL_NOTIFICATIONS_SLACK_URL - Slack incoming webhook

if [[ -f ~/.dcb.sh ]]; then
	source ~/.dcb.sh
else
	echo "WARNING: no ~/.dcb.sh found; Keycloak-secured endpoints will not work." >&2
fi

echo "running with keycloak at ${KEYCLOAK_CERT_URL:-<unset>}"

export REACTOR_DEBUG="true"
export MICRONAUT_HTTP_CLIENT_READ_TIMEOUT="PT1M"
export MICRONAUT_HTTP_CLIENT_MAX_CONTENT_LENGTH="20971520"
export DCB_INGEST_INTERVAL="1m"
export DCB_SCHEDULED_TASKS_ENABLED="true"
export DCB_ENV_CODE="${DCB_ENV_CODE:-LOCAL-DEV}"
export DCB_ENV_DESCRIPTION="${DCB_ENV_DESCRIPTION:-Local Dev}"
export LOGGER_LEVELS_ORG_OLF_DCB="${LOGGER_LEVELS_ORG_OLF_DCB:-DEBUG}"
export DCB_SHUTDOWN_MAXWAIT=60000
export R2DBC_DATASOURCES_DEFAULT_OPTIONS_MAX_SIZE=28
export FEATURES_INGEST_V2_ENABLED="true"

# Requires a search backend to be running. Unset it (DCB_INDEX_NAME= ) to boot
# without the shared index.
export DCB_INDEX_NAME="${DCB_INDEX_NAME-mobius-si}"

# Set DCB_GLOBAL_NOTIFICATIONS_SLACK_URL in ~/.dcb.sh, not here -- a webhook URL
# is a credential and must not live in the repo.
if [[ -n "${DCB_GLOBAL_NOTIFICATIONS_SLACK_URL:-}" ]]; then
	export DCB_GLOBAL_NOTIFICATIONS_SLACK_PROFILE="slack"
fi

# export DCB_SCHEDULED_TASKS_SKIPPED=IngestService,IngestJob,SourceRecordService,TrackingServiceV3
# export DCB_TRACKING_DRYRUN=true

echo "Access postgres with: psql -h ${DB_HOST:-localhost} -p ${DB_PORT:-5432} -U ${DB_USER:-dcb} -d ${DB_DATABASE:-dcb}   (password: ${DB_PASSWORD:-dcb})"

#export HOSTS_KCTOWERS_CLIENT_INGEST="false"
#export HOSTS_SANDBOX_CLIENT_INGEST="false"
#export HOSTS_DUMMYA_CLIENT_INGEST="true"

./gradlew run
# ./gradlew nativeRun

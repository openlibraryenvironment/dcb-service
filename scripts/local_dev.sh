#!/bin/bash
#
# Start DCB for local development.
#
# Micronaut Test Resources used to provision Postgres automatically. That plugin
# was removed from dcb/build.gradle, so the database is now the developer's responsibility to start --
# this script does it, waits for it to be healthy, exports the datasource
# settings and runs the app.
#
#   ./scripts/local_dev.sh                 # Postgres + Elasticsearch 9 (default)
#   ./scripts/local_dev.sh --index os2     # Postgres + OpenSearch 2.x
#   ./scripts/local_dev.sh --index none    # Postgres only -- fastest loop
#   ./scripts/local_dev.sh --fresh         # wipe database AND index
#   ./scripts/local_dev.sh --fresh-db      # wipe the database only
#   ./scripts/local_dev.sh --fresh-index   # wipe the index only
#   ./scripts/local_dev.sh --down          # stop everything and exit
#   ./scripts/local_dev.sh --no-run        # bring dependencies up, don't run DCB
#
# --fresh-index only removes the volume for the active --index mode, so wiping
# while on os2 leaves an es9 index intact. Wiping the database without the index
# leaves documents behind whose cluster records no longer exist.
#
#   ./scripts/local_dev.sh --skip-tasks AvailabilityCheckJob,IngestJob
#   ./scripts/local_dev.sh --no-scheduled-tasks
#
#   ./scripts/local_dev.sh --office-hours 09:00:00-17:00:00
#   ./scripts/local_dev.sh --no-office-hours
#
# Office hours default to 17:00:00-17:50:00 UTC. AvailabilityCheckJob and
# IndexSynch run OUTSIDE the window, so a narrow one means they run nearly
# always. --no-office-hours removes the gate entirely (they never pause).
# Overridable with DCB_OFFICEHOURS_START / DCB_OFFICEHOURS_END too.
#
# Skippable task names (the class declaring the @AppTask @Scheduled method):
#   AvailabilityCheckJob  ClusterHousekeepingService  ConfigurationService
#   DcbProfileMembershipSyncJob  HealthMonitorTask  HouseKeepingService
#   IndexSynch  IngestJob  SourceRecordService  StatsService  TrackingScheduler
#
# Which tasks exist depends on configuration. The authoritative list for your
# run is in the startup log:
#   grep AppTaskAwareScheduledMethodProcessor <log>
#
# See docs/local-development.md for the longer explanation.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/scripts/docker-compose.yml"

# Pinned so the named volumes are stable regardless of where you invoke this
# from. Changing it orphans any database you already have.
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-scripts}"
PG_VOLUME="${COMPOSE_PROJECT_NAME}_dcb_pg_data"

# --- Database settings -------------------------------------------------------
# These are exported and will override what is defined in application-dev.yml, but will typically mirror it. This means a non-default DCB_PG_PORT
# reaches both the container and the application.
export DCB_PG_PORT="${DCB_PG_PORT:-5432}"
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-$DCB_PG_PORT}"
export DB_DATABASE="${DB_DATABASE:-dcb}"
export DB_USER="${DB_USER:-dcb}"
export DB_PASSWORD="${DB_PASSWORD:-dcb}"

# Set explicitly so DCB connects to the right database even if application-dev is edited, and makes the
# wiring visible in the environment banner below. Flyway migrates over JDBC
# while the application reads and writes over R2DBC; both must be the same DB.
export DATASOURCES_DEFAULT_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_DATABASE}"
export DATASOURCES_DEFAULT_USERNAME="${DB_USER}"
export DATASOURCES_DEFAULT_PASSWORD="${DB_PASSWORD}"
export R2DBC_DATASOURCES_DEFAULT_URL="r2dbc:pool:postgresql://${DB_HOST}:${DB_PORT}/${DB_DATABASE}"
export R2DBC_DATASOURCES_DEFAULT_USERNAME="${DB_USER}"
export R2DBC_DATASOURCES_DEFAULT_PASSWORD="${DB_PASSWORD}"

# Default to a full stack: Use --index none for the fast loop when you are not touching search.
INDEX_MODE="${DCB_INDEX_MODE:-es9}"
FRESH_DB=false
FRESH_INDEX=false
RUN_APP=true

# Scheduled task control. SKIP_TASKS is a comma-separated list of the *simple
# class names* that declare the @AppTask @Scheduled methods -- that is what
# AppTaskAwareScheduledMethodProcessor matches on. See the header for the names.
#
# Note TrackingScheduler, not TrackingServiceV3: this branch moved the
# annotations onto a dedicated scheduler class, so the old name is now a no-op.
SKIP_TASKS="${DCB_SCHEDULED_TASKS_SKIPPED:-}"
SCHEDULED_TASKS=true
SKIP_TASKS_EXPLICIT=false

# Office hours gate AvailabilityCheckJob and IndexSynch, which run via
# subscribeOnlyOutsideOfficeHours -- i.e. they run OUTSIDE this window. Times
# are parsed as LocalTime and treated as UTC, not local time.
#
# The default is the narrow window carried over from run_dev_server_fs.sh, so
# those jobs run almost all the time but the gate is still exercised. Note that
# leaving both unset is not equivalent: OfficeHours.isInsideHours() returns
# false when nothing is configured, so --no-office-hours means "always outside
# hours", and the jobs never pause at all.
OFFICEHOURS_START="${DCB_OFFICEHOURS_START:-17:00:00}"
OFFICEHOURS_END="${DCB_OFFICEHOURS_END:-17:50:00}"
OFFICEHOURS=true

while [[ $# -gt 0 ]]; do
	case "$1" in
		--index)
			INDEX_MODE="${2:-}"
			shift 2
			;;
		--index=*)
			INDEX_MODE="${1#*=}"
			shift
			;;
		--fresh)
			FRESH_DB=true
			FRESH_INDEX=true
			shift
			;;
		--fresh-db)
			FRESH_DB=true
			shift
			;;
		--fresh-index)
			FRESH_INDEX=true
			shift
			;;
		--skip-tasks)
			SKIP_TASKS="${2:-}"
			SKIP_TASKS_EXPLICIT=true
			shift 2
			;;
		--skip-tasks=*)
			SKIP_TASKS="${1#*=}"
			SKIP_TASKS_EXPLICIT=true
			shift
			;;
		--no-scheduled-tasks)
			SCHEDULED_TASKS=false
			shift
			;;
		--office-hours)
			OFFICEHOURS_START="${2%%-*}"
			OFFICEHOURS_END="${2##*-}"
			shift 2
			;;
		--office-hours=*)
			_oh="${1#*=}"
			OFFICEHOURS_START="${_oh%%-*}"
			OFFICEHOURS_END="${_oh##*-}"
			shift
			;;
		--no-office-hours)
			OFFICEHOURS=false
			shift
			;;
		--no-run)
			RUN_APP=false
			shift
			;;
		--down)
			echo "==> Stopping DCB dependencies"
			docker compose -f "$COMPOSE_FILE" --profile persistent-db --profile es9 --profile os2 down
			echo "Containers stopped. Data volumes kept:"
			echo "  ${PG_VOLUME}"
			echo "  ${COMPOSE_PROJECT_NAME}_es9_data  ${COMPOSE_PROJECT_NAME}_os2_data"
			echo "To destroy data too, prefer: ./scripts/local_dev.sh --fresh --no-run"
			echo "(never 'docker compose down -v' -- that removes every volume in the file)"
			exit 0
			;;
		-h|--help)
			sed -n '3,43p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
			exit 0
			;;
		*)
			echo "Unknown option: $1" >&2
			echo "Try --help." >&2
			exit 1
			;;
	esac
done

case "$INDEX_MODE" in
	none|es9|os2) ;;
	*)
		echo "--index must be one of: none, es9, os2 (got '${INDEX_MODE}')" >&2
		exit 1
		;;
esac

# Resolved after argument parsing, not during: --fresh may appear before
# --index, and which index volume to remove depends on the mode finally chosen.
# Only the active mode's volume is touched, so wiping while on os2 cannot
# destroy an ES 9 index you still want.
case "$INDEX_MODE" in
	es9) INDEX_VOLUME="${COMPOSE_PROJECT_NAME}_es9_data"; INDEX_SERVICE="elasticsearch9" ;;
	os2) INDEX_VOLUME="${COMPOSE_PROJECT_NAME}_os2_data"; INDEX_SERVICE="opensearch" ;;
	*)   INDEX_VOLUME=""; INDEX_SERVICE="" ;;
esac

# --- Preflight ---------------------------------------------------------------

if ! docker info >/dev/null 2>&1; then
	echo "Docker is not running (or not reachable). Start Docker Desktop and retry." >&2
	exit 1
fi

# Returns 0 when something is already accepting connections on the port.
port_in_use() {
	(exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1
}

our_postgres_running() {
	[[ "$(docker inspect -f '{{.State.Running}}' dcb-postgres 2>/dev/null)" == "true" ]]
}

health_of() {
	docker inspect -f '{{.State.Health.Status}}' "$1" 2>/dev/null
}

# Containers report healthy well after they report running; starting DCB against
# a not-yet-ready dependency fails with a misleading connection error.
wait_for_health() {
	local container="$1" attempts="${2:-90}"

	echo -n "==> Waiting for ${container} to report healthy"
	for _ in $(seq 1 "$attempts"); do
		if [[ "$(health_of "$container")" == "healthy" ]]; then
			echo " ok"
			return 0
		fi
		echo -n "."
		sleep 2
	done

	echo
	echo "${container} did not become healthy. Logs:" >&2
	docker logs --tail 30 "$container" >&2
	exit 1
}

# A native or WSL Postgres on 5432 shadows the container's published port: the
# container starts fine, but DCB connects to the other server and fails
# authentication. Catch that here rather than in a stack trace.
if port_in_use "$DCB_PG_PORT" && ! our_postgres_running; then
	cat >&2 <<EOF
Port ${DCB_PG_PORT} is already in use by something that is not dcb-postgres.

A PostgreSQL installed natively (or inside WSL) is the usual culprit; it will
shadow the container and DCB will fail with:

    FATAL: password authentication failed for user "dcb"

Either stop that server, or pick a different port:

    DCB_PG_PORT=5433 ./scripts/local_dev.sh $*
EOF
	exit 1
fi

# The legacy `elasticsearch` service (8.7.0) binds 9200 and DCB's 9.x client
# cannot talk to it. Checked before we build anything, because the ICU image
# build takes minutes and would be wasted.
if [[ "$INDEX_MODE" != "none" ]] \
	&& [[ "$(docker inspect -f '{{.State.Running}}' elasticsearch 2>/dev/null)" == "true" ]]; then

	cat >&2 <<EOF
The legacy 'elasticsearch' container (8.7.0) is running and owns port 9200.
DCB's 9.x client cannot talk to it -- you would get:

    [es/indices.exists] Expecting a response body, but none was sent

Stop it first:

    docker stop elasticsearch

or run without a shared index:

    ./scripts/local_dev.sh --index none
EOF
	exit 1
fi

# --- Database ----------------------------------------------------------------

if [[ "$FRESH_DB" == true ]]; then
	echo "==> Removing database volume ${PG_VOLUME}"
	# The container has to go first: Docker refuses to remove a volume still
	# attached to one, and `|| true` would swallow that into a silent no-op.
	docker compose -f "$COMPOSE_FILE" --profile persistent-db rm -sf postgres >/dev/null 2>&1 || true
	docker volume rm "$PG_VOLUME" >/dev/null 2>&1 || true
fi

echo "==> Starting Postgres on port ${DCB_PG_PORT}"
docker compose -f "$COMPOSE_FILE" --profile persistent-db up -d postgres

wait_for_health dcb-postgres 60

# The container only applies POSTGRES_PASSWORD when it initialises an empty data
# directory. A volume created earlier with different credentials keeps its old
# password, which looks exactly like a misconfiguration -- realign it instead.
docker exec dcb-postgres psql -U "$DB_USER" -d "$DB_DATABASE" \
	-c "ALTER ROLE ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';" >/dev/null

# The checks so far only prove the container is healthy from the inside. DCB
# connects through the published host port, which a native/WSL Postgres can
# quietly own instead -- so authenticate the same way DCB will before handing
# over. This is the difference between a clear message and a stack trace.
echo "==> Verifying authentication through the published port"
if ! docker run --rm -e PGPASSWORD="$DB_PASSWORD" postgres:18 \
	psql -h host.docker.internal -p "$DCB_PG_PORT" -U "$DB_USER" -d "$DB_DATABASE" \
	-c 'select 1' >/dev/null 2>&1; then

	cat >&2 <<EOF

Postgres is healthy inside its container, but connecting through host port
${DCB_PG_PORT} failed. Another PostgreSQL (commonly a native or WSL install) is
shadowing the container's published port, so DCB would connect to that one.

Check what is listening:

    ss -lntp | grep ${DCB_PG_PORT}          # Linux/WSL
    Get-NetTCPConnection -LocalPort ${DCB_PG_PORT} -State Listen   # PowerShell

Then either stop that server, or move ours out of the way:

    DCB_PG_PORT=5433 ./scripts/local_dev.sh
EOF
	exit 1
fi

# --- Search backend ----------------------------------------------------------

if [[ "$FRESH_INDEX" == true ]]; then
	if [[ -z "$INDEX_VOLUME" ]]; then
		echo "==> No index to remove (--index none)"
	else
		echo "==> Removing index volume ${INDEX_VOLUME}"
		docker compose -f "$COMPOSE_FILE" --profile es9 --profile os2 \
			rm -sf "$INDEX_SERVICE" >/dev/null 2>&1 || true
		if docker volume rm "$INDEX_VOLUME" >/dev/null 2>&1; then
			echo "    removed"
		else
			# Nothing to remove is the normal first-run case; still attached is not.
			if docker volume inspect "$INDEX_VOLUME" >/dev/null 2>&1; then
				echo "    WARNING: ${INDEX_VOLUME} still exists -- something else is using it." >&2
			else
				echo "    (did not exist)"
			fi
		fi
	fi
fi

case "$INDEX_MODE" in
	none)
		echo "==> No shared index (DCB_INDEX_NAME unset)"
		unset DCB_INDEX_NAME DCB_INDEX_USERNAME DCB_INDEX_PASSWORD || true
		unset ELASTICSEARCH_HTTP_HOSTS OPENSEARCH_HTTP_HOSTS || true
		;;
	es9)
		echo "==> Starting Elasticsearch 9.x (first run builds the ICU image)"
		docker compose -f "$COMPOSE_FILE" --profile es9 up -d --build elasticsearch9
		wait_for_health elasticsearch9
		export ELASTICSEARCH_HTTP_HOSTS="http://localhost:9200"
		export DCB_INDEX_NAME="${DCB_INDEX_NAME:-dcb-si}"
		export DCB_INDEX_USERNAME="${DCB_INDEX_USERNAME:-elastic}"
		export DCB_INDEX_PASSWORD="${DCB_INDEX_PASSWORD:-elastic}"
		unset OPENSEARCH_HTTP_HOSTS || true
		;;
	os2)
		echo "==> Starting OpenSearch 2.x (first run builds the ICU image)"
		docker compose -f "$COMPOSE_FILE" --profile os2 up -d --build opensearch
		wait_for_health opensearch
		export OPENSEARCH_HTTP_HOSTS="http://localhost:9200"
		export DCB_INDEX_NAME="${DCB_INDEX_NAME:-dcb-si}"
		unset ELASTICSEARCH_HTTP_HOSTS || true
		;;
esac

# --- Application settings ----------------------------------------------------

# Keycloak credentials live outside the repo. Optional: DCB boots without them,
# but secured endpoints will not work.
if [[ -f "${HOME}/.dcb.sh" ]]; then
	# shellcheck disable=SC1091
	source "${HOME}/.dcb.sh"
	echo "==> Sourced ~/.dcb.sh (keycloak at ${KEYCLOAK_CERT_URL:-unset})"
else
	echo "==> No ~/.dcb.sh found; skipping Keycloak configuration"
fi

export DCB_ENV_CODE="${DCB_ENV_CODE:-LOCAL-DEV}"
export DCB_ENV_DESCRIPTION="${DCB_ENV_DESCRIPTION:-Local Dev}"
export LOGGER_LEVELS_ORG_OLF_DCB="${LOGGER_LEVELS_ORG_OLF_DCB:-DEBUG}"
export DCB_SHUTDOWN_MAXWAIT="${DCB_SHUTDOWN_MAXWAIT:-60000}"
export DCB_LOG_APPENDERS="${DCB_LOG_APPENDERS:-STDOUT_SYNC}"

# --- Runtime tuning (from run_dev_server_fs.sh) ------------------------------

export REACTOR_DEBUG="${REACTOR_DEBUG:-true}"
export MICRONAUT_HTTP_CLIENT_READ_TIMEOUT="${MICRONAUT_HTTP_CLIENT_READ_TIMEOUT:-PT1M}"
export MICRONAUT_HTTP_CLIENT_MAX_CONTENT_LENGTH="${MICRONAUT_HTTP_CLIENT_MAX_CONTENT_LENGTH:-20971520}"
export R2DBC_DATASOURCES_DEFAULT_OPTIONS_MAX_SIZE="${R2DBC_DATASOURCES_DEFAULT_OPTIONS_MAX_SIZE:-28}"
export DCB_INGEST_INTERVAL="${DCB_INGEST_INTERVAL:-1m}"
export DCB_GLOBALS_ACTIVE_REQUEST_LIMIT="${DCB_GLOBALS_ACTIVE_REQUEST_LIMIT:-100}"
export FEATURES_INGEST_V2_ENABLED="${FEATURES_INGEST_V2_ENABLED:-true}"
export FEATURES_ENABLED="${FEATURES_ENABLED:-improved-clustering}"

# Short polling intervals so state transitions are observable in a dev session
# rather than minutes apart.
export DCB_POLLING_DURATIONS_REQUEST_PLACED_AT_BORROWING_AGENCY="${DCB_POLLING_DURATIONS_REQUEST_PLACED_AT_BORROWING_AGENCY:-3m}"
export DCB_POLLING_DURATIONS_REQUEST_PLACED_AT_PICKUP_AGENCY="${DCB_POLLING_DURATIONS_REQUEST_PLACED_AT_PICKUP_AGENCY:-3m}"
export DCB_POLLING_DURATIONS_PICKUP_TRANSIT="${DCB_POLLING_DURATIONS_PICKUP_TRANSIT:-3m}"
export DCB_POLLING_DURATIONS_RECEIVED_AT_PICKUP="${DCB_POLLING_DURATIONS_RECEIVED_AT_PICKUP:-3m}"
export DCB_POLLING_DURATIONS_READY_FOR_PICKUP="${DCB_POLLING_DURATIONS_READY_FOR_PICKUP:-3m}"
export DCB_POLLING_DURATIONS_RETURN_TRANSIT="${DCB_POLLING_DURATIONS_RETURN_TRANSIT:-3m}"
export DCB_POLLING_DURATIONS_LOANED="${DCB_POLLING_DURATIONS_LOANED:-1m}"

# Slack webhook is a credential: set DCB_GLOBAL_NOTIFICATIONS_SLACK_URL in
# ~/.dcb.sh, never here.
if [[ -n "${DCB_GLOBAL_NOTIFICATIONS_SLACK_URL:-}" ]]; then
	export DCB_GLOBAL_NOTIFICATIONS_SLACK_PROFILE="slack"
fi

# --- Office hours ------------------------------------------------------------

if [[ "$OFFICEHOURS" == true ]]; then
	export DCB_OFFICEHOURS_START="$OFFICEHOURS_START"
	export DCB_OFFICEHOURS_END="$OFFICEHOURS_END"
	echo "==> Office hours ${OFFICEHOURS_START}-${OFFICEHOURS_END} UTC (AvailabilityCheckJob/IndexSynch pause inside this window)"
else
	unset DCB_OFFICEHOURS_START DCB_OFFICEHOURS_END || true
	echo "==> Office hours unset: treated as always outside hours, so nothing pauses"
fi

# --- Scheduled tasks ---------------------------------------------------------

# AvailabilityCheckJob needs the shared index; without one it fails on a timer
# with "Failed to inject value for parameter [sharedIndexService]". Skip it by
# default when running without an index, unless the caller said otherwise.
if [[ "$INDEX_MODE" == "none" && "$SKIP_TASKS_EXPLICIT" != true && -z "$SKIP_TASKS" ]]; then
	SKIP_TASKS="AvailabilityCheckJob"
	echo "==> --index none: skipping AvailabilityCheckJob (it requires the shared index)"
fi

if [[ "$SCHEDULED_TASKS" != true ]]; then
	export DCB_SCHEDULED_TASKS_ENABLED="false"
	echo "==> All scheduled tasks disabled"
else
	export DCB_SCHEDULED_TASKS_ENABLED="true"
	if [[ -n "$SKIP_TASKS" ]]; then
		export DCB_SCHEDULED_TASKS_SKIPPED="$SKIP_TASKS"
		echo "==> Scheduled tasks skipped: ${SKIP_TASKS}"
	fi
fi

cat <<EOF

==> Environment
    DB_HOST=${DB_HOST}
    DB_PORT=${DB_PORT}
    DB_DATABASE=${DB_DATABASE}
    DB_USER=${DB_USER}
    DB_PASSWORD=${DB_PASSWORD}
    DCB_INDEX_NAME=${DCB_INDEX_NAME:-<unset>}
    R2DBC_DATASOURCES_DEFAULT_URL=${R2DBC_DATASOURCES_DEFAULT_URL}
    DATASOURCES_DEFAULT_URL=${DATASOURCES_DEFAULT_URL}
    DCB_SCHEDULED_TASKS_ENABLED=${DCB_SCHEDULED_TASKS_ENABLED}
    DCB_SCHEDULED_TASKS_SKIPPED=${DCB_SCHEDULED_TASKS_SKIPPED:-<none>}
    DCB_OFFICEHOURS=${DCB_OFFICEHOURS_START:-<unset>}-${DCB_OFFICEHOURS_END:-<unset>} (UTC)

    psql -h ${DB_HOST} -p ${DB_PORT} -U ${DB_USER} -d ${DB_DATABASE}

EOF

if [[ "$RUN_APP" != true ]]; then
	echo "Dependencies are up. Re-export the variables above before ./gradlew run."
	exit 0
fi

cd "$REPO_ROOT"
exec ./gradlew run

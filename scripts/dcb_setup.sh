#!/bin/bash
#
# Load configuration into a DCB environment.
#
# Two independent choices, in this order:
#
#   1. --profile  WHERE it goes.  scripts/profiles/<name>.env
#                 Target URL, Keycloak credentials, API keys. NOT committed.
#   2. --config   WHAT gets sent. A named selection of library groups within
#                 the bundle: all, folio, alma, sierra, polaris, koha, ...
#                 This is the replacement for the old INSTALL_ALMA/INSTALL_KOHA
#                 flags.
#
# The configuration itself lives in scripts/config/<bundle>/<group>/, and is
# committed. Bundle files reference ${VARIABLES} resolved from the profile,
# which is what keeps secrets out of the repo while the config stays in it.
#
#   ./scripts/dcb_setup.sh --profile local --config all
#   ./scripts/dcb_setup.sh --profile local --config folio
#   ./scripts/dcb_setup.sh --profile local --config alma --dry-run
#   ./scripts/dcb_setup.sh --profile local --config all --only 20-agencies
#   ./scripts/dcb_setup.sh --list
#
# --bundle selects which bundle the config profiles come from (default:
# private-local if it exists, otherwise example).
#
# See docs/local-development.md for the full explanation.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE_DIR="${REPO_ROOT}/scripts/profiles"
CONFIG_DIR="${REPO_ROOT}/scripts/config"

PROFILE="${DCB_PROFILE:-}"
BUNDLE="${DCB_BUNDLE:-}"
CONFIG="${DCB_CONFIG:-all}"
DRY_RUN=false
ONLY=""

die() { echo "ERROR: $*" >&2; exit 1; }

usage() {
	sed -n '3,29p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

# Groups named zz-* are shared: applied for every config profile, and last,
# because the consortium and its membership depend on the libraries existing.
is_shared_group() { [[ "$(basename "$1")" == zz-* ]]; }

config_profiles_file() { echo "${CONFIG_DIR}/${BUNDLE}/profiles.conf"; }

list_config_profiles() {
	local conf; conf="$(config_profiles_file)"

	if [[ -f "$conf" ]]; then
		echo "  all"
		grep -vE '^[[:space:]]*(#|$)' "$conf" | cut -d= -f1 | sed 's/^/  /' || true
	else
		echo "  all"
		for d in "${CONFIG_DIR}/${BUNDLE}"/*/; do
			[[ -d "$d" ]] || continue
			is_shared_group "$d" && continue
			echo "  $(basename "$d")"
		done
	fi
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--profile) PROFILE="${2:-}"; shift 2 ;;
		--profile=*) PROFILE="${1#*=}"; shift ;;
		--bundle) BUNDLE="${2:-}"; shift 2 ;;
		--bundle=*) BUNDLE="${1#*=}"; shift ;;
		--config) CONFIG="${2:-}"; shift 2 ;;
		--config=*) CONFIG="${1#*=}"; shift ;;
		--only) ONLY="${2:-}"; shift 2 ;;
		--only=*) ONLY="${1#*=}"; shift ;;
		--dry-run) DRY_RUN=true; shift ;;
		--list)
			echo "1. Environment profiles  --profile   (${PROFILE_DIR})"
			for f in "$PROFILE_DIR"/*.env; do
				[[ -e "$f" ]] || { echo "  <none -- copy example.env.template>"; break; }
				echo "  $(basename "$f" .env)"
			done

			echo
			echo "Bundles  --bundle  (${CONFIG_DIR})"
			for d in "$CONFIG_DIR"/*/; do
				[[ -d "$d" ]] || { echo "  <none>"; break; }
				echo "  $(basename "$d")"
			done

			for d in "$CONFIG_DIR"/*/; do
				[[ -d "$d" ]] || break
				BUNDLE="$(basename "$d")"
				echo
				echo "2. Config profiles  --config  (bundle: ${BUNDLE})"
				list_config_profiles
			done
			exit 0
			;;
		-h|--help) usage; exit 0 ;;
		*) die "Unknown option: $1 (try --help)" ;;
	esac
done

command -v curl >/dev/null || die "curl is required"
command -v jq   >/dev/null || die "jq is required"

# --- Profile -----------------------------------------------------------------

[[ -n "$PROFILE" ]] || die "No profile given. Use --profile <name>, or --list to see what exists.
Create one by copying ${PROFILE_DIR}/example.env.template."

PROFILE_FILE="${PROFILE_DIR}/${PROFILE}.env"
[[ -f "$PROFILE_FILE" ]] || die "Profile not found: ${PROFILE_FILE}
Copy ${PROFILE_DIR}/example.env.template to get started."

# shellcheck disable=SC1090
set -a; source "$PROFILE_FILE"; set +a

# A profile saved with CRLF line endings leaves a carriage return on every
# value, which turns the target into "http://host:8080\r" and fails in ways that
# look nothing like the cause. Strip them rather than diagnose them later.
while IFS='=' read -r _key _; do
	[[ "$_key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
	printf -v "$_key" '%s' "${!_key//$'\r'/}"
done < "$PROFILE_FILE"

for required in DCB_TARGET KEYCLOAK_BASE KEYCLOAK_CLIENT KEYCLOAK_SECRET DCB_ADMIN_USER DCB_ADMIN_PASS; do
	[[ -n "${!required:-}" ]] || die "Profile ${PROFILE} does not set ${required}"
done

# --- Bundle ------------------------------------------------------------------

if [[ -z "$BUNDLE" ]]; then
	if [[ -d "${CONFIG_DIR}/private-local" ]]; then
		BUNDLE="private-local"
	else
		BUNDLE="example"
	fi
fi

BUNDLE_DIR="${CONFIG_DIR}/${BUNDLE}"
[[ -d "$BUNDLE_DIR" ]] || die "Bundle not found: ${BUNDLE_DIR}"

shopt -s nullglob

# --- Config profile ----------------------------------------------------------
#
# A config profile names a set of library groups -- the replacement for the old
# INSTALL_FOLIO / INSTALL_ALMA flags. Resolution order:
#   1. a named entry in <bundle>/profiles.conf  (folio=folio  mobius=polaris,folio)
#   2. "all", meaning every non-shared group
#   3. a bare group directory name
# Shared (zz-*) groups are always included, and always last.

# NOT named GROUPS: that is a read-only bash builtin holding the current user's
# group IDs, and assigning to it fails silently.
SELECTED_GROUPS=()

resolve_config_groups() {
	local conf spec entry d
	conf="$(config_profiles_file)"

	if [[ -f "$conf" ]]; then
		# `|| true` matters: no match is the normal case for built-in names like
		# "all", and under `set -o pipefail` grep's exit 1 would otherwise abort
		# the whole script silently.
		spec="$(grep -E "^[[:space:]]*${CONFIG}[[:space:]]*=" "$conf" \
			| head -n1 | cut -d= -f2- | tr -d '[:space:]' || true)"
	fi

	if [[ -z "${spec:-}" ]]; then
		if [[ "$CONFIG" == "all" ]]; then
			spec="*"
		elif [[ -d "${BUNDLE_DIR}/${CONFIG}" ]]; then
			spec="$CONFIG"
		else
			die "Unknown config profile '${CONFIG}' for bundle '${BUNDLE}'.
Available:
$(list_config_profiles)
Add one by creating a group directory, or an entry in $(config_profiles_file)."
		fi
	fi

	if [[ "$spec" == "*" ]]; then
		for d in "$BUNDLE_DIR"/*/; do
			if ! is_shared_group "$d"; then
				SELECTED_GROUPS+=("$(basename "$d")")
			fi
		done
	else
		while IFS= read -r entry; do
			[[ -n "$entry" ]] || continue
			[[ -d "${BUNDLE_DIR}/${entry}" ]] \
				|| die "Config profile '${CONFIG}' names group '${entry}', which does not exist in bundle '${BUNDLE}'."
			SELECTED_GROUPS+=("$entry")
		done < <(echo "$spec" | tr ',' '\n')
	fi

	[[ ${#SELECTED_GROUPS[@]} -gt 0 ]] \
		|| die "Config profile '${CONFIG}' selected no groups in bundle '${BUNDLE}'."

	# Plain `if`, not `cond && action`: an AND-list whose condition fails returns
	# 1, and as the last statement in the loop that becomes the function's exit
	# status, which `set -e` treats as fatal. A bundle with no zz-* group at all
	# would otherwise abort the script with no output whatsoever.
	for d in "$BUNDLE_DIR"/*/; do
		if is_shared_group "$d"; then
			SELECTED_GROUPS+=("$(basename "$d")")
		fi
	done
}

resolve_config_groups

# --- Templating --------------------------------------------------------------
#
# Bundle files reference ${VAR}. Every referenced variable must be set by the
# profile -- substituting an unset one would silently POST an empty API key and
# leave you debugging a 401 later, so this is a hard failure.

vars_referenced_by() {
	grep -oE '\$\{[A-Za-z_][A-Za-z0-9_]*\}' "$1" 2>/dev/null \
		| sed 's/^\${//; s/}$//' | sort -u
}

# Validation happens here, in the main shell, and NOT inside render(). render()
# is only ever called in a command substitution, so a `die` there would kill the
# subshell and let the run continue -- POSTing an empty API key and reporting
# success. Everything is checked before anything is sent.
validate_bundle() {
	local file var missing_report="" step group dir

	for group in "${SELECTED_GROUPS[@]}"; do
		for dir in "${BUNDLE_DIR}/${group}"/*/; do
			step="$(basename "$dir")"
			[[ -z "$ONLY" || "$step" == "$ONLY" ]] || continue

			for file in "$dir"*.json "$dir"*.graphql; do
				[[ -f "$file" ]] || continue

				local missing=()
				while IFS= read -r var; do
					[[ -n "$var" ]] || continue
					[[ -n "${!var:-}" ]] || missing+=("$var")
				done < <(vars_referenced_by "$file")

				if [[ ${#missing[@]} -gt 0 ]]; then
					missing_report+="  ${group}/${step}/$(basename "$file"): ${missing[*]}"$'\n'
				fi
			done
		done
	done

	if [[ -n "$missing_report" ]]; then
		die "Profile '${PROFILE}' does not set variables referenced by bundle '${BUNDLE}':
${missing_report}
Add them to ${PROFILE_FILE}."
	fi
}

render() {
	local file="$1" content var

	content="$(cat "$file")"

	while IFS= read -r var; do
		[[ -n "$var" ]] || continue
		content="${content//\$\{$var\}/${!var}}"
	done < <(vars_referenced_by "$file")

	printf '%s' "$content"
}

# --- Validation --------------------------------------------------------------

validate_bundle

# --- Authentication ----------------------------------------------------------

if [[ "$DRY_RUN" == true ]]; then
	echo "==> Dry run: skipping login to ${DCB_TARGET}"
	TOKEN="dry-run"
else
	echo "==> Logging in to ${DCB_TARGET} as ${DCB_ADMIN_USER}"
	TOKEN="$(curl -s \
		-d "client_id=${KEYCLOAK_CLIENT}" \
		-d "client_secret=${KEYCLOAK_SECRET}" \
		-d "username=${DCB_ADMIN_USER}" \
		-d "password=${DCB_ADMIN_PASS}" \
		-d "grant_type=password" \
		"${KEYCLOAK_BASE}/protocol/openid-connect/token" | jq -r '.access_token // empty')"

	[[ -n "$TOKEN" ]] || die "Login failed -- no access token returned. Check the Keycloak settings in profile '${PROFILE}'."
	echo "    ok"
fi

# --- Posting -----------------------------------------------------------------

FAILURES=0
APPLIED=0

# Reports the HTTP status rather than assuming success. The old script piped
# every response straight to stdout, so a 401 or 409 looked identical to a 201.
post_json() {
	local label="$1" endpoint="$2" body="$3" status

	if [[ "$DRY_RUN" == true ]]; then
		echo "    [dry-run] POST ${endpoint}  ${label}"
		return 0
	fi

	status="$(curl -s -o /tmp/dcb_setup_response -w '%{http_code}' \
		-X POST "${DCB_TARGET}${endpoint}" \
		-H "Content-Type: application/json" \
		-H "Authorization: Bearer ${TOKEN}" \
		--data-binary "$body")"

	report "$label" "$status"
}

post_upload() {
	local label="$1" endpoint="$2" file="$3" status
	shift 3

	if [[ "$DRY_RUN" == true ]]; then
		echo "    [dry-run] POST ${endpoint}  ${label}  ($*)"
		return 0
	fi

	local form=()
	for field in "$@"; do form+=(-F "$field"); done

	status="$(curl -s -o /tmp/dcb_setup_response -w '%{http_code}' \
		-X POST "${DCB_TARGET}${endpoint}" \
		-H "Authorization: Bearer ${TOKEN}" \
		-F "file=@${file}" \
		"${form[@]}")"

	report "$label" "$status"
}

# Upload steps take the host LMS code from the filename, so SLOUC.tsv uploads
# against code=SLOUC. Strips either supported extension.
code_from_filename() {
	local base; base="$(basename "$1")"
	base="${base%.csv}"
	base="${base%.tsv}"
	printf '%s' "$base"
}

graphql_query() {
	curl -s -X POST "${DCB_TARGET}/graphql" \
		-H "Content-Type: application/json" \
		-H "Authorization: Bearer ${TOKEN}" \
		--data-binary "$1"
}

# Joining libraries to a consortium group is the one genuinely stateful step:
# the group ID is only known at runtime. The old script captured it from the
# create-group response, which meant re-running it was never safe. Resolving the
# group by code instead makes this idempotent.
apply_group_membership() {
	local spec="$1" group_code library_query group_id library_ids body response
	local added=0 already=0 failed=0 total=0 first_error=""

	group_code="$(jq -r '.groupCode' "$spec")"
	library_query="$(jq -r '.libraryQuery // "agencyCode:*"' "$spec")"

	if [[ "$DRY_RUN" == true ]]; then
		echo "    [dry-run] resolve group '${group_code}', add libraries matching '${library_query}'"
		return 0
	fi

	body="$(jq -n --arg q "code:${group_code}" \
		'{query: "query($q: String) { libraryGroups(query: $q, pagesize: 100) { content { id, code } } }", variables: {q: $q}}')"
	response="$(graphql_query "$body")"
	# tr -d '\r': jq on Windows writes in text mode, so `jq -r` emits CRLF. The
	# stray CR survives `read -r` and makes a 36-char UUID 37 chars long, which
	# the server rejects with "UUID string too large".
	group_id="$(jq -r --arg c "$group_code" \
		'.data.libraryGroups.content[]? | select(.code == $c) | .id' <<< "$response" \
		| tr -d '\r' | head -n1)"

	if [[ -z "$group_id" || "$group_id" == "null" ]]; then
		echo "    FAIL group '${group_code}' not found. Response: $(head -c 300 <<< "$response")"
		FAILURES=$((FAILURES + 1))
		return 0
	fi

	body="$(jq -n --arg q "$library_query" \
		'{query: "query($q: String) { libraries(query: $q, pagesize: 100) { content { id } } }", variables: {q: $q}}')"
	response="$(graphql_query "$body")"
	library_ids="$(jq -r '.data.libraries.content[]?.id' <<< "$response" | tr -d '\r')"

	if [[ -z "$library_ids" ]]; then
		echo "    FAIL no libraries matched '${library_query}'. Response: $(head -c 300 <<< "$response")"
		FAILURES=$((FAILURES + 1))
		return 0
	fi

	while IFS= read -r library_id; do
		library_id="${library_id//$'\r'/}"
		[[ -n "$library_id" ]] || continue
		total=$((total + 1))

		# library/libraryGroup are ID! in the schema. Declaring the variables as
		# String makes GraphQL reject the whole mutation on type-checking, which
		# is silent unless you actually read .errors -- exactly how this managed
		# to report success while joining nothing.
		body="$(jq -n --arg l "$library_id" --arg g "$group_id" \
			'{query: "mutation($l: ID!, $g: ID!) { addLibraryToGroup(input: {library: $l, libraryGroup: $g}) { id } }", variables: {l: $l, g: $g}}')"
		response="$(graphql_query "$body")"

		if jq -e '.data.addLibraryToGroup.id' >/dev/null 2>&1 <<< "$response"; then
			added=$((added + 1))
			continue
		fi

		local message
		message="$(jq -r '.errors[0].message // "no data and no error message"' <<< "$response")"

		# Re-running a bundle re-adds libraries that are already members.
		if grep -qiE 'already|duplicate|constraint' <<< "$message"; then
			already=$((already + 1))
			continue
		fi

		failed=$((failed + 1))
		[[ -n "$first_error" ]] || first_error="$message"
	done <<< "$library_ids"

	if [[ "$failed" -gt 0 ]]; then
		echo "    FAIL ${group_code}: ${added}/${total} joined, ${already} already members, ${failed} failed -- ${first_error}"
		FAILURES=$((FAILURES + 1))
		return 0
	fi

	echo "    ok   ${group_code}: ${added}/${total} joined$([[ "$already" -gt 0 ]] && echo ", ${already} already members")"
	APPLIED=$((APPLIED + 1))
}

report() {
	local label="$1" status="$2" detail

	if [[ "$status" =~ ^2 ]]; then
		# GraphQL answers 200 even when the mutation failed.
		if detail="$(jq -e -r '.errors[0].message' /tmp/dcb_setup_response 2>/dev/null)"; then
			echo "    FAIL ${label} (200 but GraphQL error: ${detail})"
			FAILURES=$((FAILURES + 1))
			return 0
		fi
		echo "    ok   ${label} (${status})"
		APPLIED=$((APPLIED + 1))
	elif [[ "$status" == "409" ]]; then
		echo "    skip ${label} (409 already exists)"
	else
		detail="$(jq -r '.message // .error // empty' /tmp/dcb_setup_response 2>/dev/null || true)"
		[[ -n "$detail" ]] || detail="$(head -c 200 /tmp/dcb_setup_response 2>/dev/null || true)"
		echo "    FAIL ${label} (${status}) ${detail}"
		FAILURES=$((FAILURES + 1))
	fi
}

# --- Walk the bundle ---------------------------------------------------------
#
# Directories are applied in lexical order, so the NN- prefix controls
# dependency ordering (host LMS before agencies before libraries). The suffix
# after the prefix selects how the files are sent.

echo "==> Applying bundle '${BUNDLE}', config '${CONFIG}' to ${DCB_TARGET}"
echo "    groups: ${SELECTED_GROUPS[*]}"

for group in "${SELECTED_GROUPS[@]}"; do
echo "  [${group}]"
for dir in "${BUNDLE_DIR}/${group}"/*/; do
	step="$(basename "$dir")"
	kind="${step#*-}"

	if [[ -n "$ONLY" && "$step" != "$ONLY" ]]; then
		continue
	fi

	echo "  ${step}"

	case "$kind" in
		hostlms)
			for f in "$dir"*.json; do
				post_json "$(basename "$f")" "/hostlmss" "$(render "$f")"
			done
			;;
		agencies)
			for f in "$dir"*.json; do
				post_json "$(basename "$f")" "/agencies" "$(render "$f")"
			done
			;;
		object-rulesets)
			# The group is named object-rulesets; the endpoint is /object-rules.
			# ObjectRulesetController is @Controller("/object-rules")
			for f in "$dir"*.json; do
				post_json "$(basename "$f")" "/object-rules" "$(render "$f")"
			done
			;;
		graphql)
			# A .graphql file is the mutation itself, written normally over as
			# many lines as it takes; jq does the JSON escaping. The old script
			# hand-escaped every mutation onto one line, which is why they were
			# effectively read-only. A .json file is a ready-made request body,
			# for when you need `variables` too.
			for f in "$dir"*.graphql; do
				post_json "$(basename "$f")" "/graphql" \
					"$(render "$f" | jq -Rs '{query: .}')"
			done
			for f in "$dir"*.json; do
				post_json "$(basename "$f")" "/graphql" "$(render "$f")"
			done
			;;
		locations)
			for f in "$dir"*.json; do
				post_json "$(basename "$f")" "/locations" "$(render "$f")"
			done
			;;
		locations-upload)
			# Host LMS code comes from the filename: INTEGRATION_COLLEGE.tsv
			for f in "$dir"*.csv "$dir"*.tsv; do
				post_upload "$(basename "$f")" "/locations/upload" "$f" \
					"type=Locations" \
					"code=$(code_from_filename "$f")" \
					"reason=${UPLOAD_REASON:-dcb_setup.sh}"
			done
			;;
		mappings-upload)
			for f in "$dir"*.csv "$dir"*.tsv; do
				post_upload "$(basename "$f")" "/uploadedMappings/upload" "$f" \
					"type=Reference value mappings" \
					"category=all" \
					"code=$(code_from_filename "$f")" \
					"reason=${UPLOAD_REASON:-dcb_setup.sh}"
			done
			;;
		numeric-mappings-upload)
			for f in "$dir"*.csv "$dir"*.tsv; do
				post_upload "$(basename "$f")" "/uploadedMappings/upload" "$f" \
					"type=Numeric range mappings" \
					"category=all" \
					"code=$(code_from_filename "$f")" \
					"reason=${UPLOAD_REASON:-dcb_setup.sh}"
			done
			;;
		group-membership)
			for f in "$dir"*.json; do
				apply_group_membership "$f"
			done
			;;
		*)
			echo "    WARNING: unrecognised step kind '${kind}' -- skipping." >&2
			echo "    Directory names must be NN-<kind>, kind one of: hostlms, agencies," >&2
			echo "    locations, graphql, object-rulesets, locations-upload," >&2
			echo "    mappings-upload, numeric-mappings-upload, group-membership." >&2
			;;
	esac
done
done

rm -f /tmp/dcb_setup_response

echo
if [[ "$FAILURES" -gt 0 ]]; then
	echo "==> Finished with ${FAILURES} failure(s), ${APPLIED} applied."
	exit 1
fi
echo "==> Done. ${APPLIED} item(s) applied."

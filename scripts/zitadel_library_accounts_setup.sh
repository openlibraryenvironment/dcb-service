#!/usr/bin/env bash
#
# Configure a LOCAL DEV Zitadel for library account provisioning, and measure what its
# permission model can contain. A SPIKE: DCB implements no Zitadel provider, and what this
# measures - the answer differs from Keycloak's - is operational:identity-provider-setup.adoc
# Part 3.
#
#   docker compose --profile zitadel up -d zitadel mailpit
#   ./scripts/zitadel_library_accounts_setup.sh
#
# Idempotent. DEV ONLY - it creates users with known passwords, and the final probe
# deliberately attempts a privilege escalation. Never point it at anything shared.

set -euo pipefail

ZITADEL_URL="${ZITADEL_URL:-http://localhost:8181}"

# Written by the setup job into the shared volume. Read out of the container rather than
# copied to the host: it is a credential, and the volume is where it already lives.
#
# COMPOSE_DIR is where docker-compose.yml lives - the OpenRS workspace, one level above
# this repository. It has to be explicit because this script is run from dcb-service,
# which has no compose file of its own.
COMPOSE_DIR="${COMPOSE_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
PAT_CONTAINER="${PAT_CONTAINER:-zitadel}"
PAT_PATH="${PAT_PATH:-/zitadel/bootstrap/admin.pat}"

CONSORTIUM_ORG="${CONSORTIUM_ORG:-openrs}"
LIBRARY_ORG="${LIBRARY_ORG:-openrs-libraries}"

CONSORTIUM_PROJECT="${CONSORTIUM_PROJECT:-dcb-consortium}"
LIBRARY_PROJECT="${LIBRARY_PROJECT:-dcb-libraries}"

PROVISIONING_USER="${PROVISIONING_USER:-dcb-provisioning}"
TEST_AGENCY="${TEST_AGENCY:-alpha}"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
note() { printf '  %s\n' "$*"; }
fail() { printf '\n\033[1;31m%s\033[0m\n' "$*" >&2; exit 1; }

jqp() { python -c "$1" "${@:2}"; }

# ---------------------------------------------------------------------------
# The bootstrap PAT
# ---------------------------------------------------------------------------
say "Reading the bootstrap PAT from ${PAT_CONTAINER}:${PAT_PATH}"

# docker cp rather than `exec cat`: the Zitadel image is distroless and carries neither a
# shell nor cat, so exec can only ever run the one binary in it.
pat_container_id="$(docker compose --project-directory "${COMPOSE_DIR}" ps -q "${PAT_CONTAINER}")"
[ -n "${pat_container_id}" ] ||
  fail "No ${PAT_CONTAINER} container. Start it with:
  docker compose --profile zitadel up -d zitadel mailpit"

pat_file="$(mktemp)"
trap 'rm -f "${pat_file}"' EXIT

docker cp "${pat_container_id}:${PAT_PATH}" "${pat_file}" >/dev/null 2>&1 ||
  fail "Could not read ${PAT_PATH}. FIRSTINSTANCE_* is read on a genuinely first start
  only: if the database already carried a Zitadel schema, setup tried to UPGRADE it and no
  PAT was ever written. Drop the database, remove the zitadel_bootstrap volume, recreate."

pat="$(tr -d '\r\n' < "${pat_file}")"
[ -n "${pat}" ] || fail "The PAT file is empty."

auth=(-H "Authorization: Bearer ${pat}" -H "Content-Type: application/json")
note "bootstrap account holds IAM_OWNER"

# Every Management API call acts on the org in x-zitadel-orgid, falling back to the
# authenticated user's own. Nothing below relies on that fallback: each call names its org,
# because the entire containment argument here is which org a call landed in.
mgmt() {
  local org="$1" method="$2" path="$3" body="${4:-}"
  local args=(-sS "${auth[@]}" -H "x-zitadel-orgid: ${org}" -X "${method}"
    "${ZITADEL_URL}${path}")
  # Not `[ -n "$body" ] && args+=(...)`: that returns 1 on an empty body, and under
  # `set -e` the script exits instead of sending a bodyless request.
  if [ -n "${body}" ]; then
    args+=(-d "${body}")
  fi
  curl "${args[@]}"
}

# Same, reporting only the status code - for the probe, where the code IS the result.
mgmt_code() {
  local org="$1" method="$2" path="$3" body="${4:-}"
  local args=(-s -o /dev/null -w '%{http_code}' "${auth[@]}"
    -H "x-zitadel-orgid: ${org}" -X "${method}" "${ZITADEL_URL}${path}")
  [ -n "${body}" ] && args+=(-d "${body}")
  curl "${args[@]}"
}

# ---------------------------------------------------------------------------
# Organisations
# ---------------------------------------------------------------------------
say "Organisations"

list_orgs() {
  curl -sS "${auth[@]}" -X POST "${ZITADEL_URL}/v2/organizations/_search" -d '{}' |
    jqp 'import json,sys; print(json.dumps(json.load(sys.stdin).get("result", [])))'
}

org_id_by_name() {
  jqp 'import json,sys; print(next((o["id"] for o in json.loads(sys.argv[1])
       if o.get("name") == sys.argv[2]), ""))' "$1" "$2"
}

orgs="$(list_orgs)"

consortium_org_id="$(org_id_by_name "${orgs}" "${CONSORTIUM_ORG}")"
[ -n "${consortium_org_id}" ] ||
  fail "No organisation named ${CONSORTIUM_ORG}. That is the one the instance bootstraps
  with, so either ZITADEL_FIRSTINSTANCE_ORG_NAME differs or this is not our instance."
note "${CONSORTIUM_ORG} (consortium) ${consortium_org_id}"

library_org_id="$(org_id_by_name "${orgs}" "${LIBRARY_ORG}")"

if [ -z "${library_org_id}" ]; then
  library_org_id="$(curl -sS "${auth[@]}" -X POST "${ZITADEL_URL}/v2/organizations" \
    -d "{\"name\":\"${LIBRARY_ORG}\"}" |
    jqp 'import json,sys; print(json.load(sys.stdin).get("organizationId", ""))')"
  [ -n "${library_org_id}" ] || fail "Could not create the ${LIBRARY_ORG} organisation."
  note "${LIBRARY_ORG} (library accounts) ${library_org_id} - created"
else
  note "${LIBRARY_ORG} (library accounts) ${library_org_id}"
fi

# ---------------------------------------------------------------------------
# Projects and roles
#
# The split is the containment. Four roles in one project would leave the provisioning
# account able to grant ADMIN, because no permission here says "these roles only".
# ---------------------------------------------------------------------------
say "Projects and roles"

upsert_project() {
  local org="$1" name="$2" id

  id="$(mgmt "${org}" POST /management/v1/projects/_search '{}' |
    jqp 'import json,sys; print(next((p["id"] for p in json.load(sys.stdin).get("result", [])
         if p.get("name") == sys.argv[1]), ""))' "${name}")"

  if [ -z "${id}" ]; then
    # projectRoleAssertion puts this project's roles in the token without the client
    # having to ask for them by scope. dcb-service reads roles from the claim, so a
    # project that does not assert them is a project whose roles DCB never sees.
    id="$(mgmt "${org}" POST /management/v1/projects \
      "{\"name\":\"${name}\",\"projectRoleAssertion\":true,\"projectRoleCheck\":true}" |
      jqp 'import json,sys; print(json.load(sys.stdin).get("id", ""))')"
    [ -n "${id}" ] || fail "Could not create project ${name}."
  fi

  printf '%s' "${id}"
}

add_roles() {
  local org="$1" project="$2"; shift 2
  local body
  body="$(jqp 'import json,sys; print(json.dumps({"roles": [
      {"key": k, "displayName": k, "group": "dcb"} for k in sys.argv[1:]]}))' "$@")"

  # Re-adding an existing role is an error, not a no-op, so a second run is expected to
  # be refused here. The roles are read back below and that is what the check uses.
  mgmt "${org}" POST "/management/v1/projects/${project}/roles/_bulk" "${body}" >/dev/null 2>&1 || true

  local present
  present="$(mgmt "${org}" POST "/management/v1/projects/${project}/roles/_search" '{}' |
    jqp 'import json,sys; print(" ".join(sorted(r["key"] for r in json.load(sys.stdin).get("result", []))))')"

  for key in "$@"; do
    case " ${present} " in
      *" ${key} "*) ;;
      *) fail "Role ${key} is missing from project ${project} after the bulk add." ;;
    esac
  done

  note "${present}"
}

consortium_project_id="$(upsert_project "${consortium_org_id}" "${CONSORTIUM_PROJECT}")"
note "${CONSORTIUM_PROJECT} ${consortium_project_id} (in ${CONSORTIUM_ORG})"
add_roles "${consortium_org_id}" "${consortium_project_id}" ADMIN CONSORTIUM_ADMIN

library_project_id="$(upsert_project "${library_org_id}" "${LIBRARY_PROJECT}")"
note "${LIBRARY_PROJECT} ${library_project_id} (in ${LIBRARY_ORG})"
add_roles "${library_org_id}" "${library_project_id}" LIBRARY_ADMIN LIBRARY_READ_ONLY

# ---------------------------------------------------------------------------
# The provisioning service user
# ---------------------------------------------------------------------------
say "Provisioning service user"

prov_user_id="$(mgmt "${library_org_id}" POST /management/v1/users/_search \
  "{\"queries\":[{\"userNameQuery\":{\"userName\":\"${PROVISIONING_USER}\",\"method\":\"TEXT_QUERY_METHOD_EQUALS\"}}]}" |
  jqp 'import json,sys; r=json.load(sys.stdin).get("result", []); print(r[0]["id"] if r else "")')"

if [ -z "${prov_user_id}" ]; then
  prov_user_id="$(mgmt "${library_org_id}" POST /management/v1/users/machine \
    "{\"userName\":\"${PROVISIONING_USER}\",\"name\":\"DCB provisioning\",\"accessTokenType\":\"ACCESS_TOKEN_TYPE_BEARER\"}" |
    jqp 'import json,sys; print(json.load(sys.stdin).get("userId", ""))')"
  [ -n "${prov_user_id}" ] || fail "Could not create the ${PROVISIONING_USER} machine user."
  note "created ${prov_user_id}"
else
  note "exists ${prov_user_id}"
fi

# ORG_USER_MANAGER in the LIBRARY org only. This is the strongest containment Zitadel
# offers: not "may grant these two roles", but "may act in this organisation".
mgmt "${library_org_id}" POST /management/v1/orgs/me/members \
  "{\"userId\":\"${prov_user_id}\",\"roles\":[\"ORG_USER_MANAGER\"]}" >/dev/null 2>&1 || true

member_roles="$(mgmt "${library_org_id}" POST /management/v1/orgs/me/members/_search '{}' |
  jqp 'import json,sys; print(" ".join(sorted(
       r for m in json.load(sys.stdin).get("result", [])
       if m.get("userId") == sys.argv[1] for r in m.get("roles", []))))' "${prov_user_id}")"

[ -n "${member_roles}" ] ||
  fail "${PROVISIONING_USER} is not a member of ${LIBRARY_ORG}."
note "member of ${LIBRARY_ORG}: ${member_roles}"

prov_pat="$(mgmt "${library_org_id}" POST "/management/v1/users/${prov_user_id}/pats" \
  '{"expirationDate":"2099-01-01T00:00:00Z"}' |
  jqp 'import json,sys; print(json.load(sys.stdin).get("token", ""))')"

[ -n "${prov_pat}" ] || fail "Could not mint a PAT for ${PROVISIONING_USER}."
prov_auth=(-H "Authorization: Bearer ${prov_pat}" -H "Content-Type: application/json")
note "PAT issued"

# ---------------------------------------------------------------------------
# A library account, provisioned the way the adapter would have to
# ---------------------------------------------------------------------------
say "Test library account"

# CreateUser cannot create a user already deactivated, so the Keycloak ordering
# (create disabled -> grant -> enable) becomes create -> deactivate -> grant -> activate.
# The safety property survives: a failure between steps leaves a deactivated account.
test_email="library-readonly@example.invalid"

test_user_id="$(curl -sS "${prov_auth[@]}" -H "x-zitadel-orgid: ${library_org_id}" \
  -X POST "${ZITADEL_URL}/v2/users/human" -d "$(jqp 'import json,sys; print(json.dumps({
    "username": sys.argv[1],
    "organization": {"orgId": sys.argv[2]},
    "profile": {"givenName": "Library", "familyName": "Tester"},
    "email": {"email": sys.argv[1], "isVerified": False}}))' "${test_email}" "${library_org_id}")" |
  jqp 'import json,sys; print(json.load(sys.stdin).get("userId", ""))')"

if [ -z "${test_user_id}" ]; then
  test_user_id="$(mgmt "${library_org_id}" POST /management/v1/users/_search \
    "{\"queries\":[{\"userNameQuery\":{\"userName\":\"${test_email}\",\"method\":\"TEXT_QUERY_METHOD_EQUALS\"}}]}" |
    jqp 'import json,sys; r=json.load(sys.stdin).get("result", []); print(r[0]["id"] if r else "")')"
fi

[ -n "${test_user_id}" ] || fail "Could not create or find the test library account."

curl -sS "${prov_auth[@]}" -H "x-zitadel-orgid: ${library_org_id}" \
  -X POST "${ZITADEL_URL}/v2/users/${test_user_id}/deactivate" >/dev/null

# The agency claim. Zitadel does NOT surface metadata the way a Keycloak mapper does: it
# arrives base64 encoded under urn:zitadel:iam:user:metadata, and only when the client
# asks for that scope. An adapter that sets this and assumes `code` appears as a plain
# claim will ship an account that signs in to an empty application.
mgmt "${library_org_id}" POST "/management/v1/users/${test_user_id}/metadata/code" \
  "{\"value\":\"$(printf '%s' "${TEST_AGENCY}" | base64)\"}" >/dev/null

curl -sS "${prov_auth[@]}" -H "x-zitadel-orgid: ${library_org_id}" \
  -X POST "${ZITADEL_URL}/management/v1/users/${test_user_id}/grants" \
  -d "{\"projectId\":\"${library_project_id}\",\"roleKeys\":[\"LIBRARY_READ_ONLY\"]}" >/dev/null 2>&1 || true

curl -sS "${prov_auth[@]}" -H "x-zitadel-orgid: ${library_org_id}" \
  -X POST "${ZITADEL_URL}/v2/users/${test_user_id}/reactivate" >/dev/null

curl -sS "${prov_auth[@]}" -H "x-zitadel-orgid: ${library_org_id}" \
  -X POST "${ZITADEL_URL}/v2/users/${test_user_id}/invite_code" \
  -d '{"sendCode":{}}' >/dev/null 2>&1 ||
  note "invite email refused - check mailpit is up"

note "${test_email} - LIBRARY_READ_ONLY, code=${TEST_AGENCY}, invite sent to mailpit"

# ---------------------------------------------------------------------------
# PROVE IT. Runbook Part 5.3, executed against the org boundary.
#
# Zitadel refuses out-of-scope work with 404 "membership not found (AUTHZ-cdgFk)", not 403
# - it does not admit the resource exists. A bare 404 therefore proves nothing on its own:
# a typo in the path returns one too, and an earlier revision of this script read exactly
# that as containment. So every refusal here is paired with the SAME call made by the
# IAM_OWNER bootstrap account, which must succeed. Denied is refused-where-admin-succeeds.
# ---------------------------------------------------------------------------
say "Proving containment"

# The subject lives in the CONSORTIUM org, so this is same-org escalation with no
# cross-org confound: the only thing standing between the service user and ADMIN is its
# own membership.
escalation_subject_id="$(curl -sS "${auth[@]}" -H "x-zitadel-orgid: ${consortium_org_id}" \
  -X POST "${ZITADEL_URL}/v2/users/human" -d "$(jqp 'import json,sys; print(json.dumps({
    "username": "escalation-probe@invalid",
    "organization": {"orgId": sys.argv[1]},
    "profile": {"givenName": "Escalation", "familyName": "Probe"},
    "email": {"email": "escalation-probe@invalid", "isVerified": True}}))' "${consortium_org_id}")" |
  jqp 'import json,sys; print(json.load(sys.stdin).get("userId", ""))')"

[ -n "${escalation_subject_id}" ] || fail "Could not create the escalation probe subject."

admin_grant="{\"projectId\":\"${consortium_project_id}\",\"roleKeys\":[\"ADMIN\"]}"

code_and_message() {
  curl -s -w '\n%{http_code}' "$@" | jqp 'import json,sys
body, _, code = sys.stdin.read().rpartition("\n")
try: message = json.loads(body).get("message", "")
except Exception: message = ""
print(code.strip(), message.strip())'
}

control="$(code_and_message "${auth[@]}" -H "x-zitadel-orgid: ${consortium_org_id}" \
  -X POST "${ZITADEL_URL}/management/v1/users/${escalation_subject_id}/grants" -d "${admin_grant}")"

refused="$(code_and_message "${prov_auth[@]}" -H "x-zitadel-orgid: ${consortium_org_id}" \
  -X POST "${ZITADEL_URL}/management/v1/users/${escalation_subject_id}/grants" -d "${admin_grant}")"

# A user this run created, so the positive case is a real grant rather than a 409 telling
# us only that one already existed.
fresh_id="$(curl -sS "${prov_auth[@]}" -H "x-zitadel-orgid: ${library_org_id}" \
  -X POST "${ZITADEL_URL}/v2/users/human" -d "$(jqp 'import json,sys; print(json.dumps({
    "username": "grant-probe@invalid",
    "organization": {"orgId": sys.argv[1]},
    "profile": {"givenName": "Grant", "familyName": "Probe"},
    "email": {"email": "grant-probe@invalid", "isVerified": True}}))' "${library_org_id}")" |
  jqp 'import json,sys; print(json.load(sys.stdin).get("userId", ""))')"

[ -n "${fresh_id}" ] ||
  fail "${PROVISIONING_USER} could not create a user in ${LIBRARY_ORG} - it cannot do the
  job it exists for. Check its ORG_USER_MANAGER membership."

permitted="$(code_and_message "${prov_auth[@]}" -H "x-zitadel-orgid: ${library_org_id}" \
  -X POST "${ZITADEL_URL}/management/v1/users/${fresh_id}/grants" \
  -d "{\"projectId\":\"${library_project_id}\",\"roleKeys\":[\"LIBRARY_ADMIN\"]}")"

for subject in "${consortium_org_id}:${escalation_subject_id}" "${library_org_id}:${fresh_id}"; do
  curl -sS "${auth[@]}" -H "x-zitadel-orgid: ${subject%%:*}" \
    -X DELETE "${ZITADEL_URL}/v2/users/${subject##*:}" >/dev/null 2>&1 || true
done

note "IAM_OWNER grants ADMIN in ${CONSORTIUM_ORG}   -> ${control} (want 200)"
note "${PROVISIONING_USER} grants ADMIN there      -> ${refused} (want 403/404 denied)"
note "${PROVISIONING_USER} grants LIBRARY_ADMIN    -> ${permitted} (want 200)"

case "${control}" in
  200*) ;;
  *) fail "The control failed: IAM_OWNER could not make this grant either (${control}).
  The probe proves nothing until this succeeds - fix the call, not the permissions." ;;
esac

case "${refused}" in
  403*|404*membership\ not\ found*) ;;
  200*) fail "CONTAINMENT IS NOT IN PLACE. ${PROVISIONING_USER} granted ADMIN in
  ${CONSORTIUM_ORG}. Do not use this instance for provisioning." ;;
  *) fail "Unexpected refusal from the escalation attempt: ${refused}. Expected a denial
  naming a missing membership; something else is wrong." ;;
esac

case "${permitted}" in
  200*) ;;
  *) fail "The provisioning account cannot grant the role it exists to grant
  (${permitted}). The org membership is wrong." ;;
esac

note "the organisation boundary holds, and the admin control succeeded"

# ---------------------------------------------------------------------------
# The agency claim
#
# dcb-service reads the agency from a flat `code` claim (AgencyClaims), and treats absent
# as no access. Zitadel puts metadata under urn:zitadel:iam:user:metadata instead, base64
# encoded, so without this action a library user signs in to an empty application.
# ---------------------------------------------------------------------------
say "Agency claim action"

# The metadata value is PLAIN here - measured. The base64 applies only where Zitadel
# projects metadata into its own claim, so decoding it would corrupt it.
action_script="$(cat <<'JS'
function dcbAgencyCode(ctx, api) {
  var metadata = ctx.v1.user.getMetadata();
  if (!metadata || !metadata.metadata) {
    return;
  }
  for (var i = 0; i < metadata.metadata.length; i++) {
    var entry = metadata.metadata[i];
    if (entry.key === 'code') {
      api.v1.claims.setClaim('code', '' + entry.value);
    }
  }
}
JS
)"

# The function name must equal the action name. Name it anything a JS identifier cannot be
# - "dcb-agency-code" - and the run fails with "function not found"; with allowedToFail
# false that failure is a 500 on EVERY token request, so it is true here deliberately.
action_body="$(jqp 'import json,sys; print(json.dumps({"name": "dcbAgencyCode",
  "script": sys.argv[1], "timeout": "10s", "allowedToFail": True}))' "${action_script}")"

action_id="$(mgmt "${library_org_id}" POST /management/v1/actions/_search '{}' |
  jqp 'import json,sys; print(next((a["id"] for a in json.load(sys.stdin).get("result", [])
       if a.get("name") == "dcbAgencyCode"), ""))')"

if [ -n "${action_id}" ]; then
  mgmt "${library_org_id}" PUT "/management/v1/actions/${action_id}" "${action_body}" >/dev/null
else
  action_id="$(mgmt "${library_org_id}" POST /management/v1/actions "${action_body}" |
    jqp 'import json,sys; print(json.load(sys.stdin).get("id", ""))')"
fi

[ -n "${action_id}" ] || fail "Could not create the dcbAgencyCode action."

# Flow 2 is Complement Token, trigger 5 Pre access token creation. POST: the gateway
# answers PUT on this path with 405, and a trailing /actions with 404.
mgmt "${library_org_id}" POST "/management/v1/flows/2/trigger/5" \
  "{\"actionIds\":[\"${action_id}\"]}" >/dev/null

attached="$(mgmt "${library_org_id}" GET /management/v1/flows/2 |
  jqp 'import json,sys
flow = json.load(sys.stdin).get("flow", {})
print(" ".join(action.get("name", "")
  for trigger in flow.get("triggerActions", [])
  for action in trigger.get("actions", [])))')"

case "${attached}" in
  *dcbAgencyCode*) note "attached to Complement Token / pre access token creation" ;;
  *) fail "dcbAgencyCode did not attach to flow 2 trigger 5 (found: ${attached:-nothing})." ;;
esac

# ---------------------------------------------------------------------------
# Prove a real token carries the agency
#
# Zitadel does not support the password grant, so this drives the session API the way a
# custom login UI would. Everything here is what a deployment must also configure.
# ---------------------------------------------------------------------------
say "Proving the access token carries the agency"

# /oauth/v2/authorize mints a v1 auth request unless login v2 is on, and the v2 OIDC
# service answers 404 for those. DEFAULTINSTANCE_* does nothing once the instance exists.
# The base URI is never fetched: this script is the login UI.
curl -sS "${auth[@]}" -X PUT "${ZITADEL_URL}/v2/features/instance" \
  -d '{"loginV2":{"required":true,"baseUri":"http://localhost:9998/ui/v2/login/"}}' >/dev/null

# Linking a session to an auth request needs session.link, which IAM_OWNER does NOT carry.
bootstrap_id="$(curl -sS "${auth[@]}" "${ZITADEL_URL}/auth/v1/users/me" |
  jqp 'import json,sys; d=json.load(sys.stdin); print((d.get("user") or d).get("id", ""))')"

[ -n "${bootstrap_id}" ] || fail "Could not identify the bootstrap account."

curl -sS "${auth[@]}" -X PUT "${ZITADEL_URL}/admin/v1/members/${bootstrap_id}" \
  -d "{\"userId\":\"${bootstrap_id}\",\"roles\":[\"IAM_OWNER\",\"IAM_LOGIN_CLIENT\"]}" >/dev/null

# Recreated every run: the client secret is returned at creation and never again.
existing_app="$(mgmt "${library_org_id}" POST "/management/v1/projects/${library_project_id}/apps/_search" '{}' |
  jqp 'import json,sys; print(next((a["id"] for a in json.load(sys.stdin).get("result", [])
       if a.get("name") == "dcb-token-probe"), ""))')"

if [ -n "${existing_app}" ]; then
  mgmt "${library_org_id}" DELETE \
    "/management/v1/projects/${library_project_id}/apps/${existing_app}" >/dev/null
fi

# OIDC_TOKEN_TYPE_JWT, not the BEARER default: an opaque token carries no claims at all,
# and dcb-service validates a JWT.
app="$(mgmt "${library_org_id}" POST "/management/v1/projects/${library_project_id}/apps/oidc" '{
  "name": "dcb-token-probe",
  "redirectUris": ["http://localhost:9999/callback"],
  "responseTypes": ["OIDC_RESPONSE_TYPE_CODE"],
  "grantTypes": ["OIDC_GRANT_TYPE_AUTHORIZATION_CODE"],
  "appType": "OIDC_APP_TYPE_WEB",
  "authMethodType": "OIDC_AUTH_METHOD_TYPE_BASIC",
  "devMode": true,
  "accessTokenType": "OIDC_TOKEN_TYPE_JWT",
  "accessTokenRoleAssertion": true,
  "idTokenRoleAssertion": true,
  "idTokenUserinfoAssertion": true
}')"

probe_client_id="$(jqp 'import json,sys; print(json.loads(sys.argv[1]).get("clientId", ""))' "${app}")"
probe_client_secret="$(jqp 'import json,sys; print(json.loads(sys.argv[1]).get("clientSecret", ""))' "${app}")"

[ -n "${probe_client_id}" ] && [ -n "${probe_client_secret}" ] ||
  fail "Could not create the dcb-token-probe application."

probe_login="token-probe@invalid"
probe_password="Sup3rSecret!Probe"

old_probe="$(mgmt "${library_org_id}" POST /management/v1/users/_search \
  "{\"queries\":[{\"userNameQuery\":{\"userName\":\"${probe_login}\",\"method\":\"TEXT_QUERY_METHOD_EQUALS\"}}]}" |
  jqp 'import json,sys; r=json.load(sys.stdin).get("result", []); print(r[0]["id"] if r else "")')"

if [ -n "${old_probe}" ]; then
  curl -sS "${auth[@]}" -H "x-zitadel-orgid: ${library_org_id}" \
    -X DELETE "${ZITADEL_URL}/v2/users/${old_probe}" >/dev/null 2>&1 || true
fi

probe_user_id="$(curl -sS "${auth[@]}" -H "x-zitadel-orgid: ${library_org_id}" \
  -X POST "${ZITADEL_URL}/v2/users/human" -d "$(jqp 'import json,sys; print(json.dumps({
    "username": sys.argv[1],
    "organization": {"orgId": sys.argv[2]},
    "profile": {"givenName": "Token", "familyName": "Probe"},
    "email": {"email": sys.argv[1], "isVerified": True},
    "password": {"password": sys.argv[3], "changeRequired": False}}))' \
    "${probe_login}" "${library_org_id}" "${probe_password}")" |
  jqp 'import json,sys; print(json.load(sys.stdin).get("userId", ""))')"

[ -n "${probe_user_id}" ] || fail "Could not create the token probe user."

# Without a grant Zitadel refuses to issue a token at all - projectRoleCheck makes a
# missing grant fatal rather than merely roleless.
mgmt "${library_org_id}" POST "/management/v1/users/${probe_user_id}/grants" \
  "{\"projectId\":\"${library_project_id}\",\"roleKeys\":[\"LIBRARY_READ_ONLY\"]}" >/dev/null
mgmt "${library_org_id}" POST "/management/v1/users/${probe_user_id}/metadata/code" \
  "{\"value\":\"$(printf '%s' "${TEST_AGENCY}" | base64)\"}" >/dev/null

scopes="openid+profile+urn:zitadel:iam:user:metadata+urn:zitadel:iam:org:project:id:${library_project_id}:aud"
auth_request="$(curl -sS -o /dev/null -D - \
  "${ZITADEL_URL}/oauth/v2/authorize?client_id=${probe_client_id}&redirect_uri=http%3A%2F%2Flocalhost%3A9999%2Fcallback&response_type=code&scope=${scopes}&state=probe" |
  grep -i '^location:' | grep -o -E 'authRequest=[^&[:space:]]+' | head -1 | cut -d= -f2 | tr -d '\r')"

[ -n "${auth_request}" ] || fail "No auth request id came back from /oauth/v2/authorize."

session="$(curl -sS "${auth[@]}" -H "x-zitadel-orgid: ${library_org_id}" \
  -X POST "${ZITADEL_URL}/v2/sessions" -d "$(jqp 'import json,sys; print(json.dumps({
    "checks": {"user": {"loginName": sys.argv[1]}, "password": {"password": sys.argv[2]}}}))' \
    "${probe_login}" "${probe_password}")")"

callback="$(curl -sS "${auth[@]}" -X POST "${ZITADEL_URL}/v2/oidc/auth_requests/${auth_request}" \
  -d "$(jqp 'import json,sys; s=json.loads(sys.argv[1]); print(json.dumps({"session": {
    "sessionId": s.get("sessionId", ""), "sessionToken": s.get("sessionToken", "")}}))' "${session}")")"

probe_code="$(jqp 'import json,sys,re; d=json.loads(sys.argv[1])
m = re.search(r"[?&]code=([^&]+)", d.get("callbackUrl", ""))
print(m.group(1) if m else "")' "${callback}")"

[ -n "${probe_code}" ] ||
  fail "No authorization code: $(jqp 'import json,sys; print(json.loads(sys.argv[1]).get("message", "?"))' "${callback}")"

token="$(curl -sS -u "${probe_client_id}:${probe_client_secret}" \
  -d "grant_type=authorization_code&code=${probe_code}&redirect_uri=http://localhost:9999/callback" \
  "${ZITADEL_URL}/oauth/v2/token")"

verdict="$(jqp 'import json,sys,base64
token = json.loads(sys.argv[1])
expected = sys.argv[2]
access = token.get("access_token")
if not access:
    print("FAIL no access token: %s" % token.get("error_description", token.get("error"))); raise SystemExit
parts = access.split(".")
if len(parts) != 3:
    print("FAIL the access token is opaque, not a JWT - dcb-service cannot validate it"); raise SystemExit
payload = parts[1] + "=" * (-len(parts[1]) % 4)
claims = json.loads(base64.urlsafe_b64decode(payload))
code = claims.get("code")
roles = [k for k in claims if k.endswith(":roles")]
held = sorted({r for k in roles for r in claims[k]})
if code != expected:
    print("FAIL the flat code claim is %r, expected %r - the action did not run" % (code, expected)); raise SystemExit
if "LIBRARY_READ_ONLY" not in held:
    print("FAIL roles claim carries %s, expected LIBRARY_READ_ONLY" % (held or "nothing")); raise SystemExit
print("OK JWT, code=%s, roles=%s" % (code, ",".join(held)))' "${token}" "${TEST_AGENCY}")"

curl -sS "${auth[@]}" -H "x-zitadel-orgid: ${library_org_id}" \
  -X DELETE "${ZITADEL_URL}/v2/users/${probe_user_id}" >/dev/null 2>&1 || true

case "${verdict}" in
  OK*) note "${verdict#OK }" ;;
  *) fail "THE TOKEN DOES NOT CARRY THE AGENCY. ${verdict#FAIL }
  Library users would sign in and see an empty application." ;;
esac

note "a real sign-in produces a token dcb-service can scope"

# ---------------------------------------------------------------------------
say "Done"

cat <<EOF

  Zitadel is running at ${ZITADEL_URL}. Console sign-in: the human admin from
  ZITADEL_FIRSTINSTANCE_ORG_HUMAN_*. Invitation emails: http://localhost:8025.

  Organisations
    ${CONSORTIUM_ORG}  ${consortium_org_id}  ADMIN, CONSORTIUM_ADMIN
    ${LIBRARY_ORG}  ${library_org_id}  LIBRARY_ADMIN, LIBRARY_READ_ONLY

  DCB DOES NOT IMPLEMENT THIS PROVIDER. dcb.identity-provider.type accepts only
  keycloak, and IdentityProviderTypeCheck refuses anything else at startup. This script
  configures the instance an adapter would be written and measured against; the settings
  a ZitadelIdentityProviderClient would need are:

    base URL     ${ZITADEL_URL}
    org id       ${library_org_id}     (x-zitadel-orgid on every Management API call)
    project id   ${library_project_id}
    service user ${PROVISIONING_USER}  (PAT, re-minted by each run of this script)

  WHAT THIS MEASURED, and it differs from Keycloak:

  * There is no per-role grant restriction. ORG_USER_MANAGER and PROJECT_OWNER both
    confer granting ANY role in scope, and custom permission sets do not exist. Part 2.3's
    containment cannot be reproduced.
  * The only boundary that holds is the ORGANISATION, proven above: the provisioning
    account is refused in ${CONSORTIUM_ORG} - 404 "membership not found", where the
    IAM_OWNER control succeeds - and permitted in ${LIBRARY_ORG}.
  * So consortium roles and library roles MUST live in separate organisations. In one
    organisation, whatever can create a library account can also grant ADMIN.
  * Zitadel puts the agency under urn:zitadel:iam:user:metadata, base64 encoded, not in
    the flat \`code\` claim AgencyClaims reads - so the dcbAgencyCode action copies it
    across at token creation. Proven above against a real sign-in, not assumed.
  * That action is load-bearing and easy to break: its JS function name must equal the
    action name, and a failing action with allowedToFail false makes EVERY token request
    500. Actions v1 is also deprecated, for removal in Zitadel v5.
  * The application must issue OIDC_TOKEN_TYPE_JWT. The default is an opaque token, which
    carries no claims for dcb-service to read at all.

EOF

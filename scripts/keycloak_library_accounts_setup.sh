#!/usr/bin/env bash
#
# Configure a LOCAL DEV Keycloak realm for library account provisioning and the DCB Admin
# access bar. This is docs/identity-provider-setup.md, executed.
#
#   ./scripts/keycloak_library_accounts_setup.sh
#
# It is IDEMPOTENT: run it again after a realm re-import, or after changing your mind, and
# it converges rather than duplicating.
#
# IT CONFIGURES THE CONTAINMENT GRANT, and then PROVES it: the last thing it does is ask
# the service account to map ADMIN and require a 403. A control nobody has watched refuse
# something is not a control, and this one is version-sensitive enough that asserting it
# without testing would be worse than not claiming it at all.
#
# Requires Keycloak started with --features=admin-fine-grained-authz. It is a PREVIEW
# feature and is OFF by default in Keycloak 26; the script checks and stops if it is
# missing, rather than configuring something inert.
#
# DEV ONLY. It creates users with known passwords. Never point it at anything shared.

set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
REALM="${REALM:-openrs}"
ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

# Where the two front ends run in dev. Redirect URIs are exact-matched by Keycloak, so a
# mismatch here is a login that bounces back with "Invalid parameter: redirect_uri".
ADMIN_UI_ORIGIN="${ADMIN_UI_ORIGIN:-http://localhost:5173}"
DAFL_ORIGIN="${DAFL_ORIGIN:-http://localhost:5174}"

ADMIN_UI_CLIENT="${ADMIN_UI_CLIENT:-dcb-admin}"
DAFL_CLIENT="${DAFL_CLIENT:-dcb-admin-for-libraries}"
PROVISIONING_CLIENT="${PROVISIONING_CLIENT:-dcb-provisioning}"
PROVISIONING_SECRET="${PROVISIONING_SECRET:-dev-provisioning-secret}"

# The agency code the test library user belongs to. Must match a Library's agencyCode in
# DCB, or that user signs in and sees nothing - which is the correct behaviour for an
# unknown agency and a confusing one if you were not expecting it.
TEST_AGENCY="${TEST_AGENCY:-alpha}"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
note() { printf '  %s\n' "$*"; }

# ---------------------------------------------------------------------------
# Admin token
# ---------------------------------------------------------------------------
say "Authenticating against ${KEYCLOAK_URL}"

# Refreshed per section rather than fetched once.
#
# A master admin token lives 60 seconds by default. This script grew past that, and the
# failure is not a clean 401 - `curl -sf` swallows it, the next `python -c` receives empty
# stdin, and what you see is a JSONDecodeError pointing at entirely the wrong thing.
refresh_auth() {
  local token
  token="$(curl -sf -X POST \
    "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
    -d "client_id=admin-cli" \
    -d "username=${ADMIN_USER}" \
    -d "password=${ADMIN_PASSWORD}" \
    -d "grant_type=password" | python -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')"

  if [ -z "${token}" ]; then
    echo "Could not authenticate against ${KEYCLOAK_URL} as ${ADMIN_USER}" >&2
    exit 1
  fi

  auth=(-H "Authorization: Bearer ${token}" -H "Content-Type: application/json")
}

refresh_auth
api="${KEYCLOAK_URL}/admin/realms/${REALM}"

# ---------------------------------------------------------------------------
# Roles
# ---------------------------------------------------------------------------
say "Realm roles"

for role in ADMIN CONSORTIUM_ADMIN LIBRARY_ADMIN LIBRARY_READ_ONLY; do
  if curl -sf "${auth[@]}" "${api}/roles/${role}" >/dev/null 2>&1; then
    note "${role} already exists"
  else
    curl -sf "${auth[@]}" -X POST "${api}/roles" \
      -d "{\"name\":\"${role}\"}" >/dev/null
    note "${role} created"
  fi
done

# ---------------------------------------------------------------------------
# The agency claim
#
# A shared client scope rather than a mapper per client: the claim is one fact about the
# person, and two copies of the mapper are two things to keep in step. Assigned to both
# browser clients below.
# ---------------------------------------------------------------------------
say "Agency claim scope (code)"

scope_id="$(curl -sf "${auth[@]}" "${api}/client-scopes" |
  python -c 'import json,sys; print(next((s["id"] for s in json.load(sys.stdin) if s["name"]=="dcb-agency"), ""))')"

if [ -z "${scope_id}" ]; then
  curl -sf "${auth[@]}" -X POST "${api}/client-scopes" -d '{
    "name": "dcb-agency",
    "protocol": "openid-connect",
    "attributes": {"include.in.token.scope": "true", "display.on.consent.screen": "false"},
    "protocolMappers": [{
      "name": "agency-code",
      "protocol": "openid-connect",
      "protocolMapper": "oidc-usermodel-attribute-mapper",
      "config": {
        "user.attribute": "code",
        "claim.name": "code",
        "jsonType.label": "String",
        "multivalued": "true",
        "access.token.claim": "true",
        "id.token.claim": "true",
        "userinfo.token.claim": "true"
      }
    }]
  }' >/dev/null
  note "created client scope dcb-agency with the code mapper"
  scope_id="$(curl -sf "${auth[@]}" "${api}/client-scopes" |
    python -c 'import json,sys; print(next(s["id"] for s in json.load(sys.stdin) if s["name"]=="dcb-agency"))')"
else
  note "client scope dcb-agency already exists"
fi

# ---------------------------------------------------------------------------
# Browser clients
#
# TWO of them, and that is the point. The token records which client obtained it in `azp`,
# and AdminUiAccessPolicy refuses a DCB Admin token held by a non-consortium account. One
# shared client makes that check either useless or catastrophic.
# ---------------------------------------------------------------------------
create_public_client() {
  local client_id="$1" origin="$2"

  local uuid
  uuid="$(curl -sf "${auth[@]}" "${api}/clients?clientId=${client_id}" |
    python -c 'import json,sys; d=json.load(sys.stdin); print(d[0]["id"] if d else "")')"

  local body
  body="$(python - "$client_id" "$origin" <<'PY'
import json, sys
client_id, origin = sys.argv[1], sys.argv[2]
print(json.dumps({
    "clientId": client_id,
    "publicClient": True,          # a browser SPA holds no secret
    "standardFlowEnabled": True,   # authorization code + PKCE, as a browser uses
    # DEV ONLY, and it earns its place: the password grant is the only way to get a
    # REAL token for a chosen user - correct `azp`, correct roles, correct agency claim -
    # without driving a browser. That is what makes the access bar and the agency scoping
    # testable from a terminal. Never enable it on a client anybody else can reach.
    "directAccessGrantsEnabled": True,
    "serviceAccountsEnabled": False,
    "redirectUris": [origin + "/*"],
    "webOrigins": [origin],
    "attributes": {"pkce.code.challenge.method": "S256"},
    "protocolMappers": [{
        "name": "roles",
        "protocol": "openid-connect",
        "protocolMapper": "oidc-usermodel-realm-role-mapper",
        "config": {
            "claim.name": "roles",
            "jsonType.label": "String",
            "multivalued": "true",
            "access.token.claim": "true",
            "id.token.claim": "true",
            "userinfo.token.claim": "true",
        },
    }],
}))
PY
)"

  if [ -z "${uuid}" ]; then
    curl -sf "${auth[@]}" -X POST "${api}/clients" -d "${body}" >/dev/null
    uuid="$(curl -sf "${auth[@]}" "${api}/clients?clientId=${client_id}" |
      python -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')"
    note "${client_id} created (${origin})"
  else
    curl -sf "${auth[@]}" -X PUT "${api}/clients/${uuid}" -d "${body}" >/dev/null
    note "${client_id} updated (${origin})"
  fi

  # The agency claim, on both.
  curl -sf "${auth[@]}" -X PUT \
    "${api}/clients/${uuid}/default-client-scopes/${scope_id}" >/dev/null || true
}

refresh_auth
say "Browser clients"
create_public_client "${ADMIN_UI_CLIENT}" "${ADMIN_UI_ORIGIN}"
create_public_client "${DAFL_CLIENT}" "${DAFL_ORIGIN}"

# ---------------------------------------------------------------------------
# The provisioning service account
# ---------------------------------------------------------------------------
refresh_auth
say "Provisioning client (${PROVISIONING_CLIENT})"

prov_uuid="$(curl -sf "${auth[@]}" "${api}/clients?clientId=${PROVISIONING_CLIENT}" |
  python -c 'import json,sys; d=json.load(sys.stdin); print(d[0]["id"] if d else "")')"

prov_body="$(python - "$PROVISIONING_CLIENT" "$PROVISIONING_SECRET" <<'PY'
import json, sys
client_id, secret = sys.argv[1], sys.argv[2]
print(json.dumps({
    "clientId": client_id,
    "publicClient": False,          # confidential: this is a service, not a person
    "serviceAccountsEnabled": True,
    "standardFlowEnabled": False,   # it never signs anybody in
    "directAccessGrantsEnabled": False,
    "implicitFlowEnabled": False,
    "secret": secret,
}))
PY
)"

if [ -z "${prov_uuid}" ]; then
  curl -sf "${auth[@]}" -X POST "${api}/clients" -d "${prov_body}" >/dev/null
  prov_uuid="$(curl -sf "${auth[@]}" "${api}/clients?clientId=${PROVISIONING_CLIENT}" |
    python -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')"
  note "created"
else
  curl -sf "${auth[@]}" -X PUT "${api}/clients/${prov_uuid}" -d "${prov_body}" >/dev/null
  note "updated"
fi

# view-users and query-users ONLY.
#
# NOT manage-users, and that is the whole point. Measured on this stack: a service account
# holding manage-users can map ANY realm role, ADMIN included, and enabling fine-grained
# permissions on the role does not change that - the blanket role bypasses the policy
# entirely. "view-users, query-users, manage-users" reads like least privilege and is not.
#
# The ability to CREATE and MODIFY users is granted below instead, through the fine-grained
# users permission, so every one of this account's powers comes from a policy that names it.
refresh_auth
say "Service account roles"

sa_user_id="$(curl -sf "${auth[@]}" "${api}/clients/${prov_uuid}/service-account-user" |
  python -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

rm_uuid="$(curl -sf "${auth[@]}" "${api}/clients?clientId=realm-management" |
  python -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')"

roles_json="$(curl -sf "${auth[@]}" "${api}/clients/${rm_uuid}/roles")"

wanted="$(python - "$roles_json" <<'PY'
import json, sys
wanted = {"view-users", "query-users"}
print(json.dumps([r for r in json.loads(sys.argv[1]) if r["name"] in wanted]))
PY
)"

curl -sf "${auth[@]}" -X POST \
  "${api}/users/${sa_user_id}/role-mappings/clients/${rm_uuid}" \
  -d "${wanted}" >/dev/null

note "view-users, query-users granted"
note "manage-users NOT granted - it would bypass the containment grant below"

# Idempotence: an earlier version of this script granted manage-users. Leaving it in place
# would silently defeat everything configured after this point.
removable="$(python - "$roles_json" <<'PY'
import json, sys
print(json.dumps([r for r in json.loads(sys.argv[1]) if r["name"] == "manage-users"]))
PY
)"

if [ "${removable}" != "[]" ]; then
  curl -sf "${auth[@]}" -X DELETE     "${api}/users/${sa_user_id}/role-mappings/clients/${rm_uuid}"     -d "${removable}" >/dev/null 2>&1 && note "manage-users removed (left over from an earlier run)"
fi

# ---------------------------------------------------------------------------
# CONTAINMENT: the grant that survives a compromise of dcb-service
#
# The three gates on the role inside dcb-service - the GraphQL enum,
# ProvisionableRole.parse and the Postgres CHECK - all live in that service and fall
# together with it. This one does not, because Keycloak enforces it.
#
# Two halves, and BOTH are needed:
#   1. the service account holds no blanket manage-users (done above), so every power it
#      has comes from a policy naming it;
#   2. that policy is attached to the users resource and to exactly the two library roles.
#
# The effect: LIBRARY_ADMIN and LIBRARY_READ_ONLY become mappable and every other realm
# role stops even being OFFERED - and a forged request naming ADMIN is refused outright.
# ---------------------------------------------------------------------------
refresh_auth
say "Containment (fine-grained map-role)"

if ! curl -sf "${auth[@]}" "${KEYCLOAK_URL}/admin/serverinfo" |
  python -c 'import json,sys; sys.exit(0 if any(f["name"]=="ADMIN_FINE_GRAINED_AUTHZ" and f["enabled"] for f in json.load(sys.stdin).get("features",[])) else 1)'; then
  cat >&2 <<'EOF'

  ADMIN_FINE_GRAINED_AUTHZ is not enabled on this Keycloak.

  It is a PREVIEW feature and is off by default in Keycloak 26. Without it a role has no
  "Permissions" at all, the grant below cannot be configured, and a provisioning service
  account can map ANY realm role - ADMIN included.

  Start Keycloak with --features=admin-fine-grained-authz and run this again.

EOF
  exit 1
fi

rm_authz="${api}/clients/${rm_uuid}/authz/resource-server"

# ENABLE THE PERMISSIONS FIRST.
#
# realm-management has no authorization server at all until something enables a
# fine-grained permission on it. Looking a policy up before that point does not return an
# empty list, it 404s - and with `curl -sf` that is silent, so the next `python -c` reads
# empty stdin and reports a JSON error against an entirely innocent line.

users_perms="$(curl -sf "${auth[@]}" -X PUT "${api}/users-management-permissions" \
  -d '{"enabled":true}')"

declare -A map_role_perms

for role in LIBRARY_ADMIN LIBRARY_READ_ONLY; do
  role_id="$(curl -sf "${auth[@]}" "${api}/roles/${role}" |
    python -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

  map_role_perms[$role]="$(curl -sf "${auth[@]}" -X PUT \
    "${api}/roles-by-id/${role_id}/management/permissions" -d '{"enabled":true}' |
    python -c 'import json,sys; print(json.load(sys.stdin)["scopePermissions"]["map-role"])')"
done

note "permissions enabled on users and on the two provisionable roles"

# One policy, named for what it permits, reused by every permission below - so there is a
# single place to look when asking what the provisioning account may do.
policy_id="$(curl -sf "${auth[@]}" "${rm_authz}/policy?name=dcb-provisioning-may-map" |
  python -c 'import json,sys; d=json.load(sys.stdin); print(next((p["id"] for p in d if p["name"]=="dcb-provisioning-may-map"), ""))')"

if [ -z "${policy_id}" ]; then
  policy_id="$(curl -sf "${auth[@]}" -X POST "${rm_authz}/policy/client" \
    -d "{\"name\":\"dcb-provisioning-may-map\",\"description\":\"The DCB provisioning service account, and nothing else\",\"clients\":[\"${prov_uuid}\"],\"logic\":\"POSITIVE\"}" |
    python -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
  note "policy dcb-provisioning-may-map created"
else
  note "policy dcb-provisioning-may-map already exists"
fi

# Point a scope permission at that policy. AFFIRMATIVE: this policy granting is enough.
#
# Re-authenticates per call. A master admin token lives 60 seconds, this loop makes five
# round trips, and an expired token here is NOT a clean error: `curl -sf` swallows the 401
# and the next `python` reports a JSON parse failure against a line that is perfectly fine.
attach_policy() {
  local permission_id="$1" label="$2"

  refresh_auth

  local current
  current="$(curl -sf "${auth[@]}" "${rm_authz}/permission/scope/${permission_id}")"

  if [ -z "${current}" ]; then
    echo "  could not read permission ${label} (${permission_id})" >&2
    exit 1
  fi

  local updated
  updated="$(python - "${policy_id}" <<PY
import json, sys
permission = json.loads('''${current}''')
permission["policies"] = [sys.argv[1]]
permission["decisionStrategy"] = "AFFIRMATIVE"
print(json.dumps(permission))
PY
)"

  curl -sf "${auth[@]}" -X PUT "${rm_authz}/permission/scope/${permission_id}" \
    -d "${updated}" >/dev/null

  note "${label}"
}

# The user-level half. Without manage-users the account cannot create or edit anybody, so
# these replace it - narrowly, and through a policy that names the client.
for scope in view manage map-roles; do
  attach_policy \
    "$(python -c 'import json,sys; print(json.load(sys.stdin)["scopePermissions"][sys.argv[1]])' "${scope}" <<<"${users_perms}")" \
    "users.${scope}"
done

# The role-level half. ONLY the two provisionable roles get a policy; every other role is
# left with no fine-grained permission and no blanket role to fall back on.
for role in LIBRARY_ADMIN LIBRARY_READ_ONLY; do
  attach_policy "${map_role_perms[$role]}" "${role}.map-role"
done


# ---------------------------------------------------------------------------
# Test users
# ---------------------------------------------------------------------------
upsert_user() {
  local username="$1" password="$2" agency="$3"; shift 3
  local roles=("$@")

  refresh_auth

  local uid
  uid="$(curl -sf "${auth[@]}" "${api}/users?username=${username}&exact=true" |
    python -c 'import json,sys; d=json.load(sys.stdin); print(d[0]["id"] if d else "")')"

  local body
  body="$(python - "$username" "$agency" <<'PY'
import json, sys
username, agency = sys.argv[1], sys.argv[2]
user = {
    "username": username,
    "email": f"{username}@example.invalid",
    "firstName": username.split("-")[0].title(),
    "lastName": "Tester",
    "enabled": True,
    "emailVerified": True,
}
# Multi-valued, because AgencyClaims reads it as a collection and a person can be
# responsible for more than one library.
if agency:
    user["attributes"] = {"code": [agency]}
print(json.dumps(user))
PY
)"

  if [ -z "${uid}" ]; then
    curl -sf "${auth[@]}" -X POST "${api}/users" -d "${body}" >/dev/null
    uid="$(curl -sf "${auth[@]}" "${api}/users?username=${username}&exact=true" |
      python -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')"
  else
    curl -sf "${auth[@]}" -X PUT "${api}/users/${uid}" -d "${body}" >/dev/null
  fi

  curl -sf "${auth[@]}" -X PUT "${api}/users/${uid}/reset-password" \
    -d "{\"type\":\"password\",\"value\":\"${password}\",\"temporary\":false}" >/dev/null

  local reps
  reps="$(python - "$(curl -sf "${auth[@]}" "${api}/roles")" "${roles[*]}" <<'PY'
import json, sys
available = {r["name"]: r for r in json.loads(sys.argv[1])}
print(json.dumps([available[name] for name in sys.argv[2].split() if name in available]))
PY
)"

  curl -sf "${auth[@]}" -X POST "${api}/users/${uid}/role-mappings/realm" \
    -d "${reps}" >/dev/null

  note "${username} / ${password} — ${roles[*]}${agency:+ (code=${agency})}"
}

say "Test users"
upsert_user "consortium-admin" "password" "" ADMIN CONSORTIUM_ADMIN
upsert_user "library-admin" "password" "${TEST_AGENCY}" LIBRARY_ADMIN
upsert_user "library-readonly" "password" "${TEST_AGENCY}" LIBRARY_READ_ONLY
# Consortium staff are people at libraries. This one exists because it is the case that
# catches an over-tightened access bar.
upsert_user "both-roles" "password" "${TEST_AGENCY}" CONSORTIUM_ADMIN LIBRARY_ADMIN
# ---------------------------------------------------------------------------
# PROVE IT. Runbook Part 5.3, executed.
#
# A guard nobody has watched refuse something is not a guard. This asks the service
# account to do the one thing the whole design says it must not, against a throwaway user,
# and fails the script if Keycloak allows it.
# ---------------------------------------------------------------------------
refresh_auth
say "Proving containment"

prov_token="$(curl -sf -X POST   "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token"   -d "client_id=${PROVISIONING_CLIENT}"   -d "client_secret=${PROVISIONING_SECRET}"   -d "grant_type=client_credentials" |
  python -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')"

prov_auth=(-H "Authorization: Bearer ${prov_token}" -H "Content-Type: application/json")

subject="$(curl -sf "${prov_auth[@]}" -X POST "${api}/users"   -d '{"username":"containment-probe@invalid","email":"containment-probe@invalid","enabled":false}'   -D - -o /dev/null | tr -d '' | awk -F/ '/^[Ll]ocation:/ {print $NF}')"

if [ -z "${subject}" ]; then
  echo "  could not create the probe user - the users.manage policy is not working" >&2
  exit 1
fi

admin_role="$(curl -sf "${auth[@]}" "${api}/roles/ADMIN" |
  python -c 'import json,sys; d=json.load(sys.stdin); print(json.dumps([{k: d[k] for k in ("id","name","composite","clientRole","containerId")}]))')"

# The real representation, taken from a real admin. An attacker would not politely ask
# which roles are on offer, so neither does this.
refused="$(curl -s -o /dev/null -w '%{http_code}' "${prov_auth[@]}" -X POST   "${api}/users/${subject}/role-mappings/realm" -d "${admin_role}")"

permitted="$(curl -s -o /dev/null -w '%{http_code}' "${prov_auth[@]}" -X POST   "${api}/users/${subject}/role-mappings/realm"   -d "$(curl -sf "${prov_auth[@]}" "${api}/users/${subject}/role-mappings/realm/available" |
    python -c 'import json,sys; print(json.dumps([r for r in json.load(sys.stdin) if r["name"]=="LIBRARY_READ_ONLY"]))')")"

curl -sf "${auth[@]}" -X DELETE "${api}/users/${subject}" >/dev/null

note "mapping ADMIN             -> ${refused} (want 403)"
note "mapping LIBRARY_READ_ONLY -> ${permitted} (want 204)"

if [ "${refused}" != "403" ] || [ "${permitted}" != "204" ]; then
  echo "
  CONTAINMENT IS NOT IN PLACE. The provisioning service account can map a role it must
  never be able to map, or cannot map one it needs. Do not use this realm for provisioning
  until this passes." >&2
  exit 1
fi

note "containment holds"

# ---------------------------------------------------------------------------
say "Done"

cat <<EOF

  dcb-service:
    DCB_IDENTITY_PROVIDER_TYPE=keycloak
    DCB_IDENTITY_PROVIDER_BASE_URL=${KEYCLOAK_URL}
    DCB_IDENTITY_PROVIDER_REALM=${REALM}
    DCB_IDENTITY_PROVIDER_CLIENT_ID=${PROVISIONING_CLIENT}
    DCB_IDENTITY_PROVIDER_CLIENT_SECRET=${PROVISIONING_SECRET}
    DCB_ADMIN_UI_CLIENT_ID=${ADMIN_UI_CLIENT}
    DCB_ADMIN_UI_ACCESS_MODE=WARN
    KEYCLOAK_CERT_URL=${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/certs

  Containment is configured AND proven above: this service account can map LIBRARY_ADMIN
  and LIBRARY_READ_ONLY, and Keycloak refuses it ADMIN with a 403.

  Email is not configured, so execute-actions-email will fail. That is expected locally and
  is deliberately NOT fatal - the account is still created, enabled and given its role, and
  the invitation can be resent from the accounts page once mail works.

EOF

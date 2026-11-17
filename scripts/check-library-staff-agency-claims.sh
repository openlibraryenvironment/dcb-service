#!/usr/bin/env bash
# Reports library staff whose access token carries no usable agency code. How to run it, and how
# to check by hand: docs/v9-migration-guide.md, "Checking library staff accounts". Read-only.
#   KC_URL        Keycloak base URL (add /auth for Keycloak 16 or older)
#   KC_REALM      the realm DCB's staff sign in to
#   KC_CLIENT_ID  DCB Admin for Libraries' client id, whose access token is checked
#   DCB_URL       optional: DCB's base URL, to check the codes against DCB's agencies
#   KC_ADMIN_CLIENT_ID + KC_ADMIN_CLIENT_SECRET (a service account with realm-management view-users
#   and view-clients), or KC_ADMIN_USER + KC_ADMIN_PASSWORD (a master-realm administrator)
set -euo pipefail

: "${KC_URL:?}" "${KC_REALM:?}" "${KC_CLIENT_ID:?}"
PAGE=100

if [[ -n "${KC_ADMIN_CLIENT_ID:-}" ]]; then
	TOKEN=$(curl -sf -d grant_type=client_credentials -d "client_id=$KC_ADMIN_CLIENT_ID" \
		--data-urlencode "client_secret=${KC_ADMIN_CLIENT_SECRET:?}" \
		"$KC_URL/realms/$KC_REALM/protocol/openid-connect/token" | jq -r .access_token)
else
	TOKEN=$(curl -sf -d grant_type=password -d client_id=admin-cli -d "username=${KC_ADMIN_USER:?}" \
		--data-urlencode "password=${KC_ADMIN_PASSWORD:?}" \
		"$KC_URL/realms/master/protocol/openid-connect/token" | jq -r .access_token)
fi

api() { curl -sf -H "Authorization: Bearer $TOKEN" "$KC_URL/admin/realms/$KC_REALM$1"; }

CLIENT=$(api "/clients?clientId=$KC_CLIENT_ID" | jq -r '.[0].id // empty')
[[ -n "$CLIENT" ]] || { echo "No client $KC_CLIENT_ID in realm $KC_REALM" >&2; exit 2; }

if [[ -n "${DCB_URL:-}" ]]; then
	AGENCIES=$(curl -sf "$DCB_URL/discovery/libraries?includeAll=true" | jq -c '[.[].code]')
else
	AGENCIES=null
fi

printf 'username\tenabled\tproblem\n'
checked=0 reported=0 first=0
while :; do
	page=$(api "/users?first=$first&max=$PAGE&briefRepresentation=true")
	[[ $(jq length <<<"$page") -gt 0 ]] || break
	while IFS=$'\t' read -r id username enabled; do
		claims=$(api "/clients/$CLIENT/evaluate-scopes/generate-example-access-token?scope=openid&userId=$id")
		problem=$(jq -r --argjson agencies "$AGENCIES" '
			def codes: [.code, .agencyCodes] | map(if type == "array" then .[] else . end)
				| map(select(type == "string") | gsub("^\\s+|\\s+$"; "")) | map(select(. != "")) | unique;
			def library: index("LIBRARY_ADMIN") or index("LIBRARY_READ_ONLY");
			if ((.roles // []) | library) then
				if (codes | length) == 0 then "no code or agencyCodes in the access token"
				elif $agencies != null and (codes - $agencies | length) > 0 then
					"not a DCB agency: \(codes - $agencies | join(", "))"
				else empty end
			elif ((.realm_access.roles // []) | library) then
				"library role only in realm_access.roles: DCB reads the top-level roles claim"
			else empty end' <<<"$claims")
		checked=$((checked + 1))
		if [[ -n "$problem" ]]; then
			printf '%s\t%s\t%s\n' "$username" "$enabled" "$problem"
			reported=$((reported + 1))
		fi
	done < <(jq -r '.[] | [.id, .username, (.enabled | tostring)] | @tsv' <<<"$page")
	first=$((first + PAGE))
done
echo "Checked $checked users; $reported to fix." >&2

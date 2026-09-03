# Identity provider setup: library accounts and the DCB Admin bar

What must exist in Keycloak or Zitadel before consortium staff can create DCB Admin for
Libraries accounts from DCB Admin, and before DCB Admin can be locked to consortium staff.

**Read Part 1 whichever provider you run.** It is the part that is already load-bearing
today — the agency claim in Part 1.2 is what stops one library reading another's patron
requests, and it is not new to this feature.

| Part | Applies to | Needed for |
|---|---|---|
| 1 | both providers | data scoping, and the DCB Admin bar |
| 2 | Keycloak | account provisioning |
| 3 | Zitadel | account provisioning — **not yet implemented, see the warning** |
| 4 | both | the environment variables |
| 5 | both | proving it works |

---

## Part 1 — What both providers need

### 1.1 Two roles, spelled exactly

The realm (Keycloak) or project (Zitadel) must carry these, as **exact upper-case strings**:

| Role | Who holds it |
|---|---|
| `ADMIN` | consortium staff, full |
| `CONSORTIUM_ADMIN` | consortium staff |
| `LIBRARY_ADMIN` | library staff who may change their library's data |
| `LIBRARY_READ_ONLY` | library staff who may only read it |

Only the last two are provisionable from DCB Admin. That is enforced four times over
(GraphQL enum → `ProvisionableRole.parse` → Postgres `CHECK` → the provider grant in 2.3),
so a bug or a compromise cannot turn "create a library account" into "create an
administrator".

### 1.2 The agency claim, `code`

**This is the one that matters most, and it is easy to get silently wrong.**

Every library-level account must carry a claim named **`code`** holding its agency code —
the same string as the library's `agencyCode` in DCB. `dcb-service` reads it in
`AgencyClaims`, and it decides which library's data that person sees.

- **A multi-valued claim is supported and sometimes required.** Somebody administering a
  shared Koha on behalf of several tenants gets several codes, and must not be made a
  consortium administrator just to cover them. `agencyCodes` is accepted as an alternative
  name for providers that cannot make an existing scalar claim multi-valued.
- **An empty or absent claim means NO ACCESS, not unrestricted access.** This is deliberate
  and it fails closed. The symptom of a missing mapper is therefore not an error — it is a
  library user who signs in successfully and sees nothing at all.
- Consortium accounts carry the claim too, harmlessly. Roles decide whether scoping applies;
  the claim decides what it contains.

**Keycloak:** on the user, an attribute `code`. In the client's *Client scopes → dedicated
scope → Mappers*, add a **User Attribute** mapper: user attribute `code`, token claim name
`code`, claim type String, **Add to access token ON**, **Multivalued ON**.

**Zitadel:** user metadata `code`, surfaced through an action/claim mapping into the access
token. Confirm the exact mechanism for your Zitadel version.

> Accounts created by DCB set this attribute themselves. Accounts that already exist need
> **backfilling by hand** — and until they are, those people see nothing.

### 1.3 Two separate OIDC clients

DCB Admin and DCB Admin for Libraries must be **separate OIDC clients**. They are supposed
to be already; in at least one place they are not.

| Application | Client ID (suggested) |
|---|---|
| DCB Admin | `dcb-admin` |
| DCB Admin for Libraries | `dcb-admin-for-libraries` |

Both are public browser clients using authorization code + PKCE, with the usual redirect
URIs and web origins for their deployed hostnames.

**Why it matters here:** the token records which client obtained it in `azp`, and
`AdminUiAccessPolicy` refuses a token minted for DCB Admin's client by an account holding no
consortium role. If the two apps share one client, enabling that check either locks out
every consortium administrator or admits every library user, depending which id you
configured.

> **Known issue.** `dcb-admin-ui/.env` currently carries
> `VITE_KEYCLOAK_ID=dcb-admin-for-libraries`. Audit `VITE_KEYCLOAK_ID` in **every**
> deployment before enabling the bar.

### 1.4 Turning the bar on, in two steps

1. Set `DCB_ADMIN_UI_CLIENT_ID` to DCB Admin's client id, leave
   `DCB_ADMIN_UI_ACCESS_MODE=WARN`. Nothing is refused.
2. Watch the log for `Would deny DCB Admin access: user=… roles=… client=…`. Every line is
   somebody who *would* be locked out. Fix the accounts or the client configuration.
3. When the log is quiet, set `DCB_ADMIN_UI_ACCESS_MODE=ENFORCE`. This is config, not a
   deploy, and it is instantly reversible.

Leaving `DCB_ADMIN_UI_CLIENT_ID` unset disables the check entirely, which is the default.

---

## Part 2 — Keycloak: account provisioning

### 2.1 The provisioning client

A **confidential** client, separate from the two in 1.3 — this one is `dcb-service` talking
to Keycloak, not a person signing in.

| Setting | Value |
|---|---|
| Client ID | `dcb-provisioning` |
| Client authentication | **On** (confidential) |
| Service accounts roles | **On** |
| Standard flow, Direct access grants, Implicit | **Off** — it never signs a person in |

Copy the secret from *Credentials*. It goes into
`DCB_IDENTITY_PROVIDER_CLIENT_SECRET` and nowhere else.

### 2.2 Service account permissions — and the role you must NOT grant

*Service accounts roles → Assign role → Filter by clients → `realm-management`*:

| Assign | Do **not** assign |
|---|---|
| `view-users` | **`manage-users`** |
| `query-users` | `realm-admin` |
| | `manage-realm` |

**`manage-users` is the trap, and an earlier version of this document recommended it.**
Measured on a real realm: a service account holding `manage-users` can map **any** realm
role, `ADMIN` included, and enabling fine-grained permissions on the role does not change
that — the blanket role bypasses the policy entirely. "view-users, query-users,
manage-users" reads like least privilege and is not.

The ability to create and modify users comes from §2.3 instead, through a policy that names
this client. Every power the account has is then attributable to something you can point at.

### 2.3 The containment grant — the control that actually matters

Everything else that constrains the role lives *inside* dcb-service — the GraphQL enum,
`ProvisionableRole.parse`, the Postgres `CHECK` — and all three fall together if
dcb-service is compromised. This one does not, because Keycloak enforces it.

**Prerequisite: Keycloak must be started with `--features=admin-fine-grained-authz`.** It is
a **preview** feature and is **off by default in Keycloak 26**. Without it a role has no
*Permissions* tab at all and none of the below can be configured.

Two halves, and **both** are required:

1. **The service account holds no blanket `manage-users`** (§2.2). Without this, everything
   below is decorative.
2. **A policy naming the client, attached to the users resource and to exactly the two
   roles.**

*Realm settings → enable fine-grained admin permissions*, then:

| Where | What |
|---|---|
| *Users → Permissions → enable* | attach the policy to `view`, `manage` and `map-roles` |
| *Realm roles → `LIBRARY_ADMIN` → Permissions → enable* | attach the policy to `map-role` |
| *Realm roles → `LIBRARY_READ_ONLY` → Permissions → enable* | attach the policy to `map-role` |
| every other role | leave permissions **disabled** — with no blanket role to fall back on, they are unreachable |

The policy is a **client** policy naming `dcb-provisioning`, and one policy is reused by
every permission above, so there is a single place to look when asking what the
provisioning account may do.

**The effect, measured:** `LIBRARY_ADMIN` and `LIBRARY_READ_ONLY` become mappable, every
other realm role stops even being *offered*, and a forged request naming `ADMIN` is
refused with a 403. A full compromise of dcb-service still cannot mint an administrator.

**Do not take that on trust — §5.3 is the test, and `scripts/keycloak_library_accounts_setup.sh`
runs it automatically.** Fine-grained admin permissions have moved between Keycloak
releases; an unverified assumption here reads as a control while being none.

### 2.4 It is scripted, for a dev realm

`dcb-service/scripts/keycloak_library_accounts_setup.sh` configures everything in Parts 1
and 2 against a local Keycloak, idempotently, and **fails if containment does not hold** —
its last act is to ask the service account to map `ADMIN` and require a 403.

It is written for a development realm (it creates users with known passwords). For a real
deployment, read it as an executable specification of what to configure by hand.
### 2.5 Email must actually send

Provisioning creates the account disabled, grants the role, enables it, and then asks
Keycloak to send an actions email (`UPDATE_PASSWORD`, `VERIFY_EMAIL`). **That email is the
entire credential flow** — no password is generated, returned or displayed anywhere.

So *Realm settings → Email* must be configured and working, and the realm's *Login → Forgot
password* / email settings must permit the actions link. If SMTP is broken, accounts are
created and nobody can ever sign in to them.

### 2.6 What dcb-service calls

For anyone auditing the grant, this is the whole surface, in order:

| # | Call |
|---|---|
| 1 | `POST /admin/realms/{realm}/users` — `enabled: false`, `emailVerified: false`, `attributes.code: [agency]` |
| 2 | `GET /admin/realms/{realm}/roles/{roleName}` |
| 3 | `POST /admin/realms/{realm}/users/{id}/role-mappings/realm` |
| 4 | `PUT /admin/realms/{realm}/users/{id}` — `enabled: true` |
| 5 | `PUT /admin/realms/{realm}/users/{id}/execute-actions-email` |
| — | `GET /admin/realms/{realm}/users/{id}` when listing; `DELETE` only to clean up a failed provision |
| — | `POST /realms/{realm}/protocol/openid-connect/token` for its own token |

The order is a safety property, not an implementation detail: a failure between steps leaves
a **disabled, roleless** account, which confers nothing.

---

## Part 3 — Zitadel

> ### ⚠ Provisioning is not implemented for Zitadel
>
> `IdentityProviderClient` has one implementation, Keycloak's. Zitadel's admin API shapes
> could not be verified against a real instance, and writing them from memory is precisely
> the failure mode this estate's doctrine names.
>
> Setting `DCB_IDENTITY_PROVIDER_TYPE=zitadel` produces **no client bean**. The
> `libraryUserProvisioningAvailable` query returns false and DCB Admin renders "account
> provisioning is not configured on this deployment" instead of a form. That is a legible
> absence, not a broken feature — but accounts still have to be created by hand.

**Parts 1.1 to 1.4 apply in full and are worth doing now.** They are what makes data scoping
and the DCB Admin bar work, and none of that depends on provisioning:

- the four roles, as project roles, spelled exactly;
- the `code` claim on every library account, via user metadata surfaced into the token;
- two separate OIDC clients, so `azp` can tell the apps apart;
- `DCB_ADMIN_UI_CLIENT_ID` — confirm Zitadel populates `azp` with the **client id** and not
  the project id before enforcing, or the bar will refuse the wrong people.

**To implement provisioning**, a `ZitadelIdentityProviderClient` needs, verified against the
deployed version rather than assumed:

1. Create a human user **deactivated**, then activate — the ordering in Part 2.5 is not
   Keycloak-specific, it is the safety property.
2. Grant the project role, using `DCB_IDENTITY_PROVIDER_PROJECT_ID` (the config field
   already exists for this).
3. Write the agency code as user metadata `code`, and confirm it reaches the access token.
4. Trigger the set-password / verify-email flow. **Never generate a password.**
5. Read a user's active and verified state for the listing.
6. **State Zitadel's own containment mechanism in the class documentation** — the equivalent
   of Part 2.3, most likely an org-scoped service user whose grant covers only the two
   project roles. If Zitadel cannot express it, say so plainly in the class documentation
   and in an ADR, rather than leaving a reader to assume the Keycloak property holds here
   too.

   **What Part 2.3 turned out to cost on Keycloak is the warning worth carrying over.** The
   obvious reading — grant the service account a manage-users-equivalent, then restrict which
   roles it may map — does not work: the blanket permission bypasses the per-role rule
   entirely. Containment only became real once the blanket grant was removed and *every*
   power, including creating a user at all, came from a policy naming the client. Expect to
   have to do the same thing in Zitadel, and expect the first arrangement that looks right
   to be inert. Test it the way Part 5.3 does before believing it.

---

## Part 4 — Environment variables

`dcb-service`:

| Variable | Example | Notes |
|---|---|---|
| `DCB_IDENTITY_PROVIDER_TYPE` | `keycloak` | **Unset = provisioning off.** Only `keycloak` is implemented |
| `DCB_IDENTITY_PROVIDER_BASE_URL` | `https://sso.example.org` | No trailing path |
| `DCB_IDENTITY_PROVIDER_REALM` | `dcb` | Keycloak only |
| `DCB_IDENTITY_PROVIDER_CLIENT_ID` | `dcb-provisioning` | The confidential client from 2.1 |
| `DCB_IDENTITY_PROVIDER_CLIENT_SECRET` | — | **No default. Naming a provider without it fails startup** |
| `DCB_IDENTITY_PROVIDER_PROJECT_ID` | — | Zitadel only |
| `DCB_ADMIN_UI_CLIENT_ID` | `dcb-admin` | **Unset = the bar is off** |
| `DCB_ADMIN_UI_ACCESS_MODE` | `WARN` → `ENFORCE` | Defaults to `WARN` |

`dcb-admin-ui`: `VITE_KEYCLOAK_ID` must be DCB Admin's own client id — see the known issue
in 1.3.

---

## Part 5 — Proving it works

### 5.1 The claim reaches the token

Sign in as a library account and decode the access token. It must contain `code` with the
right agency, and the role. **If `code` is missing the person will see an empty
application, not an error** — which is why this is the first thing to check.

### 5.2 Provisioning end to end

In DCB Admin → a library → **Accounts**:

1. The page offers a **Create account** button. If it says provisioning is not configured,
   `DCB_IDENTITY_PROVIDER_TYPE` is unset or the service did not start with it.
2. Create a read-only account for an address you can receive.
3. In Keycloak the user exists, is **enabled**, has `LIBRARY_READ_ONLY`, and has attribute
   `code` set to that library's agency.
4. The invitation email arrives. Following it asks for a new password — **no password was
   ever displayed in DCB Admin**.
5. Signing in as that person reaches DCB Admin for Libraries and shows **only** that
   library's requests.

### 5.3 The containment grant actually contains

**The one test worth doing by hand**, because it is the control that survives everything
else failing — and because it has been observed *not* holding on a realm that looked
correctly configured.

`scripts/keycloak_library_accounts_setup.sh` runs this automatically and exits non-zero if
it fails. To do it yourself, get a token for the `dcb-provisioning` service account, pick
any user, and ask it to map `ADMIN` using the role's real representation:

```
POST /admin/realms/{realm}/users/{someUserId}/role-mappings/realm
[ { "id": "<id of ADMIN>", "name": "ADMIN", "composite": false,
    "clientRole": false, "containerId": "<realm id>" } ]
```

| Expected | |
|---|---|
| mapping `ADMIN` | **403 Forbidden** |
| mapping `LIBRARY_READ_ONLY` | **204 No Content** |

Use the *real* representation taken from an admin session, not one the API offered you —
an attacker would not politely ask which roles are on offer. If the ADMIN mapping succeeds,
containment is not in effect: check that the service account does **not** hold
`manage-users` (§2.2), and that Keycloak was started with `--features=admin-fine-grained-authz`.

### 5.4 The bar refuses the right people

With `DCB_ADMIN_UI_CLIENT_ID` set and `DCB_ADMIN_UI_ACCESS_MODE=ENFORCE`. The service says
which state it is in at first use — look for
`DCB Admin access bar is ENFORCE for client dcb-admin`, or `is OFF` if the client id never
bound.

The whole matrix, measured:

| Account | Client | Result |
|---|---|---|
| `CONSORTIUM_ADMIN` / `ADMIN` | `dcb-admin` | **allowed** |
| holds `LIBRARY_ADMIN` **and** `CONSORTIUM_ADMIN` | `dcb-admin` | **allowed** |
| `LIBRARY_ADMIN` | `dcb-admin` | **403** |
| `LIBRARY_READ_ONLY` | `dcb-admin` | **403** |
| any of the above | `dcb-admin-for-libraries` | **allowed** |

**The third and fifth rows are the ones that matter.** The dual-role account catches an
over-tightened rule — consortium staff are people at libraries and their tokens say so, and
reading the rule as "holds a library role, therefore barred" locks out exactly the
administrators the tool exists for. The last row catches the opposite mistake: this bar is
about one application, and DCB Admin for Libraries must keep working for every role.

**What a refused caller actually receives** is a bare
`{"type":"about:blank","status":403}` with `Content-Type: application/problem+json`. No
reason is sent, deliberately — a refusal owes the caller it is refusing no explanation of
the policy.

**The reason is in the log**, attributed:

```
Denied DCB Admin access: user=library-admin roles=[…, LIBRARY_ADMIN, …]
  client=dcb-admin reason=DCB Admin is a consortium-level tool and this account
  holds no consortium role
```

In `WARN` the identical line reads `Would deny` and nothing is refused. That is the line to
drain before flipping, and an empty warn log is the signal that flipping is safe.

A barred user of DCB Admin itself never sees the 403: the app's own guard redirects them to
`/unauthorised` before any query is sent. That guard is UX; this is the control.
# The Discovery Service Approach

How OpenRS DCB exposes patron-facing functionality to discovery services, why the surface
looks the way it does, and what a third-party discovery vendor has to do to integrate.

Audience: DCB maintainers, deployment operators, and anyone building a discovery layer that
wants to talk to an OpenRS consortium.

---

## 1. The problem this solves

A discovery service needs three things from DCB on behalf of a patron:

1. Show me the libraries in this consortium (for an institution picker, or "nearest to me").
2. Show me this patron's requests, in language a patron can read.
3. Let this patron place a request, and cancel their own request.

Before this work, none of that had a home. Discovery traffic was pointed at the staff API,
patron identity was carried as a role on a token, and the "my requests" endpoint lived on the
same controller as request placement and state-machine rollback. That arrangement had three
distinct defects, and each one drove a change described below.

---

## 2. What changed

### 2.1 A dedicated discovery surface

Everything a discovery service may call now lives under `/discovery/`:

| Route | Auth | Purpose |
|---|---|---|
| `GET /discovery/libraries` | anonymous | Public library directory: code, host LMS code, name, lat/long |
| `GET /discovery/requests` | `DISCOVERY_SERVICE` + patron assertion | The asserted patron's requests, paged |
| `POST /discovery/requests/place` | `DISCOVERY_SERVICE` + patron assertion | Place a request as the asserted patron |
| `POST /discovery/requests/{id}/cancel` | `DISCOVERY_SERVICE` + patron assertion | Cancel the asserted patron's own request |

`DiscoveryPatronRequestsController` is the only place the `DISCOVERY_SERVICE` role appears in
a `@Secured`, and a build-time test enforces that (§4). The staff API is not reachable with a
discovery credential, and no route under `/discovery/` is reachable with anything else.

### 2.2 Caller identity and patron identity were separated

This is the central design decision, so it is worth stating precisely.

A discovery service authenticates with a confidential client credential holding the
`DISCOVERY_SERVICE` role. That proves **who is calling**. It does not, and cannot, prove
**which patron they are calling for**.

The previous model answered that by giving the patron a role (`PATRON`) on their own token.
That is wrong in two directions at once:

- It puts a credential DCB accepts into a patron's browser. Every browser in the consortium
  becomes an attack surface for the consortium's request API.
- It made the patron a principal of the DCB realm, which means every route whose security was
  "any authenticated principal" was reachable by every patron. That included request placement
  as an arbitrary patron, expedited checkout, and state-machine rollback.

So `PATRON` is gone. In its place: the calling service signs a short-lived assertion with its
own key, naming the patron, and DCB verifies it.

```
Authorization: Bearer <client credential>      -> WHO is calling  (Keycloak, DISCOVERY_SERVICE)
X-OpenRS-Patron-Assertion: <signed JWT>        -> WHICH PATRON    (caller's key, DCB-verified)
```

`PatronAssertionVerifier` checks signature, trusted issuer, subject, audience, expiry, `iat`
sanity, and a **DCB-chosen upper bound on lifetime** — an issuer that mints twelve-hour
assertions has built a bearer token, and the replay window is DCB's to bound, not theirs. It
returns a `PatronAssertion` record, which is the only way to obtain a verified patron identity
in the codebase. "I checked" and "I did not check" are now different types the compiler can
see.

The verification mechanism is deliberately the existing `ki-mn-peer-auth` library, the same one
that guards inbound NCIP. A second hand-rolled JWT verifier is a bug with a long fuse. The
*trust store* is separate, though — an NCIP peer must not become a patron-assertion issuer by
accident.

### 2.3 Ownership is enforced in DCB, not delegated

Because DCB derives the patron itself, every discovery route is self-scoped by construction.
No method on the discovery controller accepts a patron identity from a path variable, query
parameter, or request body. On `/discovery/requests/place` the requestor is built from the
verified assertion and the body carries no identity at all.

One SQL predicate defines ownership (`findOwnedRequest`), and the "my requests" query uses the
identical join, so "which requests are mine" and "may I cancel this one" cannot disagree. It
keys on `patron_id` + home identity rather than `requesting_identity_id`, because the latter is
nullable and only populated later in the workflow — joining through it would hide a request
from the patron who just placed it.

A request that is not yours returns **404**, indistinguishable from one that does not exist, so
request ids cannot be probed.

### 2.4 A patron-facing status vocabulary

DCB's internal state machine has ~20 states that are essential to librarians and meaningless to
patrons. `PatronStatusMapper` collapses them into `PatronRequestDiscoveryStatus` — a coarse
enum with prose attached (`READY_FOR_PICKUP`, `IN_TRANSIT`, `COULD_NOT_SUPPLY`, …).

The response carries **both**: the raw `status` for a discovery service that wants to do its
own mapping, and `discoveryStatus` + `statusDescription` for one that doesn't.

The `switch` in the mapper is exhaustive with no `default`. Adding a state to the DCB state
machine breaks the build and forces a conscious decision about what to tell the patron. That is
the intended cost.

`errorMessage` is dropped on the patron path. It is raw internal exception text — LMS
hostnames, API response bodies, internal class names — truncated to 255 characters. A patron
can act on none of it. Staff endpoints retain it, because a librarian can.

### 2.5 Patron-initiated cancellation

`PatronRequestCancellationService` performs exactly the act a patron could perform in their own
OPAC: cancel *their* hold at *their* borrowing system. It never touches the supplier side, and
it does not transition the request — the tracking poll observes the cancelled local hold and
`CancelledPatronRequestTransition` does the state work, exactly as for an OPAC-initiated
cancellation. **The state machine remains the sole owner of transitions.** Hence `202 Accepted`:
`CANCELLED` lands asynchronously, never from the call itself.

Three behaviours worth knowing about as an integrator:

- **Idempotent.** If the local hold is already cancelled, DCB converges without calling the LMS
  again. A patron mashing the cancel button must not become a load test against a member
  library.
- **Never a false success.** `AbstractHostLmsClient.cancelHoldRequest` defaults to a silent
  no-op, which some clients inherit. An empty result is treated as failure (`502`), not
  success. Telling a patron their request is cancelled while the item still ships is the worst
  available outcome.
- **Audited either way.** Confirmed and unconfirmed attempts both write an audit entry
  recording the patron and the asserting service. For a self-service mutation, "who asked" is
  the only question the trail ever gets asked.

### 2.6 Fixes to adjacent surfaces that discovery traffic exposed

- **`PatronRequestController` and `SupplierRequestController` are default-DENY.** Both defaulted
  to `IS_AUTHENTICATED`, which is not a permission — it is a claim about the entire Keycloak
  realm, maintained by people who do not read this codebase. It grew false the moment
  low-trust credentials joined the realm. Both now carry explicit role sets.
- **`PatronRequestResolutionController` was `IS_ANONYMOUS`.** It runs full resolution, which
  makes live availability calls out to member LMS APIs, and returns item-level holdings across
  the consortium. An unauthenticated `POST` was an amplification vector into libraries we do
  not own, aimable by anybody. Now staff and interop-tester roles only.
- **Barcode lookup matched substrings.** `local_barcode LIKE '%barcode%'` meant a barcode of
  `1` returned every patron at that Host LMS whose barcode contained a `1`, with their titles
  and pickup locations. Now an exact element match against the parsed barcode list.
- **The library directory was 2N+1 queries.** The controller resolved the Host LMS code per
  agency through an unbounded `flatMap` on an anonymous endpoint — a 200-agency consortium meant
  ~401 R2DBC acquisitions per unauthenticated GET, and a trivial request loop drained the
  connection pool out from under the state machine. It is now one indexed query.
- **The directory lists borrowing-enabled agencies by default.** A patron from a non-borrowing
  agency cannot place a request, so the picker does not advertise one. `includeAll=true` returns
  the full directory.

---

## 3. Why: the reasoning in one paragraph

DCB holds real authority — it places holds in production library systems, checks items out, and
moves physical stock between institutions. Any design in which an external party tells DCB
which patron to spend that authority on is the confused-deputy problem, and no amount of careful
coding at the call sites fixes it. The only durable answer is that DCB derives the patron from
something it verified, and answers "is this yours?" itself. Everything above follows from that
one commitment.

---

## 4. Secure by Design compliance

**Secure defaults.** `dcb.discovery.enabled` is `false`. A deployment that has not been
configured for discovery accepts no patron assertions at all and fails closed on every
`/discovery/requests` call — it does not quietly behave as though it has been configured.
The trusted-services list is empty by default. Duplicate issuers refuse to start rather than
picking a winner, because two services claiming one issuer means either can mint the other's
assertions.

**Least privilege.** `DISCOVERY_SERVICE` buys the patron self-service surface and nothing else.
It is confined to `/discovery/` by construction, and `/discovery/` is reachable by nothing else.
Within that surface, a credential can only act on patrons it can cryptographically vouch for.
(One pre-existing integration sits outside this model on an `ADMIN` service credential — §8 states
that exception and what it costs.)

**Complete mediation.** Every discovery method calls `assertionVerifier.verify(request)` before
doing anything, and the verified `PatronAssertion` is the only source of patron identity
available to it. There is no code path that reaches patron-scoped data with an unverified
identity, because there is no way to construct the type that carries one.

**Make the invalid state unrepresentable.** This is the part that matters most and is easiest to
skip. `ApiSecurityArchitectureTests` reads Micronaut's compiled bean definitions at build time —
no database, no container, milliseconds — and fails the build when:

- a route is open to every authenticated principal without an explicit `@OpenToAllPrincipals`
  justification;
- a justification is blank;
- **any** mutating route is open to every principal, justification or not;
- `DISCOVERY_SERVICE` appears on a route outside `/discovery/`;
- a route under `/discovery/` is secured by anything other than `DISCOVERY_SERVICE` (or
  anonymous, for the public directory);
- any staff request route is anonymous, open to all principals, or ungated;
- a patron-held role (`PATRON`, `PATRON_*`) reappears in any `@Secured`.

The test also asserts it can still see the controllers, so a refactor cannot turn it into a
silent no-op. `@Secured(IS_AUTHENTICATED)` is not banned — it is made *visible*, so it becomes a
decision a reviewer sees in a diff rather than a default nobody noticed.

**Defence in depth.** Signature, issuer, subject, audience, `exp`, `iat` sanity, and a
DCB-enforced lifetime cap are all checked independently. Ownership is then re-checked in SQL at
the point of use, not inferred from the fact that authentication succeeded.

**Fail securely, and say nothing useful while doing it.** One exception type covers every
assertion rejection: a caller learns its assertion was not accepted, not which check it tripped.
Not-yours and not-found are the same 404. Patron-facing errors carry patron vocabulary; the raw
state and the internal detail go to the log and the audit trail.

**Auditability as a first-class output.** Every patron-initiated mutation records the patron and
the asserting service. Onboarding a discovery service is a reviewable configuration change —
deliberately not a runtime admin screen — so the decision leaves a trail wherever that
configuration is version-controlled. See §7 for what that means in each hosting environment.

---

## 5. How this makes DCB discovery-agnostic

The goal is that DCB has no idea which discovery product is talking to it, and gains nothing by
knowing.

- **The contract is the integration.** There is no discovery-specific code path, no per-vendor
  adapter, no branch anywhere in DCB that asks which system is calling. There is an HTTP API,
  a role, and an assertion format. Two implementations that satisfy them are indistinguishable
  to DCB.
- **Trust is configuration, not code.** Onboarding is a `trusted-services` entry: a service id,
  an issuer, a JWKS URL. Adding the second, fifth, or twentieth discovery service requires no
  DCB change, no release, and no schema migration.
- **The identity model assumes nothing about the caller's architecture.** DCB requires only
  that the caller can authenticate itself and sign a short-lived assertion. How the caller
  establishes patron identity — SAML, OIDC, an ILS session, a PIN check at the desk — is
  entirely its own business, and DCB is deliberately incurious about it.
- **The response shape serves both kinds of consumer.** A discovery service with strong opinions
  about status presentation reads the raw `status`. One that wants to render and move on reads
  `discoveryStatus` and `statusDescription`. Neither is privileged, and the mapping is not
  tuned for any particular UI.
- **The public directory is genuinely public.** Code, host LMS code, name, coordinates. Enough
  to build an institution picker or resolve a nearest library; nothing that maps the
  consortium's internals. Anyone can build against it without an onboarding conversation first.
- **The security model does not assume a trusted caller.** Because ownership is enforced inside
  DCB, a third-party discovery service is not a more dangerous integration than a first-party
  one. That is what makes "any discovery service" a viable position rather than a slogan.

---

## 6. For discovery service vendors: adding OpenRS DCB support

If you build a discovery layer and want it to place and track OpenRS consortial requests, this
section is the whole integration.

### 6.1 What you will need from the consortium's DCB operator

- The base URL of the DCB deployment.
- A Keycloak client credential (client id + secret) for your service, granted the
  `DISCOVERY_SERVICE` role. **Confidential client, server-side only.** This credential must
  never reach a browser.
- The `audience` value that deployment expects in patron assertions (`dcb` by default).
- Confirmation of the deployment's `max-assertion-lifetime` (`PT2M` in the shipped defaults).

### 6.2 What the operator will need from you

An entry in `dcb.discovery.trusted-services`. The operator may hold this as YAML or as the
equivalent JSON in a single environment variable (§7.2) — the fields are the same either way:

```yaml
dcb:
  discovery:
    enabled: true
    audience: dcb
    max-assertion-lifetime: PT2M
    trusted-services:
      - service-id: your-service-id
        issuer: https://discovery.example.org
        jwks-uri: https://discovery.example.org/.well-known/jwks.json
```

So, concretely: a stable service id, the `iss` value you will put in your assertions, and a
JWKS endpoint publishing your public signing keys. Key rotation at the same URL is picked up
automatically; a change of issuer or JWKS URL is a configuration change and should be reviewed
as one.

Your JWKS endpoint must be reachable from the DCB deployment. If it sits behind an IP
allow-list, say so during onboarding — the operator can supply a stable egress address (§7.6).

How the operator actually applies this configuration depends on how their DCB is hosted; §7
covers the Kubernetes and AWS Fargate routes.

Generate an asymmetric signing key (RSA or EC). The private key stays in your backend. DCB never
sees it and never needs to.

### 6.3 Minting a patron assertion

Every call that acts on a patron carries one, signed with your key:

**Header:** `alg` (your signing algorithm), `kid` (matching a key in your JWKS)

**Claims:**

| Claim | Value |
|---|---|
| `iss` | your configured issuer, exactly |
| `sub` | your configured `service-id` — the *service*, not the patron |
| `aud` | the deployment's configured audience |
| `iat` | now (must not be in the future; 60s forward tolerance) |
| `exp` | `iat` + your chosen lifetime, **not exceeding** `max-assertion-lifetime` |
| `localSystemCode` | the patron's home Host LMS code, as DCB knows it |
| `localSystemPatronId` | the patron's local id in that system |

Mint one per request, or per short-lived patron session. Do not cache them for hours; DCB will
reject an over-long lifetime outright, and the bound exists precisely so that a stolen assertion
is worth almost nothing.

`localSystemCode` and `localSystemPatronId` are the patron identity contract. Get them from
whatever authentication you already perform against the patron's home library system. **You are
asserting that you authenticated this patron.** DCB verifies that the assertion is genuinely
yours and unexpired; it cannot verify your login flow, so the strength of the whole chain is the
strength of that flow.

### 6.4 Making calls

Two headers on every patron-scoped call:

```http
POST /discovery/requests/place HTTP/1.1
Authorization: Bearer <your Keycloak access token>
X-OpenRS-Patron-Assertion: <your signed patron assertion>
Content-Type: application/json

{
  "bibClusterId": "…",
  "pickupLocationCode": "…",
  "volumeDesignator": null,
  "homeLibraryCode": null,
  "agencyCode": null,
  "requesterNote": null
}
```

Note what the body does *not* contain: a requestor. You cannot name the patron you are
requesting for — only assert them and be verified. `homeLibraryCode` and `agencyCode` are hints
DCB may resolve for itself; they carry no authority.

**`GET /discovery/requests?number=0&size=100`** returns a page of the asserted patron's requests.
Each carries `id`, raw `status`, `discoveryStatus`, `statusDescription`, `outcome`,
`nextExpectedStatus`, `timeInState`, `title`, `pickupLocationCode`, `pickupLocationName`,
`activeWorkflow`, `bibClusterId`, `dateCreated`, `dateUpdated`. `activeWorkflow` of `RET-LOCAL`
means a same-library hold, which you will usually want to present separately from consortial
requests.

**`POST /discovery/requests/{patronRequestId}/cancel`** cancels the patron's own request.

**`GET /discovery/libraries`** needs no authentication at all. Add `?includeAll=true` for
non-borrowing agencies. Coordinates may be null — filter accordingly if you are doing
nearest-library resolution.

### 6.5 Responses to handle

| Status | Body | Meaning |
|---|---|---|
| `200` | payload | Fine. |
| `202` | `{"id": …}` | Cancellation accepted. `CANCELLED` lands asynchronously — poll `GET /discovery/requests`, do not assume it is immediate. |
| `400` | `{"failedChecks": [...]}` | Preflight rejection on placement: bad pickup location, patron not eligible, and so on. These are actionable and should be shown to the patron. |
| `401` | `{"code": "INVALID_PATRON_ASSERTION"}` | Your assertion was not accepted. Deliberately does not say why. Check issuer, audience, `kid`, clock skew, and lifetime. |
| `404` | — | No such request *for this patron*. Not-yours and not-found are the same answer by design. |
| `409` | `{"code": "NOT_CANCELLABLE", "discoveryStatus": …}` | Too late to cancel. `discoveryStatus` tells you where it got to. |
| `502` | `{"code": "CANCELLATION_NOT_CONFIRMED"}` | The borrowing system did not confirm. **The hold may still be live.** Do not tell the patron it is cancelled — direct them to library staff. |

### 6.6 Rendering status

Either read `discoveryStatus` + `statusDescription` and render them, or read the raw `status`
and map it yourself. If you map it yourself, handle unknown values gracefully: DCB's state
machine gains states, and your integration should not break when it does. The
`discoveryStatus` vocabulary is the stable one; the raw `status` is the precise one.

### 6.7 Operational courtesy

- Rate-limit yourself. Behind these endpoints are real member library systems with real
  API budgets, some of them decades old.
- Do not poll `GET /discovery/requests` aggressively per patron session. Requests move on the
  timescale of physical stock in vans.
- Treat cancellation as idempotent from your side too; DCB will converge, but the network will
  not always tell you it did.

### 6.8 Testing your integration

`dcb.discovery.trusted-services[].jwks` accepts an inline JWKS, which is how DCB's own tests
onboard a service without an HTTP endpoint. The same mechanism works for an air-gapped or
local-development deployment, so you can exercise the full assertion path without publishing a
JWKS URL first.

---

## 7. Onboarding in practice: deployment mechanics

§6 is what a vendor does. This is what the *operator* does, and it is the part that differs
between hosting environments.

Onboarding has three independent parts. Only the last two care where DCB runs:

1. **The Keycloak client** — identical everywhere.
2. **The `dcb.discovery` configuration** — the part that actually differs.
3. **Network egress to the vendor's JWKS URL** — different mechanics, same requirement.

Routes by hosting environment: **§7.3** Kubernetes, **§7.4** non-Kubernetes (Docker, systemd,
generic schedulers), **§7.5** AWS ECS/Fargate. §7.2 applies to all three.

### 7.1 Part one: Keycloak (environment-independent)

Create a **confidential** client for the vendor with service accounts enabled, and grant it the
`DISCOVERY_SERVICE` role. Hand the client id and secret over a channel that is not email.

Nothing about this step depends on how DCB is hosted. Keycloak is a separate system, and a
discovery service's credential is issued there whether DCB runs on Kubernetes, Fargate, or a
laptop. Nor does it need new DCB wiring: DCB validates the bearer token against `KEYCLOAK_CERT_URL`
exactly as it does for every other role, and `DISCOVERY_SERVICE` is just a role in that realm.
If that variable is already set for staff traffic, discovery authentication needs nothing further.

### 7.2 Part two: everything can be an environment variable

The whole `dcb.discovery` block is deliverable through the environment, which means a
deployment with no way to mount a config file is fully supported. The scalars and the list
work differently, though, and the difference is not arbitrary:

| Environment variable | Config key | Type | Default | Required |
|---|---|---|---|---|
| `DCB_DISCOVERY_ENABLED` | `dcb.discovery.enabled` | boolean | `false` | yes, to serve discovery at all |
| `DCB_DISCOVERY_AUDIENCE` | `dcb.discovery.audience` | string | `dcb` | only if not `dcb` |
| `DCB_DISCOVERY_MAX_ASSERTION_LIFETIME` | `dcb.discovery.max-assertion-lifetime` | ISO-8601 duration | `PT2M` | no |
| `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` | `dcb.discovery.trusted-services` | JSON array (string) | empty | yes, unless supplied as YAML |

Two further variables are not discovery-specific but decide whether the above arrives at all:

| Environment variable | Why discovery cares |
|---|---|
| `MICRONAUT_CONFIG_FILES` | Comma-separated paths to external config files. The only way a YAML `trusted-services` list reaches the process. Ignore it if you use the JSON variable. |
| `KEYCLOAK_CERT_URL` | Validates the discovery service's bearer token. Without it every `/discovery/requests` call is a `401` before assertions are even considered. |

The three discovery scalars are declared in `application.yml` as `${DCB_DISCOVERY_*}`
placeholders and bind directly.

The list needs the JSON form because **a list of objects cannot be expressed as indexed
environment variables at all**. Micronaut maps an environment variable onto every dot/hyphen
permutation of its name, but an index stays a *dot* segment:
`DCB_DISCOVERY_TRUSTED_SERVICES_0_SERVICE_ID` resolves to
`dcb.discovery.trusted-services.0.service-id`, never the `trusted-services[0].service-id` form
that list binding reads. So `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` takes the entire list as a
JSON array in one value, using **the same kebab-case keys as the YAML**:

```
DCB_DISCOVERY_TRUSTED_SERVICES_JSON=[{"service-id":"their-service-id","issuer":"https://discovery.example.org","jwks-uri":"https://discovery.example.org/.well-known/jwks.json"}]
```

Inline `jwks` works here too, for the egress-less case in §7.6.

**If the variable is absent, empty, or blank, that is "not configured", not an error.** The setter
is simply never called, the JSON contribution is an empty list, and the effective trust anchor is
whatever the YAML supplied — nothing, in a default deployment. DCB starts normally, logs
`accepted from 0 trusted service(s): []`, keeps serving `GET /discovery/libraries` (which needs no
assertion), and rejects every patron-scoped discovery call with `401 INVALID_PATRON_ASSERTION` —
the same answer it gives when `dcb.discovery.enabled` is `false`. Fails closed, stays up, and tells
you so in one log line. Only *malformed* JSON is fatal.

Both forms feed one effective list, so you can use either or both, and the duplicate-issuer
check in §7.8 covers a collision *between* them. Malformed JSON fails the application at
startup rather than binding an empty list — a trust anchor that silently binds nothing is
indistinguishable from a deployment nobody configured, which is the one outcome worth crashing
to avoid. (`DiscoveryServicePropertiesBindingTests` pins all of this, including the indexed-env-var
limitation, so nobody removes the JSON route on the assumption that indices would do.)

Note also that the trust anchor contains **no secrets**. A service id, an issuer URL and a JWKS
URL are all public information; the vendor's *private* key never comes near DCB. Treat this as
configuration to be reviewed, not as a credential to be hidden.

### 7.3 Route A: Kubernetes deployments

Config comes from a ConfigMap, held in whatever repository holds this deployment's manifests.
Two shapes work; pick one and be consistent.

#### A1: the trust anchor as a mounted YAML document

Preferred when you want the trust anchor to read as a document in review — which, for a list of
who may assert patrons, is usually worth the extra mount.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: dcb-discovery-config
data:
  discovery.yml: |
    dcb:
      discovery:
        enabled: true
        audience: dcb
        max-assertion-lifetime: PT2M
        trusted-services:
          - service-id: their-service-id
            issuer: https://discovery.example.org
            jwks-uri: https://discovery.example.org/.well-known/jwks.json
```

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dcb-service
spec:
  strategy:
    rollingUpdate:
      maxUnavailable: 0        # see the failure mode below
  template:
    metadata:
      annotations:
        # Changes when the ConfigMap changes, which is what actually triggers the roll.
        # Helm: sha256sum of the rendered ConfigMap. Kustomize: use a generator, which
        # renames the ConfigMap and rolls the Deployment for you.
        checksum/discovery-config: "<sha256 of discovery.yml>"
    spec:
      containers:
        - name: dcb
          env:
            - name: MICRONAUT_CONFIG_FILES
              value: /config/bootstrap.yml,/config/discovery.yml
          volumeMounts:
            - name: discovery-config
              mountPath: /config/discovery.yml
              subPath: discovery.yml
      volumes:
        - name: discovery-config
          configMap:
            name: dcb-discovery-config
```

`MICRONAUT_CONFIG_FILES` takes a comma-separated list, so `discovery.yml` layers alongside the
existing `bootstrap.yml` (conventionally mounted at `/bootstrap.yml`, per the README) rather than
being merged into it. Adjust the paths to whatever this deployment already uses — the point is
that every file you rely on is named in that variable. A file that exists on disk and is not
listed there is simply not read.

**`subPath` mounts never receive ConfigMap updates.** A plain directory mount is refreshed by the
kubelet eventually; a `subPath` mount is not refreshed at all, ever, for the life of the pod. Since
DCB reads its configuration once at boot regardless, this changes nothing about correctness — but
do not let anyone talk you out of the rollout on the grounds that the mount will catch up. It will
not.

#### A2: the trust anchor as an environment variable

Better if your ConfigMap already carries the rest of DCB's environment and you would rather not
administer a second mount. Put all four values with the other `DCB_*` entries and skip the file:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: dcb-env
data:
  DCB_DISCOVERY_ENABLED: "true"
  DCB_DISCOVERY_AUDIENCE: "dcb"
  DCB_DISCOVERY_MAX_ASSERTION_LIFETIME: "PT2M"
  DCB_DISCOVERY_TRUSTED_SERVICES_JSON: |-
    [{"service-id":"their-service-id","issuer":"https://discovery.example.org","jwks-uri":"https://discovery.example.org/.well-known/jwks.json"}]
```

consumed with `envFrom: [{ configMapRef: { name: dcb-env } }]`. Use YAML's `|-` block scalar rather
than a quoted one-liner: it keeps the JSON readable in a diff without any escaping, and strips the
trailing newline that would otherwise ride along into the value.

#### Then roll the deployment

```
kubectl rollout restart deployment/dcb-service
kubectl rollout status  deployment/dcb-service
```

**This step is not optional and is the classic way to get this wrong.** Editing a ConfigMap does
not restart pods. Without a rollout — or a checksum annotation that changes when the ConfigMap
does — the onboarding takes effect whenever pods next happen to restart, for unrelated reasons,
at an unpredictable time. That is worse than it not working at all, because staging looks fine
and production changes behaviour hours later.

GitOps controllers do not save you here either: syncing a changed ConfigMap is not a restart. If
Argo CD or Flux manages this deployment, the checksum annotation (or a Kustomize `configMapGenerator`,
which renames the object on every content change) is what turns a config commit into a rollout.

Failure mode: a bad entry (§7.8) means new pods crash-loop on startup. With `maxUnavailable: 0`
the existing ReplicaSet keeps serving and the rollout stalls, which is exactly the behaviour you
want — a broken trust anchor should never take the service down with it.

### 7.4 Route B: non-Kubernetes deployments

Plain Docker, systemd on a VM, or any scheduler with no ConfigMap. There is nowhere obvious to put
a file, so the environment carries the configuration. **AWS ECS/Fargate is the most common member
of this family and gets its own section (§7.5)**; the routes below are the generic form of the same
choices.

#### B1 (recommended): environment variables only

This is why `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` exists. The entire trust anchor is four
environment variables, set wherever that platform sets them — an ECS task definition, a compose
file, a systemd unit's `EnvironmentFile`:

```
DCB_DISCOVERY_ENABLED=true
DCB_DISCOVERY_AUDIENCE=dcb
DCB_DISCOVERY_MAX_ASSERTION_LIFETIME=PT2M
DCB_DISCOVERY_TRUSTED_SERVICES_JSON=[{"service-id":"their-service-id","issuer":"https://discovery.example.org","jwks-uri":"https://discovery.example.org/.well-known/jwks.json"}]
```

No mounted volume, no entrypoint scripting, no extra IAM. Onboarding a service is an edit to
that one JSON value and a restart.

Keep the JSON in version control as a formatted file and minify it into the variable, rather
than hand-editing a single long line. The value is the trust anchor; it deserves a diff.

Watch the quoting, because it differs by platform and the failure is silent-ish:

- **`docker run -e` / `docker compose`** — no shell involved, so the value passes through
  verbatim. In a compose file, quote it as a YAML string or use a `|-` block scalar.
- **systemd `EnvironmentFile`** — one `KEY=value` line, no shell quoting, no line continuations.
  Do not wrap the JSON in single quotes: systemd strips them and you will spend an afternoon on it.
- **`.env` files** — dotenv parsers vary in how they treat `"` and `#`. Test with the startup log
  line in §7.8 rather than assuming.

#### B2: a config file fetched at container start

Preferable when the trust anchor is large, shared between services, or you specifically want it
reviewed as YAML rather than JSON. Put `discovery.yml` in versioned object storage, and have
the entrypoint fetch it before exec'ing the application:

```sh
set -euo pipefail                      # the fetch MUST be able to fail the container
aws s3 cp "s3://dcb-config/${ENVIRONMENT}/discovery.yml" /tmp/discovery.yml
export MICRONAUT_CONFIG_FILES=/bootstrap.yml,/tmp/discovery.yml
exec java -jar /app/dcb.jar
```

Grant whatever identity the container runs as read access to that object; on ECS that is the
**task role**, not the execution role (§7.5). Object versioning gives you the audit trail.

The fetch must fail the container if it fails — hence `set -e`, and hence `exec` only after the
fetch. `MICRONAUT_CONFIG_FILES` pointing at a file that was never written is not graceful
degradation: it is a deployment that comes up with an empty trust list, rejects every discovery
call, and passes its health check while doing it.

A mounted network filesystem (EFS on Fargate, an NFS mount on a VM) is a variant of the same
idea, and swaps the entrypoint scripting for a mount to administer. Reach for it only if that
mount already exists for other reasons.

#### B3: a distributed configuration backend

Already wired in this codebase: `micronaut-aws-secretsmanager` is a dependency,
`micronaut.config.bootstrap: true` is set, and `dcb/src/main/resources/bootstrap.yml` carries
commented templates for AWS Secrets Manager and HashiCorp Vault. Enable one there and grant the
runtime whatever read permission it needs.

Reasonable if your organisation's policy is "all runtime configuration flows through the secret
store" — though note again that none of this data is secret. Two caveats, both real:

- **Env vars win.** As the README notes, Micronaut reads environment variables *last*, so they
  override distributed config. If you move `dcb.discovery` into a secret store, remove the
  `DCB_DISCOVERY_*` variables from the runtime or they will silently take precedence.
- **Verify the list flattening in staging first.** How a secret's key/value pairs map onto a
  nested list of objects is a property of the connector, not of DCB. Confirm against the startup
  log line in §7.8 before relying on it. B1 and B2 have no such ambiguity, which is why they
  rank higher.

  The way to sidestep the ambiguity entirely is to store the *whole JSON array* as a single
  secret value and inject it as `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` — one string in, one string
  out, no flattening involved. On ECS that is a `secrets` entry (§7.5) and it is the recommended
  shape.

#### B4 (rejected): bake it into the image

Onboarding a discovery service should not require a release, and a trust anchor buried in an
image layer cannot be reviewed or rolled back independently of the code. Do not do this.

#### Configuration is read once, at boot

None of these routes reload live. Editing an object in storage, a file on a mount, or a secret
has **no effect on a running process** — you must restart it. This is the non-Kubernetes
equivalent of the ConfigMap trap in §7.3: a broken edit lies dormant until something restarts
the service for an unrelated reason, and then it bites.

Where the platform offers automatic rollback on failed deployment, turn it on. A duplicate issuer
refuses to start (§7.8), so a bad edit fails health checks; without rollback the scheduler keeps
retrying and you have an outage instead of a reverted change.

### 7.5 Route C: AWS ECS on Fargate

The B1 shape (§7.4) with AWS-specific mechanics. Fargate has no ConfigMap and no host filesystem
to mount, so the task definition *is* the configuration — which is a feature: every revision is
immutable, numbered, diffable, and rollback-able, which is more than most config files manage.

#### The task definition

```json
{
  "family": "dcb-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "2048",
  "memory": "4096",
  "executionRoleArn": "arn:aws:iam::123456789012:role/dcb-task-execution",
  "taskRoleArn": "arn:aws:iam::123456789012:role/dcb-task",
  "containerDefinitions": [
    {
      "name": "dcb",
      "image": "…/knowledgeintegration/dcb:<tag>",
      "portMappings": [{ "containerPort": 8080, "protocol": "tcp" }],
      "environment": [
        { "name": "DCB_DISCOVERY_ENABLED", "value": "true" },
        { "name": "DCB_DISCOVERY_AUDIENCE", "value": "dcb" },
        { "name": "DCB_DISCOVERY_MAX_ASSERTION_LIFETIME", "value": "PT2M" },
        {
          "name": "DCB_DISCOVERY_TRUSTED_SERVICES_JSON",
          "value": "[{\"service-id\":\"their-service-id\",\"issuer\":\"https://discovery.example.org\",\"jwks-uri\":\"https://discovery.example.org/.well-known/jwks.json\"}]"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/dcb-service",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "dcb"
        }
      }
    }
  ]
}
```

Four things about that block, in the order they bite:

- **It is JSON inside JSON, so every quote is escaped.** Hand-editing this is how you get a
  malformed trust anchor. Generate it: Terraform `jsonencode(local.trusted_services)`,
  CloudFormation `Fn::ToJsonString`, or `jq -c . discovery-services.json` in your pipeline from a
  pretty-printed file that lives in version control. Keep the readable file as the source of truth
  and never edit the escaped form directly.
- **Malformed JSON is a startup crash, by design** (§7.2). On Fargate that means a task that
  starts, fails, and gets replaced — see the circuit breaker below, or ECS will do it forever.
- **`awslogs` is not optional here.** The three startup lines in §7.8 are the only confirmation
  that an onboarding took effect, and a Fargate task with no log driver takes them to the grave.
- **Environment variables are visible to anyone with `ecs:DescribeTaskDefinition`.** Fine for this
  data — the trust anchor is a service id and two URLs, all public (§7.2) — but do not get in the
  habit and put a database password beside it.

#### Keeping the anchor in Secrets Manager instead

If policy says runtime configuration flows through the secret store, put the whole JSON array in
one secret and inject it as the same variable:

```json
"secrets": [
  {
    "name": "DCB_DISCOVERY_TRUSTED_SERVICES_JSON",
    "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789012:secret:dcb/discovery-trusted-services"
  }
]
```

The ECS agent resolves this at task start and hands it to the container as an ordinary environment
variable, which means it wins over any distributed-config value (§7.4 B3) — and, being one opaque
string, involves no list flattening to get wrong. This is the recommended way to reconcile "must
be in the secret store" with "must actually bind".

Grant `secretsmanager:GetSecretValue` on that secret to the **execution role**, not the task role.
The agent fetches it before your code runs, so it is the execution role that needs the permission;
the task role governs what the running container can reach (S3 in §7.4 B2, for instance). Confusing
the two produces a `ResourceInitializationError` at task start, before a single application log line
appears.

Note that updating a secret does **not** restart tasks. Force a new deployment, exactly as with the
ConfigMap trap in §7.3 — `aws ecs update-service --cluster … --service dcb --force-new-deployment`.

#### Networking (`awsvpc`), health checks, and rollback

- **Egress.** Each task gets its own ENI, so the JWKS fetch (§7.6) leaves through the task's own
  security group. It needs an egress rule permitting 443 to the vendor's host, and a route to the
  internet: private subnets plus a NAT gateway, or public subnets with `assignPublicIp: ENABLED`.
  Prefer the former; the NAT gateway's Elastic IP is also the stable source address a vendor needs
  if their JWKS endpoint is behind an allow-list.
- **Health check.** DCB serves `/health` anonymously on Micronaut's default port 8080, unless
  `MICRONAUT_SERVER_PORT` says otherwise. Prefer the ALB target group
  health check on that path over a container `healthCheck` — Fargate task definitions have no shell
  utilities guaranteed in the image, and `CMD-SHELL curl …` fails permanently if `curl` is not
  there. Allow a generous startup grace period: DCB runs Flyway migrations before it serves.
- **Deployment circuit breaker.** Turn it on, with rollback:
  `"deploymentConfiguration": { "deploymentCircuitBreaker": { "enable": true, "rollback": true } }`.
  A duplicate issuer or malformed JSON refuses to start (§7.8), so the new tasks never pass health
  checks. With the circuit breaker you get an automatic revert to the previous task definition
  revision; without it, ECS retries the broken revision indefinitely and a bad onboarding edit
  becomes an outage.
- **Clocks are handled for you.** Fargate tasks use Amazon Time Sync, so the tight `iat`/`exp`
  window in §7.7 is not your problem on this platform. It may still be the vendor's.

### 7.6 Part three: egress to the vendor's JWKS URL

DCB fetches the vendor's public keys from `jwks-uri` over HTTPS and caches them for **15 minutes**
(`DiscoveryTrustedServiceStore`, not configurable). This is an outbound call to a host named in
configuration, and it has to be possible.

| | Kubernetes | Docker / systemd / VM | ECS on Fargate |
|---|---|---|---|
| Allow egress | NetworkPolicy permitting 443 to the vendor host, plus whatever egress gateway applies | host firewall / outbound proxy | task security group egress rule on 443 |
| Route to internet | cluster-dependent | whatever the host has | private subnet + NAT gateway, **or** public subnet with `assignPublicIp: ENABLED` |
| Stable source IP for the vendor's allow-list | egress gateway / SNAT address, if the cluster has one | the host's address, or its NAT | NAT gateway Elastic IP — stable, and easy to hand to the vendor |

Private subnets plus a NAT gateway is the right default on Fargate. It costs more than a public
subnet and it is worth it: DCB reaches out, nothing reaches in, and the NAT gateway's Elastic IP
gives the vendor a fixed address to allow-list if their JWKS endpoint is restricted.

If egress leaves through an HTTP proxy rather than a gateway, note that the fetch is a plain
outbound HTTPS call from the JVM — `JAVA_TOOL_OPTIONS=-Dhttps.proxyHost=… -Dhttps.proxyPort=…`,
and the vendor's host must not be in `http.nonProxyHosts`. A first onboarding that fails only on
the assertion step, with everything else healthy, is very often this.

**If egress genuinely is not available**, use the inline JWKS escape hatch — `jwks` instead of
`jwks-uri`, with the vendor's public key set pasted in:

```yaml
dcb:
  discovery:
    trusted-services:
      - service-id: their-service-id
        issuer: https://discovery.example.org
        jwks:
          keys:
            - { kty: RSA, kid: "…", n: "…", e: AQAB }
```

`jwks` takes precedence over `jwks-uri`. The cost is that you now own key rotation manually: when
the vendor rotates, your deployment breaks until you paste the new key. Only take this trade in a
deployment that truly cannot egress.

### 7.7 Clock skew

Assertion lifetimes are short by design (`PT2M` by default) and an `iat` more than 60 seconds in
the future is rejected outright. That makes clock discipline an operational requirement rather
than a nicety.

Managed container runtimes generally handle this for you — ECS/Fargate tasks get Amazon Time
Sync automatically, for instance. Self-managed Kubernetes nodes and VMs need NTP configured. In
practice the clock that causes trouble is the **vendor's**, not yours — an integration that
fails only for some callers, intermittently, is almost always this. Worth naming in the
onboarding conversation.

### 7.8 Verifying that an onboarding worked

DCB tells you three things at startup, and they are the same in every environment — only the
place you read them differs (`kubectl logs deploy/dcb-service`, `docker logs`, `journalctl -u dcb`,
or the CloudWatch log group named in the task definition):

- **Success.** `DiscoveryTrustedServiceStore` logs at INFO:
  `Discovery patron assertions accepted from N trusted service(s): [...]`. If N is 0, or your
  new service is not in the list, the configuration did not reach the process — check
  `MICRONAUT_CONFIG_FILES` or `DCB_DISCOVERY_TRUSTED_SERVICES_JSON`, and the precedence rules in
  §7.4 B3, before suspecting anything else.
- **Incomplete entry.** An entry missing `service-id`, `issuer`, or both of `jwks-uri`/`jwks` is
  logged at ERROR and **skipped**. The service starts normally and rejects that vendor's calls.
  This is the failure mode that looks like nothing happened, so check the count.
- **Duplicate issuer.** Two entries sharing an issuer throw at bean construction and the
  application does not start. Deliberate: two services on one issuer means either can mint the
  other's assertions, and picking a winner silently would be worse than failing loudly.

Then smoke-test the surface. All three of these should hold before you tell the vendor they are
live:

```sh
# 1. The public directory answers at all — no credential, no assertion.
curl -sS "$DCB/discovery/libraries" | head

# 2. A valid bearer with NO assertion header is rejected, and rejected for the right reason.
curl -sS -o - -w '%{http_code}\n' \
  -H "Authorization: Bearer $TOKEN" "$DCB/discovery/requests"
#    -> 401 {"code":"INVALID_PATRON_ASSERTION"}

# 3. The same call WITH the vendor's assertion succeeds.
curl -sS -o - -w '%{http_code}\n' \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-OpenRS-Patron-Assertion: $ASSERTION" "$DCB/discovery/requests"
#    -> 200
```

Check (2) explicitly rather than assuming: a `401` from a missing or wrong `KEYCLOAK_CERT_URL`
looks similar from the outside but is a different fault, and the `INVALID_PATRON_ASSERTION` code in
the body is what distinguishes "your token was accepted, your assertion was not" from "your token
never got in".

### 7.9 Offboarding and rotation

- **Key rotation at the same JWKS URL is automatic.** The vendor publishes the new key, DCB picks
  it up when its 15-minute JWKS cache expires (§7.6). No configuration change, no deployment. Tell
  the vendor that number: a rotation that removes the old key before DCB's cache has turned over
  fails assertions for up to fifteen minutes, so they must publish both keys and retire the old one
  afterwards.
- **Changing `issuer` or `jwks-uri` is a re-onboarding.** It moves the trust anchor, so review it
  the way you reviewed the original — not as a routine config tweak.
- **Offboarding is removing the entry and redeploying.** Until the deployment rolls, the vendor
  can still act. If you need it immediate, set `dcb.discovery.enabled=false` and roll, which
  closes the whole discovery surface rather than one vendor's access to it.
- **Revoking the Keycloak client is the faster lever**, and it is the one to reach for in an
  incident. It takes effect as soon as the vendor's current access token expires, without touching
  DCB's configuration or restarting anything. Do that first, then remove the trust anchor entry at
  the next deployment. A vendor whose Keycloak credential is gone cannot call `/discovery/` at all,
  however good their assertions are.

Whatever the change, it lands only when the process restarts:

| Environment | Roll it |
|---|---|
| Kubernetes | `kubectl rollout restart deployment/dcb-service` (plus the checksum annotation, §7.3) |
| ECS / Fargate | new task definition revision, then `aws ecs update-service --force-new-deployment` |
| Docker Compose | `docker compose up -d --force-recreate dcb` |
| systemd | edit the `EnvironmentFile`, then `systemctl restart dcb` |

---

## 8. The one exception: EBSCO Locate

Everything above describes the model DCB is moving to. One production integration predates it,
does not fit it, and is not going to change. This section covers why that is.

### 8.1 What it is

EBSCO Locate is a live discovery deployment that calls dcb-service directly using a **Keycloak
service account holding an ADMIN role**. It names the patron in the request body, the way the
staff API has always allowed. It is closed-source and outside OpenRS: it may adopt the approach above in future, but that's not something we can control.

### 8.2 What the exception actually costs

- An `ADMIN` credential can place a request naming **any** patron, and can read any patron's
  requests by barcode. It is the consortium's highest-privilege role,  and the EBSCO Locate exception grants no authority the role did not
  already hold. It is a *credential-handling* risk — one long-lived, high-privilege secret in third party's infrastructure — not an authorisation-model hole.
- The blast radius is therefore the credential itself. An ADMIN credential leaking is considerably worse than a leaked `DISCOVERY_SERVICE` credential, which buys only the
  self-service surface and still cannot name a patron. Our partners at EBSCO therefore protect this credential and are aware of its importance.

### 8.3 The surfaces Locate uses, audited against this branch

Locate calls the following, outside what the `dcb-locate` search proxy handles. Every one was
checked against `main`:

| Surface | Endpoint | Security | Touched by this branch? |
|---|---|---|---|
| Pickup locations | `GET /agencies/{id}/pickupLocations` | `@Secured(ADMINISTRATOR)` (class default) | No |
| Pickup locations (alternative) | `GET /locations…` | `@Secured(ADMINISTRATOR)` (class default) | No |
| Patron auth and lookup | `/patron/auth/**`, `/v2/patron/auth/**` | `{ADMINISTRATOR, INTERNAL_API}`; some routes anonymous | No |
| Agencies | `POST /graphql` | `isAuthenticated()` via `intercept-url-map` | No |
| Placement | `POST /patrons/requests/place` | `{CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN, LIBRARY_READ_ONLY, INTERNAL_API}` | Role set made explicit; ADMIN retained |
| Patron request summary | `GET /patrons/requests/patrons/{hostLmsCode}/requests?barcode=…` | `@Secured(ADMINISTRATOR)` | **Renamed — restored, see §8.4** |

### 8.4 What did *not* change for Locate

The exception is deliberately narrow. `ADMIN` keeps the surface it already had, and gains nothing:

- **No `DISCOVERY_SERVICE` role.** Locate does not get one, does not need one (although we encourage the team to adopt one in good time), and the arch test in
  §4 forbids that role outside `/discovery/`.
- **No `ADMIN` access to `/discovery/`.** The reverse rule holds too: the discovery surface is
  reachable by `DISCOVERY_SERVICE` alone, so nobody can quietly route Locate through the new
  endpoints without doing the assertion work properly.
- **No `IS_AUTHENTICATED` anywhere.** The default-DENY change stands. `ADMIN` is named explicitly on
  every route it reaches.

### 8.5 One behaviour change EBSCO Locate developers should be aware of

Barcode lookup is now an **exact** match — in `PatronRequestRepository`, in the `WHERE` clause of
both `findActiveRequestsForPatronByBarcode` and `findAllRequestsForPatronByBarcode`. It was
`LIKE '%barcode%'`, so a barcode of `1` returned every patron at that Host LMS whose barcode
contained a `1`, with titles and pickup locations attached. If this is ever being used by EBSCO Locate developers, they need to be aware of the following:

If Locate sends barcodes exactly as they come from the ILS, nothing changes. If it sends a
normalised or partial form and relied on the substring match to paper over the difference, its
lookups will start returning empty. 

The response body also gained fields (`outcome`, `discoveryStatus`, `statusDescription`). Additive and harmless to any client that ignores unknown properties.

### 8.6 Future of this exception

Ideally, Locate will migrate to using a `DISCOVERY_SERVICE` credential and sign patron assertions.
At that point both legacy routes get deleted and the `ADMIN` service account is revoked. Until then:

- **This is the only exception, and it is `ADMIN`-only.** Both legacy routes name `ADMINISTRATOR`
  and nothing else, and `DiscoveryApiSecurityTests` fails if that widens — including if someone
  folds the alias back onto the current method and it inherits the wider role set. A second
  integration asking for an `ADMIN` credential because "Locate has one" is not a precedent being
  followed, it is the exception becoming the rule. The answer is §6.
- **Do not widen it.** No new route gets added to `PatronRequestController` on the grounds that Locate might want it. Anything genuinely new belongs on the discovery surface with an assertion
  behind it.
- **Both legacy routes are marked LEGACY in the code**, with this section named in the comment, so
  the next person to read them learns why they exist before deciding to remove them.

---

## 9. Related

- `README.md` — the environment variable tables this document's §7.2 expands on, and the
  `MICRONAUT_CONFIG_FILES` / secret manager conventions the whole of §7 builds on.
- `docs/ncip-peer-authentication.md` — the same verification library, applied to inbound NCIP.
  Separate trust store, deliberately.
- `ApiSecurityArchitectureTests` — the build-time rules described in §4.
- `DiscoveryServicePropertiesBindingTests` — pins the configuration binding in §7.2, including the
  indexed-environment-variable limitation that makes `DCB_DISCOVERY_TRUSTED_SERVICES_JSON`
  necessary.
- `DiscoveryApiSecurityTests`, `PatronAssertionVerifierTests`,
  `PatronRequestCancellationServiceTests`, `PatronStatusMapperTests`, `DiscoveryLibrariesApiTests`
  — behavioural coverage of the surface.


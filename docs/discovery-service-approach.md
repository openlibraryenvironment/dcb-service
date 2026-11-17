# The Discovery Service Approach

How OpenRS DCB exposes patron-facing functionality to discovery services, why the surface
looks the way it does, and how an operator onboards a discovery service.

Audience: DCB maintainers, deployment operators, and anyone building a discovery layer that
wants to talk to an OpenRS consortium. **Vendors:** the contract you build against is
[`discovery-service-contract.md`](discovery-service-contract.md).

---

## 1. The problem this solves

A discovery service needs three things from DCB on behalf of a patron:

1. Show me the libraries in this consortium (for an institution picker, or "nearest to me").
2. Show me this patron's requests, in language a patron can read.
3. Let this patron place a request, and cancel their own request.

All three assume the discovery service already knows who the patron is. Signing the patron in
comes first. DCB still does this for any discovery service, as it did in v8.71.0 (§2.7). It sits
outside the contract only because it comes before it.

In v8.71.0, none of that had a home. Discovery traffic was pointed at the staff API with an
`ADMIN` credential that could act for any patron, and the "my requests" endpoint lived on the
same controller as request placement and state-machine rollback. Each defect in that
arrangement drove a change described below.

---

## 2. What is new since v8.71.0

### 2.1 A dedicated discovery surface

Everything a discovery service does on a patron's behalf now lives under `/discovery/`. The routes
are listed in [contract §1](discovery-service-contract.md#1-what-dcb-offers-a-discovery-service).

Two kinds of call fall outside `/discovery/`. Checking a library card uses DCB's patron
authentication endpoints, which are unchanged since v8.71.0 (§2.7). Search results, cluster ids
and live availability come from anonymous catalogue routes ([contract §13](discovery-service-contract.md#13-the-catalogue-routes-beside-the-contract)).

Only routes under `/discovery/` carry the `DISCOVERY_SERVICE` role, and a build-time test
enforces that for REST routes (§4). The staff REST API is not reachable with a discovery
credential. Every route under `/discovery/` either requires `DISCOVERY_SERVICE` or is anonymous.
The anonymous ones are read-only and return no patron data.

### 2.2 Caller identity and patron identity were separated

This is the central design decision, so it is worth stating precisely.

A discovery service authenticates with a confidential client credential holding the
`DISCOVERY_SERVICE` role. That proves **who is calling**. It does not, and cannot, prove
**which patron they are calling for**.

DCB does not answer that by giving the patron a token of their own. That would be wrong in two
directions at once:

- It would put a credential DCB accepts into a patron's browser. Every browser in the consortium
  would become an attack surface for the consortium's request API.
- It would make the patron a principal of the DCB realm, so every route open to "any
  authenticated principal" would be reachable by every patron. In v8.71.0 those routes included
  request placement as an arbitrary patron, expedited checkout, and state-machine rollback.

Instead, the calling service signs a short-lived assertion with its own key, naming the patron,
and DCB verifies it.

```
Authorization: Bearer <client credential>      -> WHO is calling  (Keycloak, DISCOVERY_SERVICE)
X-OpenRS-Patron-Assertion: <signed JWT>        -> WHICH PATRON    (caller's key, DCB-verified)
```

`PatronAssertionVerifier` checks signature, trusted issuer, subject, audience, expiry, `iat`
sanity, and a **DCB-chosen upper bound on lifetime** — an issuer that mints twelve-hour
assertions has built a bearer token, and the replay window is DCB's to bound, not theirs. It
also checks that the Keycloak client presenting the assertion is the service that signed it:
the bearer token's `azp` must equal that trusted service's `client-id`, and a token with no
`azp` is refused. It returns a
`PatronAssertion` record. In production code only the verifier constructs one, so a method that
holds one holds a verified identity. That is a convention the code follows, not a guarantee the
compiler gives: the record has a public constructor, and tests use it.

The verification mechanism is deliberately the existing `ki-mn-peer-auth` library, the same one
that guards inbound NCIP. A second hand-rolled JWT verifier is a bug with a long fuse. The
*trust store* is separate, though — an NCIP peer must not become a patron-assertion issuer by
accident.

### 2.3 Ownership is enforced in DCB, not delegated

Because DCB derives the patron itself, every discovery route is self-scoped by construction.
No method on the discovery controller accepts a patron identity from a path variable, query
parameter, or request body. On `/discovery/requests/place` the requestor is built from the
verified assertion and the body carries no identity at all.

The place body may still carry `agencyCode` and `homeLibraryCode`. Neither carries authority.
Request limits use the agency DCB resolves from the patron's home library at their own library
system, not an agency the caller names.

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

The response carries **both** the raw `status` and `discoveryStatus` + `statusDescription`
([contract §9](discovery-service-contract.md#9-rendering-status)).

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

What an integrator sees, including idempotence and the audit entry, is [contract §10](discovery-service-contract.md#10-cancellation). One
reason stays here, for maintainers: `AbstractHostLmsClient.cancelHoldRequest` defaults to a silent
no-op, which some clients inherit, so an empty result is treated as failure (`502`), not success.

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

### 2.7 Signing a patron in: unchanged, and open to every discovery service

DCB still checks a patron's library card for a discovery service. It asks the patron's own
library system, through that library's adapter. The endpoints, their paths and their security are
the same as in v8.71.0, and any discovery service may call them, not only EBSCO Locate (§8). What
changed is the step after sign-in. A discovery service no longer needs `ADMIN` to act for the
patron. It holds `DISCOVERY_SERVICE` and sends a signed patron assertion to the routes under
`/discovery/`.

The endpoints are not under `/discovery/` because they are not patron-scoped. Until they answer, there is
no verified patron and nothing to assert. A discovery service calls them the way any other caller
does, with no `DISCOVERY_SERVICE` credential and no patron assertion. They are not specific to
discovery either: existing integrations call them at these paths, including the one described in
§8. Moving them would break those callers and would not change what the endpoints do.

The endpoints, the sign-in sequence and the institution sign-in (`/discovery/patrons/resolve`, the
new route's equivalent of the lookup EBSCO Locate calls with `ADMIN`) are in [contract §3](discovery-service-contract.md#3-signing-a-patron-in).

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
configured for discovery accepts no patron assertions at all: it has no discovery configuration,
and the patron-scoped routes, `/discovery/patrons/resolve` included, do not exist. DCB's security
refuses a call to a route it has no rule for: `401` with a token, `400` without.
It does not quietly behave as though it has been configured. The trusted-services list is empty
by default. Duplicate issuers refuse to start rather than picking a winner, because two services
claiming one issuer means either can mint the other's assertions.

**Least privilege.** `DISCOVERY_SERVICE` buys the patron self-service surface and nothing else.
Only routes under `/discovery/` carry it, and the build-time test below enforces that for REST
routes. `POST /graphql` admits any authenticated token at the URL; there, each data fetcher's
own role check is what refuses a discovery credential. Within the surface, a credential can act
only on a patron named in an assertion its own service signed: DCB checks that the bearer
token's `azp` is the `client-id` of the service that signed the assertion. A trusted service may
vouch for a patron at any library in the consortium, which is why only a consortium-wide service
is onboarded ([contract §5](discovery-service-contract.md#5-what-the-operator-will-need-from-you)). The surface includes
walk-up checkout (`/discovery/requests/place/expedited`), so a discovery credential with a valid
assertion can cause a loan, not only a hold. Protect the signing key accordingly. (One
pre-existing integration sits outside this model on an `ADMIN` service credential — §8 states
that exception and what it costs.)

**Complete mediation.** Every discovery method that acts on a patron calls
`assertionVerifier.verify(request)` before doing anything, and the verified `PatronAssertion` is
the only source of patron identity available to it. Resolve ([contract §7](discovery-service-contract.md#7-making-calls)) is the one that cannot, since
it is how a service learns whom to assert. It answers with an identity only, and only to a client
that is a trusted service. By convention, only the verifier constructs a `PatronAssertion` in production
code, so no code path reaches patron-scoped data with an unverified identity.

**Make the invalid state unrepresentable.** This is the part that matters most and is easiest to
skip. `ApiSecurityArchitectureTests` reads Micronaut's compiled bean definitions at build time —
no database, no container, milliseconds — and fails the build when:

- a route is open to every authenticated principal without an explicit `@OpenToAllPrincipals`
  justification;
- a justification is blank;
- **any** mutating route is open to every authenticated principal, justification or not;
- an anonymous mutating route is not on the test's explicit allow-list (today, the two
  library-card checks in §2.7);
- `DISCOVERY_SERVICE` appears on a route outside `/discovery/`;
- a route under `/discovery/` is secured by anything other than `DISCOVERY_SERVICE` or anonymous.
  The test allows anonymous on any `/discovery/` route, so review a new anonymous one as you
  would any public endpoint;
- any staff request route is anonymous, open to all principals, or ungated;
- a patron-held role (`PATRON`, `PATRON_*`) reappears in any `@Secured`;
- a staff walk-up checkout route, which takes its borrower from the request body, is reachable
  by `DISCOVERY_SERVICE`, anonymously, by every principal, or ungated;
- a request body on `DiscoveryPatronRequestsController` has a component that reads as a patron
  identity.

The test also asserts that it can still see the controllers it names, so a refactor cannot turn
those checks into a silent no-op. `DiscoveryApiSecurityTests` exercises the discovery routes
directly. `@Secured(IS_AUTHENTICATED)` is not banned — it is made *visible*, so it becomes a
decision a reviewer sees in a diff rather than a default nobody noticed.

**Defence in depth.** Signature, issuer, subject, audience, `exp`, `nbf`, `iat` sanity, a
DCB-enforced lifetime cap, and the match between the bearer's client and the signing service are
all checked independently. Ownership is then re-checked in SQL at the point of use, not inferred
from the fact that authentication succeeded.

**Fail securely, and say nothing useful while doing it.** One exception type covers every
assertion rejection: a caller learns its assertion was not accepted, not which check it tripped.
Not-yours and not-found are the same 404. Patron-facing errors carry patron vocabulary; the raw
state and the internal detail go to the log and the audit trail.

**Auditability as a first-class output.** Placement, walk-up checkout and cancellation each write
an audit entry naming the patron and the asserting service. Onboarding a discovery service is a reviewable configuration change —
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
  an issuer, a JWKS URL, and the Keycloak client id where it differs from the service id. Adding the second, fifth, or twentieth discovery service requires no
  DCB change, no release, and no schema migration.
- **The identity model assumes nothing about the caller's architecture.** DCB requires only
  that the caller can authenticate itself and sign a short-lived assertion. How the caller
  establishes patron identity — SAML, OIDC, an ILS session, a PIN check at the desk — is
  entirely its own business, and DCB is deliberately incurious about it. DCB still offers a
  library-card check to every discovery service: a caller that wants it uses the patron
  authentication endpoints (§2.7).
- **The response shape serves both kinds of consumer.** A discovery service with strong opinions
  about status presentation reads the raw `status`. One that wants to render and move on reads
  `discoveryStatus` and `statusDescription`. Neither is privileged, and the mapping is not
  tuned for any particular UI.
- **The public directory is genuinely public.** It returns what an institution picker and a
  sign-in screen need: codes, names, coordinates, branding, and what each library's card is
  checked against ([contract §7](discovery-service-contract.md#7-making-calls)). It returns nothing that maps the consortium's internals. Anyone can
  build against it without an onboarding conversation first.
- **The security model does not assume a trusted caller.** Because ownership is enforced inside
  DCB, a third-party discovery service is not a more dangerous integration than a first-party
  one. That is what makes "any discovery service" a viable position rather than a slogan.

---

## 6. For discovery service vendors

The vendor-facing contract is [`discovery-service-contract.md`](discovery-service-contract.md): the
routes, signing a patron in, the patron assertion, the requests and responses, and what each side
undertakes. This document keeps the reasons behind it (§2 to §5), the operator's onboarding (§7)
and the EBSCO Locate exception (§8).

---

## 7. Onboarding in practice: deployment mechanics

[The contract](discovery-service-contract.md) is what a vendor does. This is what the *operator* does, and it is the part that differs
between hosting environments.

Onboarding has three independent parts. Only the last two care where DCB runs:

1. **The Keycloak client** — identical everywhere.
2. **The `dcb.discovery` configuration** — the part that actually differs.
3. **Network egress to the vendor's JWKS URL** — different mechanics, same requirement.

Routes by hosting environment: **§7.3** Kubernetes, **§7.4** non-Kubernetes (Docker, systemd,
generic schedulers), **§7.5** AWS ECS/Fargate. §7.2 applies to all three.

### 7.1 Part one: Keycloak (environment-independent)

Create a **confidential** client for the vendor with service accounts enabled. Then:

1. Create `DISCOVERY_SERVICE` as a **realm** role, not a client role, if the realm does not
   already have it.
2. Assign it to the client's service-account user.
3. Give the client a realm-role protocol mapper that writes the user's realm roles to a
   top-level `roles` claim in the access token, as the staff clients already have.

DCB reads roles from that top-level `roles` claim and nowhere else. Keycloak's default puts
realm roles only in `realm_access.roles`, so a client without the mapper, or with the role
granted as a client role, gets `403` on every `/discovery/` call. Use the vendor's `service-id`
as the client id. If you cannot, record the client id as `client-id` in their trust entry
(§7.2), because DCB rejects their assertions unless the token's `azp` matches it. An identity
provider other than Keycloak must stamp `azp` on its access tokens.

Hand the client id and secret over a channel that is not email.

Nothing about this step depends on how DCB is hosted. Keycloak is a separate system, and a
discovery service's credential is issued there whether DCB runs on Kubernetes, Fargate, or a
laptop. DCB validates the bearer token against `KEYCLOAK_CERT_URL` exactly as it does for every
other role, so if that variable is already set for staff traffic, it needs no new DCB wiring.

### 7.2 Part two: everything can be an environment variable

The whole `dcb.discovery` block is deliverable through the environment, which means a
deployment with no way to mount a config file is fully supported. The scalars and the list
work differently, though, and the difference is not arbitrary:

| Environment variable                   | Config key                             | Type                | Default                                               | Required                                                                 |
|----------------------------------------|----------------------------------------|---------------------|-------------------------------------------------------|--------------------------------------------------------------------------|
| `DCB_DISCOVERY_ENABLED`                | `dcb.discovery.enabled`                | boolean             | `false`                                               | yes, to serve discovery at all                                           |
| `DCB_DISCOVERY_AUDIENCE`               | `dcb.discovery.audience`               | string              | `dcb`                                                 | recommended: set a value unique to this deployment, such as its base URL |
| `DCB_DISCOVERY_MAX_ASSERTION_LIFETIME` | `dcb.discovery.max-assertion-lifetime` | ISO-8601 duration   | `PT2M`                                                | no                                                                       |
| `DCB_DISCOVERY_TRUSTED_SERVICES_JSON`  | `dcb.discovery.trusted-services-json`  | JSON array (string) | unset; with discovery on, set but blank stops startup | yes                                                                      |

Two further variables are not discovery-specific but decide whether the above arrives at all:

| Environment variable     | Why discovery cares                                                                                                                                                                                  |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `MICRONAUT_CONFIG_FILES` | Comma-separated paths to external config files: the way to supply the `dcb.discovery` keys, `trusted-services-json` included, from a file rather than variables. Ignore it if you use the variables. |
| `KEYCLOAK_CERT_URL`      | Validates the discovery service's bearer token. Without it every `/discovery/requests` call is a `401` before assertions are even considered.                                                        |

The defaults are in `DiscoveryConfig`, which exists only when `dcb.discovery.enabled` is `true`;
these variables set the values.

The list needs the JSON form because **a list of objects cannot be expressed as indexed
environment variables at all**. Micronaut maps an environment variable onto every dot/hyphen
permutation of its name, but an index stays a *dot* segment:
`DCB_DISCOVERY_TRUSTED_SERVICES_0_SERVICE_ID` resolves to
`dcb.discovery.trusted-services.0.service-id`, never the `trusted-services[0].service-id` form
that list binding reads. So `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` takes the entire list as a
JSON array in one value, with kebab-case keys. It is the only form DCB reads: a YAML
`dcb.discovery.trusted-services` list stops startup with a message naming `trusted-services-json`.

```
DCB_DISCOVERY_TRUSTED_SERVICES_JSON=[{"service-id":"their-service-id","issuer":"https://discovery.example.org","jwks-uri":"https://discovery.example.org/.well-known/jwks.json"}]
```

Inline `jwks` works here too, for the egress-less case in §7.6. Add `"client-id"` to an entry
only when the vendor's Keycloak client id differs from its `service-id` (§7.1).

Every deployment ships with the audience `dcb`. A discovery service that serves two consortia
would then mint assertions either deployment accepts. Set `DCB_DISCOVERY_AUDIENCE` to a value
unique to this deployment, and tell the vendor ([contract §4](discovery-service-contract.md#4-what-you-will-need-from-the-consortiums-dcb-operator)).

**With discovery on and the variable absent, that is "not configured", not an error.** DCB starts
with no trusted service, logs `accepted from 0 trusted service(s): []`, keeps serving
`GET /discovery/libraries` (which needs no assertion), and rejects every patron-scoped discovery
call with `401 INVALID_PATRON_ASSERTION`. Fails closed, stays up, and tells you so in one log line.
With discovery on, a value that is set but blank, or malformed, stops the application starting.

**With discovery off there is no discovery configuration at all.** DCB reads no `dcb.discovery`
setting, so a blank or malformed one does not stop it starting, and the patron-scoped discovery
routes (`/discovery/requests`, `/discovery/loans`, `/discovery/account`, `/discovery/patrons/resolve`)
do not exist, and a call to one with a token is refused with `401`. Routes that need no patron, such as `GET /discovery/libraries`, are
unaffected.

Malformed JSON fails the application at startup rather than binding an empty list — a trust anchor that silently binds nothing is
indistinguishable from a deployment nobody configured, which is the one outcome worth crashing
to avoid. (`DiscoveryConfigBindingTests` pins the JSON parsing and the indexed-env-var limitation, so
nobody removes the JSON route on the assumption that indices would do, and
`DiscoveryTrustAnchorStartupTests` starts the application with each unusable setting.)

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
        trusted-services-json: >-
          [{"service-id": "their-service-id",
            "issuer": "https://discovery.example.org",
            "jwks-uri": "https://discovery.example.org/.well-known/jwks.json"}]
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

This rolling update is for onboarding changes once you run v9.2.0. For the upgrade from v8.71.0
itself, stop the old pods first with `strategy: type: Recreate` (`docs/v9-migration-guide.md` §1):
a rolling update keeps v8.71.0 pods running while the migrations remove columns they read.

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
exec java -server -jar /home/app/application.jar
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
(`DiscoveryTrustedServiceStore`, not configurable). An assertion whose `kid` is not in the cache
makes DCB fetch the set again at once. This is an outbound call to a host named in configuration,
and it has to be possible.

|                                              | Kubernetes                                                                            | Docker / systemd / VM          | ECS on Fargate                                                                    |
|----------------------------------------------|---------------------------------------------------------------------------------------|--------------------------------|-----------------------------------------------------------------------------------|
| Allow egress                                 | NetworkPolicy permitting 443 to the vendor host, plus whatever egress gateway applies | host firewall / outbound proxy | task security group egress rule on 443                                            |
| Route to internet                            | cluster-dependent                                                                     | whatever the host has          | private subnet + NAT gateway, **or** public subnet with `assignPublicIp: ENABLED` |
| Stable source IP for the vendor's allow-list | egress gateway / SNAT address, if the cluster has one                                 | the host's address, or its NAT | NAT gateway Elastic IP — stable, and easy to hand to the vendor                   |

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
    trusted-services-json: >-
      [{"service-id": "their-service-id",
        "issuer": "https://discovery.example.org",
        "jwks": {"keys": [{"kty": "RSA", "kid": "…", "n": "…", "e": "AQAB"}]}}]
```

`jwks` takes precedence over `jwks-uri`. The cost is that you now own key rotation manually: when
the vendor rotates, your deployment breaks until you paste the new key. Only take this trade in a
deployment that truly cannot egress.

### 7.7 Clock skew

Assertion lifetimes are short by design (`PT2M` by default). An `iat` more than 60 seconds in
the future is rejected outright, and `exp` and `nbf` are checked with no tolerance at all. A
vendor whose clock runs a second fast and who sets `nbf` to `iat` fails every call. That makes
clock discipline an operational requirement rather than a nicety.

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
- **Incomplete or blank entry.** With discovery on, an entry missing `service-id`, `issuer`, or
  both of `jwks-uri`/`jwks`, or with any of its fields set but blank, stops the application starting
  and names the entry. So does `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` set but blank, and a blank
  `audience` or a non-positive `max-assertion-lifetime`. Leave a setting out to take its default.
  With discovery off, none of this is checked.
- **Duplicate issuer.** Two entries sharing an issuer throw at bean construction and the
  application does not start. Deliberate: two services on one issuer means either can mint the
  other's assertions, and picking a winner silently would be worse than failing loudly. A
  duplicate `service-id` or `client-id` refuses to start for the same reason.

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
never got in". A `403` at (2) means the token got in without the role: check the `roles` mapper
in §7.1.

Do not skip (3) because the startup log looked right. The startup checks prove the configuration
parses. Only a real assertion proves that the vendor's key, issuer, audience and Keycloak client
all line up.

### 7.9 Offboarding and rotation

- **Key rotation at the same JWKS URL is automatic.** A new key is accepted on first use: an
  assertion carrying a `kid` DCB has not seen makes it fetch the JWKS again (§7.6). No
  configuration change, no deployment. The vendor should keep the old key published for one
  assertion lifetime after switching, so assertions already in flight still verify.
- **A withdrawn key is not dropped at once.** DCB keeps trusting a key the vendor has removed
  until its 15-minute cache expires. After a key compromise, restart DCB, or remove the trust
  entry and roll, to stop trusting it immediately.
- **Changing `issuer` or `jwks-uri` is a re-onboarding.** It moves the trust anchor, so review it
  the way you reviewed the original — not as a routine config tweak.
- **Offboarding is removing the entry and redeploying.** Until the deployment rolls, the vendor
  can still act. If you need it immediate, set `dcb.discovery.enabled=false` and roll, which
  closes the whole discovery surface rather than one vendor's access to it.
- **Revoking the Keycloak client is the faster lever**, and it is the one to reach for in an
  incident. It takes effect as soon as the vendor's current access token expires, without touching
  DCB's configuration or restarting anything. Do that first, then remove the trust anchor entry at
  the next deployment. Because DCB accepts a vendor's assertions only with that vendor's own
  client token, a vendor whose Keycloak credential is gone cannot reach any patron-scoped route,
  however good their assertions are. The anonymous routes stay open to them, as to anyone.

Whatever the change, it lands only when the process restarts:

| Environment    | Roll it                                                                               |
|----------------|---------------------------------------------------------------------------------------|
| Kubernetes     | `kubectl rollout restart deployment/dcb-service` (plus the checksum annotation, §7.3) |
| ECS / Fargate  | new task definition revision, then `aws ecs update-service --force-new-deployment`    |
| Docker Compose | `docker compose up -d --force-recreate dcb`                                           |
| systemd        | edit the `EnvironmentFile`, then `systemctl restart dcb`                              |

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
  requests by barcode. It is the consortium's highest-privilege role, and the EBSCO Locate exception grants no authority the role did not
  already hold. It is a *credential-handling* risk — one long-lived, high-privilege secret in a third party's infrastructure — not an authorisation-model hole.
- The blast radius is therefore the credential itself. An ADMIN credential leaking is considerably worse than a leaked `DISCOVERY_SERVICE` credential, which buys only the
  self-service surface and still cannot name a patron. The consortium and EBSCO should agree how this credential is stored, rotated and revoked.

### 8.3 The surfaces Locate uses

The `dcb-locate` search proxy reads the shared index and never calls dcb-service, so Locate's
`ADMIN` credential is used only on the calls below, which Locate makes to dcb-service directly:

| Surface                        | Endpoint                                                         | Security                                                                                       | Changed since v8.71.0?                                                                                                                 | New-route equivalent              |
|--------------------------------|------------------------------------------------------------------|------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| Pickup locations               | `GET /agencies/{id}/pickupLocations`                             | `@Secured(ADMINISTRATOR)` (class default)                                                      | No                                                                                                                                     | `GET /discovery/pickup-locations` |
| Pickup locations (alternative) | `GET /locations` (list); `GET /locations/{id}`                   | list anonymous; `/{id}` `@Secured(ADMINISTRATOR)` (class default)                              | No                                                                                                                                     | `GET /discovery/pickup-locations` |
| Patron authentication          | `POST /patron/auth`, `POST /v2/patron/auth`                      | anonymous (§2.7)                                                                               | No                                                                                                                                     | the same endpoints (§2.7)         |
| Patron lookup                  | `POST /patron/auth/lookup`, `POST /v2/patron/auth/lookup`        | `{ADMINISTRATOR, INTERNAL_API, CONSORTIUM_ADMIN, LIBRARY_ADMIN, LIBRARY_READ_ONLY}`            | **Yes, in 9.2.0:** `/v2/patron/auth/lookup` was anonymous. Locate must send its token on it (§8.5). `/patron/auth/lookup` is unchanged | `POST /discovery/patrons/resolve` |
| Agencies                       | `POST /graphql`                                                  | `isAuthenticated()` via `intercept-url-map` at the URL, then `STAFF` roles in the data fetcher | Yes, in 9.2.0: v8.71.0 answered any signed-in caller. `ADMIN` is a `STAFF` role, so Locate is not affected                             | `GET /discovery/libraries`        |
| Live availability              | `GET /items/availability?clusteredBibId=`                        | anonymous, deliberately public                                                                 | No                                                                                                                                     | the same route                    |
| Placement                      | `POST /patrons/requests/place`                                   | `{CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN, LIBRARY_READ_ONLY, INTERNAL_API}`            | Yes: v8.71.0 let any signed-in caller place. The role set is explicit now, and keeps `ADMIN`                                           | `POST /discovery/requests/place`  |
| Patron request summary         | `GET /patrons/requests/patrons/{hostLmsCode}/requests?barcode=…` | `@Secured(ADMINISTRATOR)`                                                                      | Yes: v8.71.0 also let `CONSORTIUM_ADMIN` in, and the barcode now has to match exactly (§8.5)                                           | `GET /discovery/requests`         |
| Request browse                 | `GET /patrons/requests`                                          | `@Secured(ADMINISTRATOR)`                                                                      | No: `ADMIN` only in both. Now marked LEGACY                                                                                            | none: a staff route               |

### 8.4 What did *not* change for Locate

The exception is deliberately narrow. `ADMIN` keeps the surface it already had, and gains nothing:

- **Patron authentication is not part of the exception.** The library-card check is open to every
  discovery service, as it was in v8.71.0 (§2.7).
- **No `DISCOVERY_SERVICE` role.** Locate does not get one, does not need one (although we encourage the team to adopt one in good time), and the arch test in
  §4 forbids that role outside `/discovery/`.
- **No `ADMIN` access to `/discovery/`.** The reverse rule holds too: the discovery surface is
  reachable by `DISCOVERY_SERVICE` alone, so nobody can quietly route Locate through the new
  endpoints without doing the assertion work properly.
- **No `IS_AUTHENTICATED` anywhere.** The default-DENY change stands. `ADMIN` is named explicitly on
  every route it reaches.

### 8.5 Behaviour changes EBSCO Locate developers should be aware of

`POST /v2/patron/auth/lookup` needs a token with a role (since 9.2.0). In v8.71.0 it was
anonymous. Locate's `ADMIN` account qualifies, so Locate must send its token on this call.

Barcode lookup is now an **exact** match — in `PatronRequestRepository`, in the `WHERE` clause of
both `findActiveRequestsForPatronByBarcode` and `findAllRequestsForPatronByBarcode`. It was
`LIKE '%barcode%'`, so a barcode of `1` returned every patron at that Host LMS whose barcode
contained a `1`, with titles and pickup locations attached.

If Locate sends barcodes exactly as they come from the ILS, nothing changes. If it sends a
normalised or partial form and relied on the substring match to paper over the difference, its
lookups will start returning empty.

The response body also gained fields (`outcome`, `discoveryStatus`, `statusDescription`). Additive and harmless to any client that ignores unknown properties.

### 8.6 Future of this exception

Ideally, Locate will migrate to using a `DISCOVERY_SERVICE` credential and sign patron assertions.
At that point both legacy routes get deleted and the `ADMIN` service account is revoked. Until then:

- **This is the only exception, and it is `ADMIN`-only.** Both legacy routes name `ADMINISTRATOR`
  and nothing else. `DiscoveryApiSecurityTests` fails if a `CONSORTIUM_ADMIN` or a
  `DISCOVERY_SERVICE` token reaches either of them — which is what happens if someone folds the
  alias back onto the current method and it inherits the wider role set. It does not try every
  other role, so review any change to those two `@Secured` lines by eye. A second
  integration asking for an `ADMIN` credential because "Locate has one" is not a precedent being
  followed, it is the exception becoming the rule. The answer is [the contract](discovery-service-contract.md).
- **Do not widen it.** No new route gets added to `PatronRequestController` on the grounds that Locate might want it. Anything genuinely new belongs on the discovery surface with an assertion
  behind it.
- **Both legacy routes are marked LEGACY in the code**, with this section named in the comment, so
  the next person to read them learns why they exist before deciding to remove them.

---

## 9. Related

- `docs/discovery-service-contract.md` — the contract for vendors.
- `README.md` — the environment variable tables this document's §7.2 expands on, and the
  `MICRONAUT_CONFIG_FILES` / secret manager conventions the whole of §7 builds on.
- `docs/ncip-peer-authentication.md` — the same verification library, applied to inbound NCIP.
  Separate trust store, deliberately.
- `ApiSecurityArchitectureTests` — the build-time rules described in §4.
- `DiscoveryConfigBindingTests` — pins the JSON parsing in §7.2, including the
  indexed-environment-variable limitation that makes `DCB_DISCOVERY_TRUSTED_SERVICES_JSON`
  necessary. `DiscoveryTrustAnchorStartupTests` starts the application with discovery off and with
  each unusable setting on.
- `DiscoveryApiSecurityTests`, `PatronAssertionVerifierTests`,
  `PatronRequestCancellationServiceTests`, `PatronStatusMapperTests`, `DiscoveryLibrariesApiTests`
  — behavioural coverage of the surface.

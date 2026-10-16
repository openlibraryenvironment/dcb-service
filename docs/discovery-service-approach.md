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

All three assume the discovery service already knows who the patron is. Signing the patron in
comes first, and it is not part of the contract this document describes. §2.7 covers what DCB
offers for that step, and why it stayed where it was.

Before this work, none of that had a home. Discovery traffic was pointed at the staff API,
patron identity was carried as a role on a token, and the "my requests" endpoint lived on the
same controller as request placement and state-machine rollback. That arrangement had three
distinct defects, and each one drove a change described below.

---

## 2. What changed

### 2.1 A dedicated discovery surface

Everything a discovery service does on a patron's behalf now lives under `/discovery/`:

| Route | Auth | Purpose |
|---|---|---|
| `GET /discovery/libraries` | anonymous | Public library directory (fields in §6.4) |
| `GET /discovery/pickup-locations?agencyCode=` | anonymous | Where a patron of that library may collect a request (§6.4) |
| `GET /discovery/consortium` | anonymous | The consortium's own brand |
| `GET /discovery/announcements` | anonymous | The notices live now, for a library, its groups and the consortium |
| `GET /discovery/brand-assets/{key}` | anonymous | A logo or other brand image named by the routes above |
| `GET /discovery/requests` | `DISCOVERY_SERVICE` + patron assertion | The asserted patron's requests, paged |
| `POST /discovery/requests/place` | `DISCOVERY_SERVICE` + patron assertion | Place a request as the asserted patron |
| `POST /discovery/requests/place/expedited` | `DISCOVERY_SERVICE` + patron assertion | Check out the item the asserted patron is holding (walk-up). This creates a **loan**, not a hold |
| `POST /discovery/requests/{id}/cancel` | `DISCOVERY_SERVICE` + patron assertion | Cancel the asserted patron's own request |
| `GET /discovery/loans` | `DISCOVERY_SERVICE` + patron assertion | What the asserted patron has on loan through DCB |
| `GET /discovery/account` | `DISCOVERY_SERVICE` + patron assertion | Loans and charges at the asserted patron's own library |
| `POST /discovery/patrons/resolve` | `DISCOVERY_SERVICE`, no assertion | Turn an institution sign-in into a borrower, so that you can mint an assertion |

Two kinds of call fall outside `/discovery/`. Checking a library card uses DCB's patron
authentication endpoints, which this work did not change (§2.7). Search results, cluster ids
and live availability come from anonymous catalogue routes (§6.9).

Only routes under `/discovery/` carry the `DISCOVERY_SERVICE` role, and a build-time test
enforces that for REST routes (§4). The staff REST API is not reachable with a discovery
credential. Every route under `/discovery/` either requires `DISCOVERY_SERVICE` or is anonymous.
The anonymous ones are read-only and return no patron data.

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

- **Idempotent while it is in flight.** If the local hold is already cancelled, DCB converges
  without calling the LMS again. A patron mashing the cancel button must not become a load test
  against a member library. Once the request itself has reached `CANCELLED`, a repeat call
  answers `409 NOT_CANCELLABLE` with `discoveryStatus` `CANCELLED`, which means the earlier
  cancellation worked.
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

### 2.7 What did not move: signing a patron in

Everything in §2.1 starts from a patron the discovery service has already identified. Identifying
the patron is a separate step. For a patron who signs in with a library card, DCB already had
endpoints for it. This work left them where they were and did not change their security:

| Route | Auth | What it does |
|---|---|---|
| `POST /patron/auth` | anonymous | Checks a barcode and its PIN, password or name against the patron's home library system, through that library's adapter. Answers `status` (`VALID` or `INVALID`), `localPatronId`, `systemCode`, `agencyCode` and `homeLocationCode` |
| `POST /v2/patron/auth` | anonymous | The same check, with the agency carried in the principal as `AGENCY/barcode` |

They are not under `/discovery/` because they are not patron-scoped. Until they answer, there is
no verified patron and nothing to assert. A discovery service calls them the way any other caller
does, with no `DISCOVERY_SERVICE` credential and no patron assertion. They are not specific to
discovery either: existing integrations call them at these paths, including the one described in
§8. Moving them would break those callers and would not change what the endpoints do.

For a library-card sign-in, the sequence is:

1. Call `POST /patron/auth` with `agencyCode`, `patronPrinciple` (the barcode) and `secret`.
   `GET /discovery/libraries` tells you which libraries accept a card (`acceptsLibraryCard`) and
   what the secret is (`secretKind`).
2. On `VALID`, map `systemCode` to `localSystemCode`, and the first entry of `localPatronId` to
   `localSystemPatronId`.
3. Mint the patron assertion (§6.3), and send it with your `/discovery/` calls.

Only a `200` that carries a `status` is an answer. Any other response means the library system
could not be asked. Never show it to the patron as a wrong PIN.

`INVALID` can also mean that DCB does not know the `agencyCode` you sent. Take the code from
`GET /discovery/libraries` rather than from anything the patron types.

DCB does not rate-limit these checks or lock out a card after failed attempts. Throttle sign-in
attempts yourself, per card and per client, as you would on any login form.

A patron whom your identity provider signed in through their institution, not their library, has
no library credential to check. For that patron, use `POST /discovery/patrons/resolve` (§6.4). It
is part of the contract because it needs your service credential.

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
configured for discovery accepts no patron assertions at all: every patron-scoped route answers
`401 INVALID_PATRON_ASSERTION`, and `/discovery/patrons/resolve` answers `403 DISCOVERY_DISABLED`.
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
is onboarded (§6.2). The surface includes
walk-up checkout (`/discovery/requests/place/expedited`), so a discovery credential with a valid
assertion can cause a loan, not only a hold. Protect the signing key accordingly. (One
pre-existing integration sits outside this model on an `ADMIN` service credential — §8 states
that exception and what it costs.)

**Complete mediation.** Every discovery method that acts on a patron calls
`assertionVerifier.verify(request)` before doing anything, and the verified `PatronAssertion` is
the only source of patron identity available to it. Resolve (§6.4) is the one that cannot, since
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
  entirely its own business, and DCB is deliberately incurious about it. A caller that wants
  DCB to check a library card can use the patron authentication endpoint (§2.7). That check
  happens before the contract starts, not inside it.
- **The response shape serves both kinds of consumer.** A discovery service with strong opinions
  about status presentation reads the raw `status`. One that wants to render and move on reads
  `discoveryStatus` and `statusDescription`. Neither is privileged, and the mapping is not
  tuned for any particular UI.
- **The public directory is genuinely public.** It returns what an institution picker and a
  sign-in screen need: codes, names, coordinates, branding, and what each library's card is
  checked against (§6.4). It returns nothing that maps the consortium's internals. Anyone can
  build against it without an onboarding conversation first.
- **The security model does not assume a trusted caller.** Because ownership is enforced inside
  DCB, a third-party discovery service is not a more dangerous integration than a first-party
  one. That is what makes "any discovery service" a viable position rather than a slogan.

---

## 6. For discovery service vendors: adding OpenRS DCB support

If you build a discovery layer and want it to place and track OpenRS consortial requests, this
section is the whole patron-facing integration. The catalogue data you show beside it, such as
cluster ids and live availability, comes from the anonymous routes in §6.9.

### 6.1 What you will need from the consortium's DCB operator

- The base URL of the DCB deployment.
- A Keycloak client credential (client id + secret) for your service, granted the
  `DISCOVERY_SERVICE` role. **Confidential client, server-side only.** This credential must
  never reach a browser. DCB accepts your assertions only when they arrive with this client's
  token.
- The `audience` value that deployment expects in patron assertions. It is `dcb` by default,
  but each deployment should set its own, so that an assertion minted for one consortium is
  worthless at another. If you serve several consortia, you need a client, an audience and an
  assertion per deployment.
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
        # client-id: your-keycloak-client   # only if it differs from service-id
```

So, concretely: a stable service id, the `iss` value you will put in your assertions, and a
JWKS endpoint publishing your public signing keys. `client-id` is the Keycloak client your
calls arrive with, and it defaults to `service-id`. A new key at the same JWKS URL is accepted
on first use (§7.9). A change of issuer or JWKS URL is a configuration change and should be
reviewed as one.

**A trusted service can act for any patron at any library in the consortium.** Onboard only a
service that discovers for the whole consortium. A product serving one library hands its patrons
off to the consortium's discovery front end rather than becoming a trusted service.

Your JWKS endpoint must be served over HTTPS and reachable from the DCB deployment, and each key
in it must carry the `kid` you put in your assertion headers. If it sits behind an IP allow-list,
say so during onboarding — the operator can supply a stable egress address (§7.6). If your
platform cannot serve a JWKS at all, the operator can hold your public key inline (`jwks`,
§7.6), at the cost of updating it by hand whenever you rotate.

How the operator actually applies this configuration depends on how their DCB is hosted; §7
covers the Kubernetes and AWS Fargate routes.

Generate an RSA key pair. DCB accepts RS256 signatures only and rejects every other algorithm,
EC and RSA-PSS included. The private key stays in your backend. DCB never sees it and never
needs to.

### 6.3 Minting a patron assertion

Every call that acts on a patron carries one, signed with your key:

**Header:** `alg` `RS256` (the only algorithm DCB accepts), `kid` (matching a key in your JWKS)

**Claims:**

| Claim | Value |
|---|---|
| `iss` | your configured issuer, exactly |
| `sub` | your configured `service-id` — the *service*, not the patron |
| `aud` | the deployment's configured audience |
| `iat` | now. This is the only time claim with any clock tolerance: up to 60 seconds in the future is accepted |
| `exp` | `iat` + your chosen lifetime, **not exceeding** `max-assertion-lifetime`. Checked with no tolerance |
| `nbf` | optional. Checked with no tolerance, so omit it or set it a few seconds before `iat` |
| `localSystemCode` | the patron's home Host LMS code, as DCB knows it |
| `localSystemPatronId` | the patron's local id in that system |

Send the assertion with your own client's access token. DCB rejects an assertion whose signing
service is not the client that presented it, with the same `401` as any other rejection.

Mint one per request, or per short-lived patron session. Do not cache them for hours; DCB will
reject an over-long lifetime outright, and the bound exists precisely so that a stolen assertion
is worth almost nothing.

`localSystemCode` and `localSystemPatronId` are the patron identity contract. Get them from
whatever authentication you already perform against the patron's home library system. If you
check library cards through DCB, `POST /patron/auth` returns both (§2.7). **You are
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
  "requesterNote": null,
  "itemLocalId": null,
  "itemHostLmsCode": null,
  "itemAgencyCode": null
}
```

Note what the body does *not* contain: a requestor. You cannot name the patron you are
requesting for — only assert them and be verified. `homeLibraryCode` and `agencyCode` carry no
authority. Request limits apply to the agency DCB resolves from the patron's own library system,
whatever you send (§2.3).

`pickupLocationCode` is a pickup location's `id`, a UUID, taken from
`GET /discovery/pickup-locations` below. A location code or name is refused at preflight with
`UNKNOWN_PICKUP_LOCATION_CODE`.

The three `item*` fields request one specific copy. Send all three, taken from that copy's `id`,
`hostLmsCode` and `agency.code` in live availability (§6.9), or send none and let DCB choose.
Sending one or two of them is a `400`. Put a copy's `parsedVolumeStatement` in
`volumeDesignator`.

**`POST /discovery/requests/place/expedited`** checks out the item a patron is standing at the
desk holding (walk-up). It takes the same fields, except that `itemLocalId`, `itemHostLmsCode`,
`itemAgencyCode` and `agencyCode` (the patron's own library) are all required, and
`pickupLocationCode` is the location the patron is in. Without `agencyCode` the walk-up is
refused with `WALK_UP_LIBRARY_REQUIRED`, because DCB could not otherwise refuse a loan at the
patron's own library (`WALK_UP_SAME_LIBRARY`). It creates a loan immediately, so offer it only
where a patron has the item in hand.

**`GET /discovery/pickup-locations?agencyCode=&page=0&size=100`** lists the places a patron of
that library may collect a request. It needs no credential. The response is
`{"locations": [...], "page", "size", "hasMore"}`, and each location carries `id`, `code`, `name`,
`agencyCode` and `agencyName`. `size` is clamped to between 1 and 100; follow `hasMore` for the
rest. For a library that allows pickup anywhere, the list is every pickup location, at any
library, that is enabled for pickup anywhere, in place of its own. An unknown `agencyCode` is a `404`. Send the
`id` as `pickupLocationCode`.

**`GET /discovery/requests?page=0&size=100`** returns a page of the asserted patron's requests.
The page number parameter is `page`. (The generated API description calls it `number`, which DCB
ignores.) Always send `size`: without it DCB applies its server-wide maximum of 2500 rows. The
response is a page envelope, with the rows in `content`.
Each carries `id`, raw `status`, `discoveryStatus`, `statusDescription`, `outcome`,
`nextExpectedStatus`, `timeInState`, `title`, `pickupLocationCode`, `pickupLocationName`,
`activeWorkflow`, `bibClusterId`, `dateCreated`, `dateUpdated`. `activeWorkflow` of `RET-LOCAL`
means a same-library hold, which you will usually want to present separately from consortial
requests.

**`POST /discovery/requests/{patronRequestId}/cancel`** cancels the patron's own request.

**`GET /discovery/loans`** returns what the patron currently has out through DCB. Each entry
carries `id` (the request id, which is the only handle any patron action takes), `title`,
`bibClusterId`, the pickup location, `dueDate`, `dueDateKnown`, `renewalCount`, `renewable`,
`renewalStatus` and `dateUpdated`.

**Read `dueDateKnown` before you render `dueDate`.** DCB does not store a due date: it knows
a request reached `LOANED`, and it asks the borrowing library for the date when you call
this. `dueDateKnown: false` means that library could not be asked, or answered without one -
and "we could not find out" is a different sentence from "there is no due date". A patron
shown a blank cell will read it as the second and plan around it.

**This endpoint costs the member library one item read per loan**, which is why it is
separate from `GET /discovery/requests` rather than folded into it. The list is capped
(`dcb.discovery.max-loans`, default 25), the reads are throttled
(`dcb.discovery.loan-lookup-concurrency`, default 2, against what is usually a single
library) and each is time-bounded (`dcb.discovery.loan-lookup-timeout`, default 8s). Call it
when a patron opens their account, not on a timer.

**Loans a patron holds directly from their own library are NOT here.** DCB can only see what
passed through it. An account page that presents this as "everything you have out" is
wrong wherever the patron also borrows locally, which is nearly everywhere. `GET
/discovery/account` is the other half.

**`GET /discovery/account`** returns what the patron holds at their OWN library - the loans
and charges DCB never saw. It carries `available`, `loans`, `charges`, `totalLoans`,
`totalCharges`, `amountOwed` and `currency`. A loan carries `title`, `author`, `isbn`,
`localBibId`, `loanDate`, `dueDate` and `overdue`; a charge carries `title`, `description`,
`reason`, `state`, `amount`, `currency` and `accrualDate`.

**Read `available` before you render anything else.** A library DCB cannot ask and a patron
with nothing out both produce empty lists, and showing the second for the first tells a
patron they owe nothing and have nothing due. `available: false` is returned, with a 200,
whenever the home Host LMS has no route to a patron account, the patron cannot be found in
it, or it did not answer in time. **Say so on the page.**

**Not every Host LMS can answer this.** It is unimplemented by default, so a system with no
patron-account route is an absent capability rather than a silent zero. FOLIO answers it
through **edge-patron**, which the consortium must have deployed for that tenant and which
is off unless the Host LMS carries `patron-account: true` in its client configuration.
edge-patron finds a patron by `externalSystemId` and by nothing else - a FOLIO library that
does not populate that field on its user records cannot answer for its own patrons at all,
and every patron there sees `available: false`.

**This endpoint costs the member library ONE read**, not one per row, because the account
arrives whole. `dcb.discovery.max-account-rows` (default 25) caps the loans and charges
returned and `dcb.discovery.account-lookup-timeout` (default 8s) bounds the wait. It is
still patron data read live and stored nowhere: call it when a patron opens their account.

`localBibId` on a loan is the library's own bib record id. DCB offers no lookup from it to a
cluster, so linking a local loan to a catalogue record is up to your own index.

**`POST /discovery/patrons/resolve`** turns an institution sign-in into a borrower, for a
patron whose identity provider authenticated them against their institution rather than
their library and so cannot name their record in the library system. It takes your service
credential and **no** patron assertion, because this is the step that lets you mint one:

```json
{ "agencyCode": "ab6", "principal": "…" }
```

It answers `systemCode`, `patronId`, `agencyCode` and `homeLocationCode`, which are what a
patron assertion carries, and no name or barcode. `404` means the library has no such patron:
refuse borrowing, not the sign-in. `502` with `LIBRARY_UNAVAILABLE` means the library could
not be asked; `403` with `DISCOVERY_DISABLED` means this DCB accepts no discovery patrons.
Each call is a live read at a member library, so a service may make at most
`dcb.discovery.patron-resolves-per-minute` (default 600) a minute, counted from its first call
in the window. Beyond that it gets `429` with `TOO_MANY_RESOLVES` and `Retry-After: 60`.
`403` with `NOT_A_TRUSTED_SERVICE` means your bearer's client is not the `client-id` of any
trusted service.
Take `agencyCode` from your identity provider's configuration, never from anything the patron
sends.

`principal` is matched the way the library system matches a sign-in name, which differs by
system: Alma matches any unique user identifier (a SAML match point, for example), Sierra a
unique id and then a barcode, Koha a `userid`, and FOLIO and Polaris a barcode only. The
principal travels in the body so it never reaches a URL or an access log, and DCB does not log
it. It costs the library one read per call, bounded by `dcb.discovery.patron-resolve-timeout`
(default 8s), and nothing is cached, because the cache key would be the person.

#### Announcements

**`GET /discovery/announcements`** needs no authentication. Add `?libraryCode=` for a patron of
one library. It answers at most two notices, narrowest first:

1. The library's newest live notice; else the newest live notice of any group the library
   belongs to; else the consortium's newest live notice.
2. Beneath a library's notice, the newest live *urgent* notice of its groups or the consortium,
   whichever started last. Beneath a group's notice, the consortium's newest live urgent one.

With no library, an unknown one, or a library with nothing live at any level, the answer is the
consortium's newest live notice. Consortium administrators publish group and consortium notices;
a library administrator publishes only for their own library. Three rules are held by the
schema rather than by the code that writes it:

- **Every notice expires.** There is no "no expiry" value, because a banner still up months
  after it mattered teaches patrons to ignore the strip, including on the day it matters.
- **The body is plain text**, with no markup column and no flag that could enable one.
  Operator-written markup rendered into every patron's page would be stored XSS behind an admin
  form.
- **Precedence is a query per scope**, using `ScopeLevel` (`LIBRARY`, `LIBRARY_GROUP`,
  `CONSORTIUM`).

Dismissal is per patron and is not stored here: DCB holds no patron identity, so the discovery
service remembers it. A notice is one of discovery-affecting news (an ILS migration, a
requesting outage, a branch closure, a new source going live), not a newsletter: for those the
directory publishes the library's own website.

**`GET /discovery/libraries`** needs no authentication at all. Add `?includeAll=true` for
non-borrowing agencies. Each entry carries `code`, `hostLmsCode`, `name`, `latitude`,
`longitude`, `acceptsLibraryCard`, `secretKind` (`PIN`, `PASSWORD` or `NAME`: what the card is
checked against), `principalLabel` and `secretLabel` (the library's own wording for the two
sign-in fields, or null for your default), `brandLogoUrl`, `brandLogoAlt`, `defaultThemeName`,
`patronWebsite`, `supportUrl` and `classificationScheme`. Coordinates may be null — filter
accordingly if you are doing nearest-library resolution.

### 6.5 Responses to handle

| Status | Body | Meaning |
|---|---|---|
| `200` | payload | Fine. |
| `202` | `{"id": …}` | Cancellation accepted. `CANCELLED` lands asynchronously — poll `GET /discovery/requests`, do not assume it is immediate. |
| `400` | `{"failedChecks": [...]}` | Preflight rejection on placement. Each entry carries `code`, `description`, `notes` and `userMessage`. Show the patron `userMessage`; the codes are listed below. |
| `400` | Micronaut's standard error body | A malformed body, or a chosen copy with only one or two of its three `item*` fields. Not every `400` carries `failedChecks`, so check for it. |
| `401` | `{"code": "INVALID_PATRON_ASSERTION"}` | Your assertion was not accepted. Deliberately does not say why. Check issuer, audience, `kid`, `alg`, clock skew, lifetime, and that you sent it with your own client's token. It is also what every patron-scoped route answers while discovery is disabled. |
| `403` | `{"code": "DISCOVERY_DISABLED"}` | From `/discovery/patrons/resolve` only: this deployment accepts no discovery patrons. |
| `403` | `{"code": "NOT_A_TRUSTED_SERVICE"}` | From `/discovery/patrons/resolve` only: your bearer's client is not the `client-id` of any trusted service. |
| `404` | — | No such request *for this patron*. Not-yours and not-found are the same answer by design. From `/discovery/patrons/resolve`, the library has no such patron, or DCB does not know the agency you named. |
| `409` | `{"code": "NOT_CANCELLABLE", "discoveryStatus": …}` | Not cancellable now: too early (not yet placed at the patron's library), too late, or already cancelled. `discoveryStatus` says which; `CANCELLED` means an earlier cancellation worked. |
| `429` | `{"code": "TOO_MANY_RESOLVES"}` | From `/discovery/patrons/resolve` only: your service has resolved `dcb.discovery.patron-resolves-per-minute` sign-ins this minute. Retry after the `Retry-After` seconds. |
| `502` | `{"code": "CANCELLATION_NOT_CONFIRMED"}` | The borrowing system did not confirm. **The hold may still be live.** Do not tell the patron it is cancelled — direct them to library staff. |
| `502` | `{"code": "LIBRARY_UNAVAILABLE"}` | From `/discovery/patrons/resolve`: the library system could not be asked. |
| `500` | — | From cancel, treat it exactly like `CANCELLATION_NOT_CONFIRMED`: the hold may still be live. |

The preflight failure codes are a stable vocabulary: `EXCEEDS_GLOBAL_LIMIT`,
`EXCEEDS_AGENCY_LIMIT`, `REQUEST_LIMITS_UNCHECKED`, `PATRON_HOLD_LIMIT_REACHED`,
`DUPLICATE_REQUEST_ATTEMPT`, `INVALID_PATRON_BARCODE`, `PATRON_INELIGIBLE`, `PATRON_BLOCKED`,
`PATRON_INACTIVE`, `PATRON_EXPIRED`, `PATRON_DELETED`, `PATRON_NOT_FOUND`,
`PATRON_TYPE_NOT_MAPPED`, `LOCAL_PATRON_TYPE_IS_NON_NUMERIC`,
`PATRON_AGENCY_NOT_PARTICIPATING_IN_BORROWING`, `PATRON_NOT_ASSOCIATED_WITH_AGENCY`,
`UNKNOWN_BORROWING_HOST_LMS`, `NO_ITEM_SELECTABLE_FOR_REQUEST`, `CLUSTER_RECORD_NOT_FOUND`,
`UNKNOWN_PICKUP_LOCATION_CODE` and `UNKNOWN_PICKUP_LOCATION_AGENCY`. Codes may be added, so
show `userMessage` for any code you do not recognise rather than failing.

### 6.6 Rendering status

Either read `discoveryStatus` + `statusDescription` and render them, or read the raw `status`
and map it yourself. If you map it yourself, handle unknown values gracefully: DCB's state
machine gains states, and your integration should not break when it does. The
`discoveryStatus` vocabulary is the stable one; the raw `status` is the precise one.

### 6.7 Operational courtesy

- Rate-limit yourself. DCB itself does not rate-limit these routes; at most the deployment's
  ingress does. Behind them are real member library systems with real API budgets, some of them
  decades old.
- Do not poll `GET /discovery/requests` aggressively per patron session. Requests move on the
  timescale of physical stock in vans.
- Treat cancellation as idempotent from your side too; DCB will converge, but the network will
  not always tell you it did.

### 6.8 Testing your integration

`dcb.discovery.trusted-services[].jwks` accepts an inline JWKS, which is how DCB's own tests
onboard a service without an HTTP endpoint. The same mechanism works for an air-gapped or
local-development deployment, so you can exercise the full assertion path without publishing a
JWKS URL first.

### 6.9 The catalogue routes beside the contract

A discovery front end also shows records and availability. These routes need no credential and
no assertion, and they are not part of the patron contract above:

| Route | What it gives you |
|---|---|
| The consortium's search proxy | Search results. Each instance's `id` is the `bibClusterId` that placement takes |
| `GET /clusters?since=&page=&size=` | Every cluster, paged, for a layer that keeps its own index. `since` limits it to clusters changed after that instant |
| `GET /clusters/{id}` | One cluster |
| `GET /items/availability?clusteredBibId=` | Live availability for one cluster. Each item carries `id`, `hostLmsCode`, `agency.code`, `isRequestable` and `parsedVolumeStatement` |
| `GET /items/availability/batch?clusteredBibId=…` | The same for up to 25 clusters at once |
| `GET /discovery/consortium`, `GET /discovery/announcements` | The consortium's brand and its current announcements |

There is no lookup from a library's own bib id or an ISBN to a cluster. A layer with its own
index must harvest clusters from `GET /clusters` or take ids from the search proxy.

Live availability reaches every member library that holds the record, so it is the most
expensive call you can make. Use the batch route for a results page rather than one call per
row, and do not refresh it on a timer.

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
(§7.2), because DCB rejects their assertions unless the token's `azp` matches it. **Check this
for every existing trust entry before upgrading to a DCB that binds the client:** an entry whose
client id differs from its `service-id`, and has no `client-id`, stops working at the upgrade.
An identity provider other than Keycloak must stamp `azp` on its access tokens.

Hand the client id and secret over a channel that is not email.

Nothing about this step depends on how DCB is hosted. Keycloak is a separate system, and a
discovery service's credential is issued there whether DCB runs on Kubernetes, Fargate, or a
laptop. DCB validates the bearer token against `KEYCLOAK_CERT_URL` exactly as it does for every
other role, so if that variable is already set for staff traffic, it needs no new DCB wiring.

### 7.2 Part two: everything can be an environment variable

The whole `dcb.discovery` block is deliverable through the environment, which means a
deployment with no way to mount a config file is fully supported. The scalars and the list
work differently, though, and the difference is not arbitrary:

| Environment variable | Config key | Type | Default | Required |
|---|---|---|---|---|
| `DCB_DISCOVERY_ENABLED` | `dcb.discovery.enabled` | boolean | `false` | yes, to serve discovery at all |
| `DCB_DISCOVERY_AUDIENCE` | `dcb.discovery.audience` | string | `dcb` | recommended: set a value unique to this deployment, such as its base URL |
| `DCB_DISCOVERY_MAX_ASSERTION_LIFETIME` | `dcb.discovery.max-assertion-lifetime` | ISO-8601 duration | `PT2M` | no |
| `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` | `dcb.discovery.trusted-services-json` (merged into `trusted-services`) | JSON array (string) | unset; set but blank stops startup | yes, unless supplied as YAML |

Two further variables are not discovery-specific but decide whether the above arrives at all:

| Environment variable | Why discovery cares |
|---|---|
| `MICRONAUT_CONFIG_FILES` | Comma-separated paths to external config files. The only way a YAML `trusted-services` list reaches the process. Ignore it if you use the JSON variable. |
| `KEYCLOAK_CERT_URL` | Validates the discovery service's bearer token. Without it every `/discovery/requests` call is a `401` before assertions are even considered. |

The three discovery scalars are literal defaults in `application.yml`, and these variables
override them because the environment outranks that file.

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

Inline `jwks` works here too, for the egress-less case in §7.6. Add `"client-id"` to an entry
only when the vendor's Keycloak client id differs from its `service-id` (§7.1).

Every deployment ships with the audience `dcb`. A discovery service that serves two consortia
would then mint assertions either deployment accepts. Set `DCB_DISCOVERY_AUDIENCE` to a value
unique to this deployment, and tell the vendor (§6.1).

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
to avoid. (`DiscoveryServicePropertiesBindingTests` pins the JSON parsing and the indexed-env-var
limitation, so nobody removes the JSON route on the assumption that indices would do. It calls the
property setters directly. No test yet binds the variable through a running application.)

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
- **Incomplete or blank entry.** An entry missing `service-id`, `issuer`, or both of
  `jwks-uri`/`jwks`, or with any of its fields set but blank, stops the application starting and
  names the entry. So does `DCB_DISCOVERY_TRUSTED_SERVICES_JSON` set but blank, and a blank
  `audience` or a non-positive `max-assertion-lifetime`. Leave a setting out to take its default.
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

The `dcb-locate` search proxy reads the shared index and never calls dcb-service, so Locate's
`ADMIN` credential is used only on the calls below, which Locate makes to dcb-service directly:

| Surface | Endpoint | Security | Touched by this branch? |
|---|---|---|---|
| Pickup locations | `GET /agencies/{id}/pickupLocations` | `@Secured(ADMINISTRATOR)` (class default) | No |
| Pickup locations (alternative) | `GET /locations` (list); `GET /locations/{id}` | list anonymous; `/{id}` `@Secured(ADMINISTRATOR)` (class default) | No |
| Patron authentication | `POST /patron/auth`, `POST /v2/patron/auth` | anonymous (§2.7) | No |
| Patron lookup | `POST /patron/auth/lookup`, `POST /v2/patron/auth/lookup` | `{ADMINISTRATOR, INTERNAL_API, CONSORTIUM_ADMIN, LIBRARY_ADMIN, LIBRARY_READ_ONLY}` | No |
| Agencies | `POST /graphql` | `isAuthenticated()` via `intercept-url-map` at the URL, then `STAFF` roles in the data fetcher | No |
| Live availability | `GET /items/availability?clusteredBibId=` | anonymous, deliberately public | No |
| Placement | `POST /patrons/requests/place` | `{CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN, LIBRARY_READ_ONLY, INTERNAL_API}` | Role set made explicit; ADMIN retained |
| Patron request summary | `GET /patrons/requests/patrons/{hostLmsCode}/requests?barcode=…` | `@Secured(ADMINISTRATOR)` | **Renamed — restored, see §8.4** |
| Request browse | `GET /patrons/requests` | `@Secured(ADMINISTRATOR)` | Narrowed to `ADMINISTRATOR`; marked LEGACY |

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
  and nothing else. `DiscoveryApiSecurityTests` fails if a `CONSORTIUM_ADMIN` or a
  `DISCOVERY_SERVICE` token reaches either of them — which is what happens if someone folds the
  alias back onto the current method and it inherits the wider role set. It does not try every
  other role, so review any change to those two `@Secured` lines by eye. A second
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
- `DiscoveryServicePropertiesBindingTests` — pins the JSON parsing in §7.2, including the
  indexed-environment-variable limitation that makes `DCB_DISCOVERY_TRUSTED_SERVICES_JSON`
  necessary.
- `DiscoveryApiSecurityTests`, `PatronAssertionVerifierTests`,
  `PatronRequestCancellationServiceTests`, `PatronStatusMapperTests`, `DiscoveryLibrariesApiTests`
  — behavioural coverage of the surface.


# The DCB discovery service contract

What a discovery service may call on an OpenRS DCB, what it must send, and what DCB undertakes in
return. It is for vendors building a discovery layer for an OpenRS consortium.

Why DCB works this way is in [the discovery service approach](discovery-service-approach.md). How a
consortium's operator onboards your service is its [§7](discovery-service-approach.md#7-onboarding-in-practice-deployment-mechanics).

---

## 1. What DCB offers a discovery service

Everything a discovery service does on a patron's behalf lives under `/discovery/`:

| Route                                         | Auth                                   | Purpose                                                                                          |
|-----------------------------------------------|----------------------------------------|--------------------------------------------------------------------------------------------------|
| `GET /discovery/libraries`                    | anonymous                              | Public library directory (fields in §7)                                                          |
| `GET /discovery/pickup-locations?agencyCode=` | anonymous                              | Where a patron of that library may collect a request (§7)                                        |
| `GET /discovery/consortium`                   | anonymous                              | The consortium's own brand                                                                       |
| `GET /discovery/announcements`                | anonymous                              | The notices live now, for a library, its groups and the consortium                               |
| `GET /discovery/brand-assets/{key}`           | anonymous                              | A logo or other brand image named by the routes above                                            |
| `GET /discovery/requests`                     | `DISCOVERY_SERVICE` + patron assertion | The asserted patron's requests, paged                                                            |
| `POST /discovery/requests/place`              | `DISCOVERY_SERVICE` + patron assertion | Place a request as the asserted patron                                                           |
| `POST /discovery/requests/place/expedited`    | `DISCOVERY_SERVICE` + patron assertion | Check out the item the asserted patron is holding (walk-up). This creates a **loan**, not a hold |
| `POST /discovery/requests/{id}/cancel`        | `DISCOVERY_SERVICE` + patron assertion | Cancel the asserted patron's own request                                                         |
| `GET /discovery/loans`                        | `DISCOVERY_SERVICE` + patron assertion | What the asserted patron has on loan through DCB                                                 |
| `GET /discovery/account`                      | `DISCOVERY_SERVICE` + patron assertion | Loans and charges at the asserted patron's own library                                           |
| `POST /discovery/patrons/resolve`             | `DISCOVERY_SERVICE`, no assertion      | Turn an institution sign-in into a borrower, so that you can mint an assertion                   |

Two kinds of call fall outside `/discovery/`. Checking a library card uses DCB's patron
authentication endpoints, which are unchanged since v8.71.0 (§3). Search results, cluster ids
and live availability come from anonymous catalogue routes (§13).

Only routes under `/discovery/` carry the `DISCOVERY_SERVICE` role, and a build-time test
enforces that for REST routes ([approach §4](discovery-service-approach.md#4-secure-by-design-compliance)). The staff REST API is not reachable with a discovery
credential. Every route under `/discovery/` either requires `DISCOVERY_SERVICE` or is anonymous.
The anonymous ones are read-only and return no patron data.

A patron-scoped call carries two credentials:

```
Authorization: Bearer <client credential>      -> WHO is calling  (Keycloak, DISCOVERY_SERVICE)
X-OpenRS-Patron-Assertion: <signed JWT>        -> WHICH PATRON    (caller's key, DCB-verified)
```

## 2. The contract

The contract is the routes under `/discovery/` (§1), and these terms:

|                | The discovery service                                                                                                                                         | DCB                                                                                                                                                                                                                                                                                                |
|----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Who is calling | Calls with an access token from its own confidential Keycloak client, holding `DISCOVERY_SERVICE` (§4)                                                        | Accepts that role only under `/discovery/`, and only from the client named in the service's trust entry, matched on the token's `azp` ([approach §2.2](discovery-service-approach.md#22-caller-identity-and-patron-identity-were-separated))                                                       |
| Which patron   | Authenticates the patron: itself, through DCB's library-card check (§3), or through `/discovery/patrons/resolve`. Then signs an RS256 patron assertion (§6)   | Checks the signature, issuer, audience and expiry, and caps the lifetime (`PT2M` by default). Acts only as that patron, and decides ownership itself: a request that is not the patron's is a `404` ([approach §2.3](discovery-service-approach.md#23-ownership-is-enforced-in-dcb-not-delegated)) |
| Keys           | Publishes its public keys at a JWKS URL over HTTPS, each with a `kid` (§5)                                                                                    | Keeps the keys for 15 minutes, and fetches them again for a `kid` it has not seen ([approach §7.6](discovery-service-approach.md#76-part-three-egress-to-the-vendors-jwks-url))                                                                                                                    |
| Scope          | Serves the whole consortium (§5)                                                                                                                              | Lets a trusted service act for any patron at any library in the consortium                                                                                                                                                                                                                         |
| Load           | Limits its own rate of calls. Calls loans and account only when a patron opens their account. Uses the batch availability route for a results page (§11, §13) | Caps loans at 25 and account rows at 25 per call, institution sign-ins at 600 a minute per service, and each live read at the patron's library at 8 seconds (§7)                                                                                                                                   |
| Errors         | Handles the responses in §8. Shows `userMessage` for a preflight code it does not know. Never shows "the library could not be asked" as a wrong PIN (§3)      | Answers with the statuses and codes in §8. Not-yours and not-found are the same `404`                                                                                                                                                                                                              |
| Change         | Ignores fields, preflight codes and raw statuses it does not know                                                                                             | May add fields, preflight codes and raw statuses. `discoveryStatus` is the stable vocabulary (§9)                                                                                                                                                                                                  |

Outside the contract, but open to every discovery service: the library-card check (§3) and the
catalogue routes (§13).

## 3. Signing a patron in

DCB checks a patron's library card for any discovery service, through the patron's own library
system. These endpoints are open to every discovery service, and need no DCB credential and no
patron assertion. Signing in comes before the contract: until it answers, there is no patron to
assert.

| Route                  | Auth      | What it does                                                                                                                                                                                                                         |
|------------------------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST /patron/auth`    | anonymous | Checks a barcode and its PIN, password or name against the patron's home library system, through that library's adapter. Answers `status` (`VALID` or `INVALID`), `localPatronId`, `systemCode`, `agencyCode` and `homeLocationCode` |
| `POST /v2/patron/auth` | anonymous | The same check, with the agency carried in the principal as `AGENCY/barcode`                                                                                                                                                         |

For a library-card sign-in, the sequence is:

1. Call `POST /patron/auth` with `agencyCode`, `patronPrinciple` (the barcode) and `secret`.
   `GET /discovery/libraries` tells you which libraries accept a card (`acceptsLibraryCard`) and
   what the secret is (`secretKind`).
2. On `VALID`, map `systemCode` to `localSystemCode`, and the first entry of `localPatronId` to
   `localSystemPatronId`.
3. Mint the patron assertion (§6), and send it with your `/discovery/` calls.

Only a `200` that carries a `status` is an answer. Any other response means the library system
could not be asked. Never show it to the patron as a wrong PIN.

`INVALID` can also mean that DCB does not know the `agencyCode` you sent. Take the code from
`GET /discovery/libraries` rather than from anything the patron types.

DCB does not rate-limit these checks or lock out a card after failed attempts. Throttle sign-in
attempts yourself, per card and per client, as you would on any login form.

A patron whom your identity provider signed in through their institution, not their library, has
no library credential to check. For that patron, use `POST /discovery/patrons/resolve` (§7). It
is part of the contract because it needs your service credential. It does for a discovery service
what `POST /v2/patron/auth/lookup` does for EBSCO Locate, which calls that route with its `ADMIN`
credential ([approach §8.3](discovery-service-approach.md#83-the-surfaces-locate-uses)). A `DISCOVERY_SERVICE` credential cannot reach the lookup, because the role is
refused outside `/discovery/` ([approach §4](discovery-service-approach.md#4-secure-by-design-compliance)).

## 4. What you will need from the consortium's DCB operator

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

## 5. What the operator will need from you

An entry in `dcb.discovery.trusted-services-json`, a JSON array the operator holds in a config
file or in one environment variable ([approach §7.2](discovery-service-approach.md#72-part-two-everything-can-be-an-environment-variable)):

```yaml
dcb:
  discovery:
    enabled: true
    audience: dcb
    max-assertion-lifetime: PT2M
    trusted-services-json: >-
      [{"service-id": "your-service-id",
        "issuer": "https://discovery.example.org",
        "jwks-uri": "https://discovery.example.org/.well-known/jwks.json"}]
```

Add `"client-id": "your-keycloak-client"` to the entry only if it differs from `service-id`.

So, concretely: a stable service id, the `iss` value you will put in your assertions, and a
JWKS endpoint publishing your public signing keys. `client-id` is the Keycloak client your
calls arrive with, and it defaults to `service-id`. A new key at the same JWKS URL is accepted
on first use ([approach §7.9](discovery-service-approach.md#79-offboarding-and-rotation)). A change of issuer or JWKS URL is a configuration change and should be
reviewed as one.

**A trusted service can act for any patron at any library in the consortium.** Onboard only a
service that discovers for the whole consortium. A product serving one library hands its patrons
off to the consortium's discovery front end rather than becoming a trusted service.

Your JWKS endpoint must be served over HTTPS and reachable from the DCB deployment, and each key
in it must carry the `kid` you put in your assertion headers. If it sits behind an IP allow-list,
say so during onboarding — the operator can supply a stable egress address ([approach §7.6](discovery-service-approach.md#76-part-three-egress-to-the-vendors-jwks-url)). If your
platform cannot serve a JWKS at all, the operator can hold your public key inline (`jwks`,
[approach §7.6](discovery-service-approach.md#76-part-three-egress-to-the-vendors-jwks-url)), at the cost of updating it by hand whenever you rotate.

How the operator actually applies this configuration depends on how their DCB is hosted; [approach §7](discovery-service-approach.md#7-onboarding-in-practice-deployment-mechanics)
covers the Kubernetes and AWS Fargate routes.

Generate an RSA key pair. DCB accepts RS256 signatures only and rejects every other algorithm,
EC and RSA-PSS included. The private key stays in your backend. DCB never sees it and never
needs to.

## 6. Minting a patron assertion

Every call that acts on a patron carries one, signed with your key:

**Header:** `alg` `RS256` (the only algorithm DCB accepts), `kid` (matching a key in your JWKS)

**Claims:**

| Claim                 | Value                                                                                                 |
|-----------------------|-------------------------------------------------------------------------------------------------------|
| `iss`                 | your configured issuer, exactly                                                                       |
| `sub`                 | your configured `service-id` — the *service*, not the patron                                          |
| `aud`                 | the deployment's configured audience                                                                  |
| `iat`                 | now. This is the only time claim with any clock tolerance: up to 60 seconds in the future is accepted |
| `exp`                 | `iat` + your chosen lifetime, **not exceeding** `max-assertion-lifetime`. Checked with no tolerance   |
| `nbf`                 | optional. Checked with no tolerance, so omit it or set it a few seconds before `iat`                  |
| `localSystemCode`     | the patron's home Host LMS code, as DCB knows it                                                      |
| `localSystemPatronId` | the patron's local id in that system                                                                  |

Send the assertion with your own client's access token. DCB rejects an assertion whose signing
service is not the client that presented it, with the same `401` as any other rejection.

Mint one per request, or per short-lived patron session. Do not cache them for hours; DCB will
reject an over-long lifetime outright, and the bound exists precisely so that a stolen assertion
is worth almost nothing.

`localSystemCode` and `localSystemPatronId` are the patron identity contract. Get them from
whatever authentication you already perform against the patron's home library system. If you
check library cards through DCB, `POST /patron/auth` returns both (§3). **You are
asserting that you authenticated this patron.** DCB verifies that the assertion is genuinely
yours and unexpired; it cannot verify your login flow, so the strength of the whole chain is the
strength of that flow.

## 7. Making calls

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
whatever you send ([approach §2.3](discovery-service-approach.md#23-ownership-is-enforced-in-dcb-not-delegated)).

`pickupLocationCode` is a pickup location's `id`, a UUID, taken from
`GET /discovery/pickup-locations` below. A location code or name is refused at preflight with
`UNKNOWN_PICKUP_LOCATION_CODE`.

The three `item*` fields request one specific copy. Send all three, taken from that copy's `id`,
`hostLmsCode` and `agency.code` in live availability (§13), or send none and let DCB choose.
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
not be asked. While discovery is off on this DCB the route does not exist, and a call with a
token is refused with `401`.
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

### Announcements

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

### The library directory

**`GET /discovery/libraries`** needs no authentication at all. Add `?includeAll=true` for
non-borrowing agencies. Each entry carries `code`, `hostLmsCode`, `name`, `latitude`,
`longitude`, `acceptsLibraryCard`, `secretKind` (`PIN`, `PASSWORD` or `NAME`: what the card is
checked against), `principalLabel` and `secretLabel` (the library's own wording for the two
sign-in fields, or null for your default), `brandLogoUrl`, `brandLogoAlt`, `defaultThemeName`,
`patronWebsite`, `supportUrl` and `classificationScheme`. Coordinates may be null — filter
accordingly if you are doing nearest-library resolution.

## 8. Responses to handle

| Status | Body                                                | Meaning                                                                                                                                                                                                 |
|--------|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `200`  | payload                                             | Fine.                                                                                                                                                                                                   |
| `202`  | `{"id": …}`                                         | Cancellation accepted. `CANCELLED` lands asynchronously — poll `GET /discovery/requests`, do not assume it is immediate.                                                                                |
| `400`  | `{"failedChecks": [...]}`                           | Preflight rejection on placement. Each entry carries `code`, `description`, `notes` and `userMessage`. Show the patron `userMessage`; the codes are listed below.                                       |
| `400`  | Micronaut's standard error body                     | A malformed body, or a chosen copy with only one or two of its three `item*` fields. Not every `400` carries `failedChecks`, so check for it.                                                           |
| `401`  | `{"code": "INVALID_PATRON_ASSERTION"}`              | Your assertion was not accepted. Deliberately does not say why. Check issuer, audience, `kid`, `alg`, clock skew, lifetime, and that you sent it with your own client's token.                          |
| `401`  | no `code`                                           | Every patron-scoped route while discovery is off on this DCB: the routes do not exist.                                                                                                                  |
| `403`  | `{"code": "NOT_A_TRUSTED_SERVICE"}`                 | From `/discovery/patrons/resolve` only: your bearer's client is not the `client-id` of any trusted service.                                                                                             |
| `404`  | —                                                   | No such request *for this patron*. Not-yours and not-found are the same answer by design. From `/discovery/patrons/resolve`, the library has no such patron, or DCB does not know the agency you named. |
| `409`  | `{"code": "NOT_CANCELLABLE", "discoveryStatus": …}` | Not cancellable now: too early (not yet placed at the patron's library), too late, or already cancelled. `discoveryStatus` says which; `CANCELLED` means an earlier cancellation worked.                |
| `429`  | `{"code": "TOO_MANY_RESOLVES"}`                     | From `/discovery/patrons/resolve` only: your service has resolved `dcb.discovery.patron-resolves-per-minute` sign-ins this minute. Retry after the `Retry-After` seconds.                               |
| `502`  | `{"code": "CANCELLATION_NOT_CONFIRMED"}`            | The borrowing system did not confirm. **The hold may still be live.** Do not tell the patron it is cancelled — direct them to library staff.                                                            |
| `502`  | `{"code": "LIBRARY_UNAVAILABLE"}`                   | From `/discovery/patrons/resolve`: the library system could not be asked.                                                                                                                               |
| `500`  | —                                                   | From cancel, treat it exactly like `CANCELLATION_NOT_CONFIRMED`: the hold may still be live.                                                                                                            |

The preflight failure codes are a stable vocabulary: `EXCEEDS_GLOBAL_LIMIT`,
`EXCEEDS_AGENCY_LIMIT`, `REQUEST_LIMITS_UNCHECKED`, `PATRON_HOLD_LIMIT_REACHED`,
`DUPLICATE_REQUEST_ATTEMPT`, `INVALID_PATRON_BARCODE`, `PATRON_INELIGIBLE`, `PATRON_BLOCKED`,
`PATRON_INACTIVE`, `PATRON_EXPIRED`, `PATRON_DELETED`, `PATRON_NOT_FOUND`,
`PATRON_TYPE_NOT_MAPPED`, `LOCAL_PATRON_TYPE_IS_NON_NUMERIC`,
`PATRON_AGENCY_NOT_PARTICIPATING_IN_BORROWING`, `PATRON_NOT_ASSOCIATED_WITH_AGENCY`,
`UNKNOWN_BORROWING_HOST_LMS`, `NO_ITEM_SELECTABLE_FOR_REQUEST`, `CLUSTER_RECORD_NOT_FOUND`,
`UNKNOWN_PICKUP_LOCATION_CODE` and `UNKNOWN_PICKUP_LOCATION_AGENCY`. Codes may be added, so
show `userMessage` for any code you do not recognise rather than failing.

## 9. Rendering status

The response carries **both**: the raw `status` for a discovery service that wants to do its
own mapping, and `discoveryStatus` + `statusDescription` for one that doesn't.

Either read `discoveryStatus` + `statusDescription` and render them, or read the raw `status`
and map it yourself. If you map it yourself, handle unknown values gracefully: DCB's state
machine gains states, and your integration should not break when it does. The
`discoveryStatus` vocabulary is the stable one; the raw `status` is the precise one.

`errorMessage` is not returned to a discovery service. It is raw internal exception text, which a
patron can act on none of.

## 10. Cancellation

`POST /discovery/requests/{patronRequestId}/cancel` cancels the patron's hold at their own library
system, as the patron could in their own catalogue. DCB's tracking then sees the cancelled hold and
moves the request to `CANCELLED`. Hence `202 Accepted`: `CANCELLED` arrives later, never from the
call itself.

- **Repeating it is safe while it is in progress.** If the hold is already cancelled, DCB does not
  call the library system again. Once the request has reached `CANCELLED`, a repeat call answers
  `409 NOT_CANCELLABLE` with `discoveryStatus` `CANCELLED`, which means the earlier cancellation
  worked.
- **Never a false success.** A cancellation the library system does not confirm is a failure
  (`502 CANCELLATION_NOT_CONFIRMED`), never a success. Telling a patron their request is
  cancelled while the item still ships is the worst outcome there is.
- **Recorded either way.** Confirmed and unconfirmed attempts both write an audit entry naming the
  patron and your service.

## 11. Operational courtesy

- Rate-limit yourself. Apart from `/discovery/patrons/resolve` (§7), DCB does not rate-limit these
  routes; at most the deployment's ingress does. Behind them are real member library systems with
  real API budgets, some of them decades old.
- Do not poll `GET /discovery/requests` aggressively per patron session. Requests move on the
  timescale of physical stock in vans.
- Treat cancellation as idempotent from your side too; DCB will converge, but the network will
  not always tell you it did.

## 12. Testing your integration

A `jwks` field in a `trusted-services-json` entry accepts an inline JWKS, which is how DCB's own tests
onboard a service without an HTTP endpoint. The same mechanism works for an air-gapped or
local-development deployment, so you can exercise the full assertion path without publishing a
JWKS URL first.

## 13. The catalogue routes beside the contract

A discovery front end also shows records and availability. These routes need no credential and
no assertion, and they are not part of the patron contract above:

| Route                                                       | What it gives you                                                                                                                    |
|-------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| The consortium's search proxy                               | Search results. Each instance's `id` is the `bibClusterId` that placement takes                                                      |
| `GET /clusters?since=&page=&size=`                          | Every cluster, paged, for a layer that keeps its own index. `since` limits it to clusters changed after that instant                 |
| `GET /clusters/{id}`                                        | One cluster                                                                                                                          |
| `GET /items/availability?clusteredBibId=`                   | Live availability for one cluster. Each item carries `id`, `hostLmsCode`, `agency.code`, `isRequestable` and `parsedVolumeStatement` |
| `GET /items/availability/batch?clusteredBibId=…`            | The same for up to 25 clusters at once                                                                                               |
| `GET /discovery/consortium`, `GET /discovery/announcements` | The consortium's brand and its current announcements                                                                                 |

There is no lookup from a library's own bib id or an ISBN to a cluster. A layer with its own
index must harvest clusters from `GET /clusters` or take ids from the search proxy.

Live availability reaches every member library that holds the record, so it is the most
expensive call you can make. Use the batch route for a results page rather than one call per
row, and do not refresh it on a timer.

## 14. Related

- [The discovery service approach](discovery-service-approach.md): why the contract looks the way
  it does, and its security model.
- [Its §7](discovery-service-approach.md#7-onboarding-in-practice-deployment-mechanics): how an operator onboards your service,
  on Kubernetes, Docker or systemd, and AWS ECS/Fargate.
- [Its §8](discovery-service-approach.md#8-the-one-exception-ebsco-locate): the one integration that predates this
  contract, and what it costs.

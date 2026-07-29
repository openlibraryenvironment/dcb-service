# Unified Host Interaction — Integration Guide

How to onboard a library into DCB under the unified host-interaction model:
add a **Foundation** integration (NCIP/SIP2 + custom overrides) for a given ILS,
or wire an **ORS Appliance** (declarative) integration for a library with no
usable API of its own. Includes how to test each.

See `docs/backlog/current/unified-host-interaction-plan.md` for the architecture
and `docs/non-imperative-support.md` for the design rationale.

---

## 1. The model in one picture

```
Workflow transition  (business milestone, e.g. REQUEST_PLACED_AT_BORROWING_AGENCY)
  └─ capability resolver         ── per host + role → strategy
       ├─ IMPERATIVE → HostLmsClient
       │     ├─ Sierra / Polaris / FOLIO / Alma        (existing vendor adapters)
       │     └─ FoundationClient  ── base protocol (NCIP/SIP2) + per-op overrides
       └─ DECLARATIVE → DeclarativeRequestTransport(ncip-v202) → ORSApplianceHostLMS
```

A library is configured entirely through **one `HostLms` record**:

- `clientType` — the Java client class DCB instantiates.
- `clientConfig` — a JSON/map holding a `capabilities` block plus client-specific keys.

The **capabilities** block has two scopes:

```yaml
capabilities:
  # --- outer lifecycle scope (LifecycleCapabilityResolver) ---
  borrowing-agency-request: { strategy: imperative | declarative, protocol: <p> }
  supplying-agency-request: { strategy: imperative | declarative, protocol: <p> }
  borrower-tracking:        { mode: scheduled-poll | event-driven, protocol: <p> }
  supplier-tracking:        { mode: scheduled-poll | event-driven, protocol: <p> }
  # --- inner Foundation scope (ImperativeCapabilityConfig) ---
  imperative:
    base-protocol: NCIP | SIP2
    ncip-endpoint-url: <url>
    overrides: { <op>: <beanName> }
```

**Defaults are safe:** any missing capability means `strategy: imperative` +
`mode: scheduled-poll`. Existing Sierra/Polaris/FOLIO/Alma hosts need no
`capabilities` block at all and are completely unaffected.

`declarative` / `event-driven` **must** name a `protocol` (currently `ncip-v202`)
or the resolver fails fast.

---

## 2. The four library profiles

| Profile | Library has | clientType | strategy |
|---------|-------------|------------|----------|
| A | NCIP (± SIP2), no vendor API | `FoundationClient` | imperative |
| B | NCIP + vendor API | `FoundationClient` (+ overrides) | imperative |
| C | Vendor API only | existing vendor client | imperative |
| D | Nothing usable | `ORSApplianceHostLMS` | declarative |

---

## 3. Adding a Foundation integration (profiles A & B)

`FoundationClient` is an imperative `HostLmsClient` that composes a **base
protocol adaptor** (NCIP today, SIP2 groundwork) with **per-operation
overrides**. Use it when the library speaks NCIP (and optionally has a vendor API
you want to plug in for specific operations).

### 3.1 Minimal NCIP host (profile A)

Create a `HostLms` with:

- **`clientType`** = `org.olf.dcb.core.interaction.foundation.FoundationClient`
- **`clientConfig`**:

```yaml
capabilities:
  imperative:
    base-protocol: NCIP
    ncip-endpoint-url: https://library-a.example.org/ncip
# NCIP envelope identity (legacy 'ncip' sub-block still honoured):
ncip:
  fromAgency: DCB-CENTRAL      # InitiationHeader/FromAgencyId
  toAgency:   LIBRARY-A        # InitiationHeader/ToAgencyId (defaults to host code)
  appProfileType: EZBORROW
```

That is enough for DCB to place/track requests using NCIP primitives
(`LookupUser`, `RequestItem`, `AcceptItem`, `LookupItem`, `CheckOutItem`,
`CheckInItem`, `CreateUser`). No `capabilities.borrowing-agency-request` block is
needed — imperative is the default.

### 3.2 NCIP + vendor-API overrides (profile B)

When NCIP covers ~90% but a few operations need the vendor's own API (e.g.
Evergreen renewals over OpenSRF), provide an **override bean** and reference it by
name. The override implements the relevant strategy interface and delegates the
rest to the base NCIP adaptor.

```java
@Bean
@Named("EvergreenRenewalOverride")   // must match the config value
public class EvergreenRenewalOverride implements CirculationStrategy {
  private final NcipAdaptor base;           // delegate for what NCIP does well
  private final HttpClient httpClient;      // client for the vendor workaround

  public EvergreenRenewalOverride(@Parameter("hostLms") HostLms lms,
      HttpClient httpClient, ObjectMapper objectMapper) {
    this.base = new NcipAdaptor(lms, httpClient);
    this.httpClient = httpClient;
    // ...
  }

  @Override public Mono<HostLmsRenewal> renew(HostLmsRenewal r) {
    // call the vendor API (e.g. OpenSRF) here
  }
  @Override public Mono<String> checkOutItem(CheckoutItemCommand c) {
    return base.checkOutItem(c);             // NCIP is fine, delegate
  }
  @Override public Mono<HostLmsItem> getItem(String localItemId) {
    return base.getItem(localItemId);
  }
}
```

Wire it in `clientConfig`:

```yaml
capabilities:
  imperative:
    base-protocol: NCIP
    ncip-endpoint-url: https://library-b.example.org/ncip
    overrides:
      renew: EvergreenRenewalOverride     # strategy key -> named bean
    evergreen-api-url: https://library-b.example.org/osrf   # any custom keys the override needs
```

Override resolution: `FoundationClient` reads `capabilities.imperative.overrides`;
for a configured key it resolves the named bean implementing that strategy,
otherwise it uses the base adaptor. `EvergreenExampleCustomOverride` in
`core.interaction.foundation.customisations` is a worked reference.

**Strategy interfaces available to override:** `CirculationStrategy`
(`checkOutItem`, `renew`, `getItem`) and `PatronStrategy` (`findPatron`,
`findVirtualPatron`). Extend the surface as real gaps appear — do not
pre-create speculative capability interfaces.

### 3.3 What FoundationClient does NOT implement yet

`FoundationClient` extends `AbstractHostLmsClient`, so any host operation DCB
has not wired for this integration returns `Mono.empty()` (never `null`). Growing
the `ProtocolAdaptor` surface (bib/delete/cancel families are `default`
not-implemented today) is done by lifecycle slice, per real need. SIP2 transport
is groundwork only — the message vocabulary exists but the TCP transport is not
yet wired.

---

## 4. Setting up an ORS Appliance integration (profile D)

For a library that cannot expose NCIP or a vendor API, an **external ORS
Appliance** speaks NCIP v2.02 on the library's behalf. DCB places requests
**declaratively** (one coarse `RequestItem`/`AcceptItem`) and the appliance
performs the local choreography. Tracking is **event-driven**: the appliance
POSTs NCIP messages back to DCB rather than DCB polling.

Create a `HostLms` with:

- **`clientType`** = `org.olf.dcb.request.lifecycle.ncip.ORSApplianceHostLMS`
- **`clientConfig`**:

```yaml
capabilities:
  supplying-agency-request: { strategy: declarative, protocol: ncip-v202 }
  borrowing-agency-request: { strategy: declarative, protocol: ncip-v202 }
  supplier-tracking:        { mode: event-driven,  protocol: ncip-v202 }
  borrower-tracking:        { mode: event-driven,  protocol: ncip-v202 }
# Outbound NCIP target + this peer's NCIP identity:
ncip-endpoint-url: https://appliance-z.example.org/ncip/v2_02
ncip-system-id: APPLIANCE-Z          # legacy: ncipSystemId
ncip-agency-id: APPLIANCE-Z          # optional; defaults to system id
```

And DCB's own NCIP identity (instance-wide application config, e.g.
`application.yml`):

```yaml
dcb:
  ncip:
    system-id: DCB-CENTRAL
    agency-id: DCB-CENTRAL
```

### 4.1 How the declarative flow runs

1. Workflow reaches supplier placement → resolver sees `declarative`/`ncip-v202`
   → `NcipSupplyingRequestStrategy` builds a `RequestItem` and POSTs it via
   `NcipDeclarativeRequestTransport` to `ncip-endpoint-url`.
2. Then borrower placement → `NcipBorrowingRequestStrategy` builds an `AcceptItem`
   and POSTs it.
3. Because tracking is `event-driven`, `PatronRequestWorkflowService` sets
   `nextScheduledPoll = null` — DCB does **not** poll these requests.
4. The appliance drives progress by POSTing NCIP messages
   (`ItemShipped`, `RequestItemResponse`, `AcceptItemResponse`, …) to DCB's
   inbound endpoint **`POST /ncip/v2_02`**. `NcipController` validates against the
   XSD, maps to a canonical `InboundLifecycleMessage`, and the evidence ingestor
   advances the workflow idempotently.

### 4.2 Peer authentication (JWT / JWKS)

Peer authentication is **wired on both directions** and gated by config. The
module now builds on the **Java 25** toolchain against
`com.k_int.mn:ki-mn-peer-auth:1.4.0`, so the earlier JVM-25 blocker is resolved.

- **Outbound** — `NcipDeclarativeRequestTransport` signs each POST via
  `NcipPeerAuthorizationService` (Nimbus, `NimbusPeerTokenSigner`).
- **Inbound** — `NcipController` rejects unauthorised messages via
  `NcipPeerAuthGuard`, which returns an NCIP `Problem` rather than a bare 401.
- **Key publication** — DCB serves its own public keys at
  `GET /peer-auth/.well-known/jwks.json`.

Configuration has **two halves, and a secured peer needs both.**

**(a) Per-HostLms — is this peer secured?** (`NcipPeerAuthProfile`, read from the
appliance's `clientConfig`.) Defaults to `INSECURE`, so existing hosts are
unaffected. `JWT_REQUIRED` fails fast at load unless all three companions are set:

```yaml
ncip-peer-auth-mode: JWT_REQUIRED        # or INSECURE (default)
ncip-peer-issuer:    https://appliance-z.example.org
ncip-peer-jwks-url:  https://appliance-z.example.org/.well-known/jwks.json
ncip-peer-audience:  dcb-central
ncip-system-id:      APPLIANCE-Z         # must equal the JWT `sub` and NCIP FromSystemId
```

**(b) Instance-wide — DCB's own identity and the trusted-peer register**
(`DcbPeerAuthProperties` → `DcbPeerAuthStore`, which backs the ki-mn-peer-auth
store SPI). Disabled by default; **both** flags must be on:

```yaml
dcb:
  peer-auth:
    enabled: true
    ncip:
      enabled: true          # both this AND the parent flag gate NCIP peer auth
    local-identity:          # DCB's signing identity, published at the JWKS endpoint
      id: dcb
      issuer: https://your-dcb/peer-auth
      subject: dcb-central
      audiences: [ appliance-z ]
      key-id: dcb-2026-01
      public-jwk:  '{"kty":"RSA",...}'
      private-jwk: '{"kty":"RSA",...}'   # inject from a secret, never commit
      token-lifetime: 5m
    trusted-peers:           # matched to an inbound token by `issuer`
      - peer-id: appliance-z
        issuer: https://appliance-z.example.org
        jwks-uri: https://appliance-z.example.org/.well-known/jwks.json
        audiences: [ dcb-central ]
        subjects: [ appliance-z ]
        status: ACTIVE
        bindings:
          - protocol: ncip-v202
            system-id: APPLIANCE-Z     # ties the token to the NCIP peer identity
```

A trusted peer may supply an inline `jwks` map instead of `jwks-uri`. The
`bindings` block is what stops a validly-signed token from one peer being
replayed as another peer's NCIP system id. Key rotation at an unchanged JWKS URL
is picked up automatically; an issuer or URL change is a manual review step.

If a host is `JWT_REQUIRED` but `dcb.peer-auth` is off, requests are refused
rather than silently downgraded. While peer auth is off everywhere, restrict the
appliance ↔ DCB channel at the network layer. See also
`docs/ncip-peer-authentication.md`.

---

## 5. Testing a Foundation integration

### 5.1 Automated (already in the suite)

- `FoundationClientTests` — construction, delegation to the resolved strategy,
  and that unwired operations return `Mono.empty()` (not null).
- `ImperativeCapabilityConfigTests` — nested `capabilities.imperative` reading,
  top-level fallback, and base-protocol (SIP2) selection.
- `NcipPayloadBuilderTests` — NCIP payloads validate against the shared XSD.

Run: `./gradlew :dcb:test --tests "org.olf.dcb.core.interaction.foundation.*"`

### 5.2 Manual end-to-end

1. Create a `HostLms` as in §3 pointing `ncip-endpoint-url` at the library's NCIP
   responder (or a mock).
2. Confirm the client resolves:
   `GET /hostlmss/{code}` and check `clientType`; DCB logs
   `DCB-LIFECYCLE-CAPABILITY: ... placementStrategy=IMPERATIVE`.
3. Place a request through the normal DCB flow (`POST /patrons/requests/place`)
   with this library as borrower/supplier. Watch the audit log: you should see
   NCIP `LookupUser` / `RequestItem` / `AcceptItem` calls hit the endpoint.
4. Verify an overridden op (e.g. renew) hits the vendor API, not NCIP.

**Mock the ILS:** point `ncip-endpoint-url` at a MockServer returning canned NCIP
XML (see `mockserver-junit-jupiter`, already a test dependency) to exercise the
full flow without a live ILS.

---

## 6. Testing an ORS Appliance integration

### 6.1 Automated (already in the suite)

- `NcipBorrowingRequestStrategyContractTests` /
  `NcipSupplyingRequestStrategyContractTests` — the strategies self-identify as
  `DECLARATIVE` for `ncip-v202` (the keys the resolver selects on).
- `NcipPayloadBuilderTests` — outbound RequestItem/AcceptItem are XSD-valid.
- `NcipSchemaValidatorTests` — the shared validator enforces the XSD.
- `DefaultRequestTrackingPolicyTests` — event-driven config suppresses polling.
- Placement integration tests confirm DI health of the whole declarative closure.

Run: `./gradlew :dcb:test --tests "org.olf.dcb.request.lifecycle.*"`

### 6.2 Manual end-to-end (outbound placement)

1. Create the appliance `HostLms` as in §4. Point `ncip-endpoint-url` at a
   MockServer.
2. Place a request with the appliance library as supplier. Verify MockServer
   received **one** `RequestItem` POST, then (borrower phase) **one** `AcceptItem`
   POST, each valid NCIP v2.02.
3. Verify polling suppression: the `patron_request.next_scheduled_poll` column is
   `NULL` for the request (the scheduled tracker will not pick it up).

### 6.3 Manual end-to-end (inbound events)

Simulate the appliance driving progress by POSTing NCIP to DCB:

```bash
curl -X POST https://your-dcb/ncip/v2_02 \
  -H 'Content-Type: application/xml' \
  --data-binary @item-shipped.xml
```

- A valid message advances the correlated `PatronRequest` (check its status /
  audit) and returns the message-specific NCIP response (204 for
  `RequestItemResponse`/`AcceptItemResponse`).
- Invalid XML or an unmappable message returns an NCIP `Problem`.
- Re-POSTing the same message is idempotent (no duplicate transition).

Correlation ids follow `{patronRequestId}:SUPPLIER` / `{patronRequestId}:BORROWER`.

### 6.4 Full four-profile check

Configure one consortium with a host per profile (A–D) and place a request that
routes A↔D, B↔C, etc. Confirm each host reaches
`REQUEST_PLACED_AT_BORROWING_AGENCY` via its configured path — imperative hosts
via `HostLmsClient` calls, the appliance via NCIP POSTs — with no cross-host
contamination. (Automating this as a single harness is the remaining PR-8 item.)

---

## 7. Quick reference — config keys

| Key | Scope | Used by | Meaning |
|-----|-------|---------|---------|
| `clientType` | HostLms | `HostLmsService` | Java client class to instantiate |
| `capabilities.<role>.strategy` | outer | `LifecycleCapabilityResolver` | `imperative` / `declarative` |
| `capabilities.<role>.protocol` | outer | resolver | required when declarative (e.g. `ncip-v202`) |
| `capabilities.<tracking>.mode` | outer | `DefaultRequestTrackingPolicy` | `scheduled-poll` / `event-driven` |
| `capabilities.imperative.base-protocol` | inner | `FoundationClient` | `NCIP` / `SIP2` |
| `capabilities.imperative.ncip-endpoint-url` | inner | `NcipAdaptor` / transport | outbound NCIP URL |
| `capabilities.imperative.overrides.<op>` | inner | `FoundationClient` | named strategy override bean |
| `ncip-system-id` / `ncip-agency-id` | HostLms | `NcipHostLmsConfiguration` | peer NCIP identity |
| `dcb.ncip.system-id` / `dcb.ncip.agency-id` | app config | `NcipIdentityConfiguration` | DCB's own NCIP identity |
| `ncip-peer-auth-mode` | HostLms | `NcipPeerAuthProfile` | `JWT_REQUIRED` / `INSECURE` (default) |
| `ncip-peer-issuer` / `ncip-peer-jwks-url` / `ncip-peer-audience` | HostLms | `NcipPeerAuthProfile` | approved peer JWT metadata; required when `JWT_REQUIRED` |
| `dcb.peer-auth.enabled` | app config | `DcbPeerAuthProperties` | master JWT/JWKS peer-auth toggle (default off) |
| `dcb.peer-auth.ncip.enabled` | app config | `NcipPeerAuthGuard` / transport | enables peer auth for NCIP specifically |
| `dcb.peer-auth.local-identity.*` | app config | `DcbPeerAuthStore` | DCB's signing key, issuer, audiences, token lifetime |
| `dcb.peer-auth.trusted-peers[]` | app config | `DcbPeerAuthStore` | accepted peers: issuer, JWKS, audiences, protocol bindings |

Roles: `borrowing-agency-request`, `supplying-agency-request`,
`borrower-tracking`, `supplier-tracking`.

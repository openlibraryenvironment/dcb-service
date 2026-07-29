# Unified Host Interaction Model — Delivery Plan (Profiles A–D)

## Status

Active. Branch: `feat/unified-host-interaction`.

Supersedes the parallel exploration on `openrs-foundation` (NCIP-plus / mix-and-match)
and `spike/iso18626-declarative-dual-agency` (declarative / ORS-appliance) by merging
both into a single host-scoped capability model.

**Platform: up to date with `main` (2026-07-24).** The Micronaut 5 / JDK 25 / ES9 /
Jackson 3 upgrade from `main` has been merged in and the full suite is green. Spike is
a full ancestor (every spike feature is present by construction); the branch is ~95
commits ahead of `main`. See *Main merge & stabilisation* below for what the merge
reconciled and where unified deliberately diverges from `main`.

## Goal

Deliver the standard DCB workflow for four library profiles selected purely by
per-host configuration, with no behaviour change for existing Sierra/Polaris/FOLIO/Alma
integrations:

| Profile | Library has | Resolver picks | Implementation |
|---------|-------------|----------------|----------------|
| A | NCIP (± SIP2), no vendor API | Imperative | `FoundationClient` base=NCIP + overrides |
| B | NCIP + vendor API | Imperative | `FoundationClient` base=NCIP, overrides→API beans |
| C | Vendor API only | Imperative | existing Sierra/Polaris/FOLIO/Alma client |
| D | Nothing usable | Declarative | `DeclarativeStrategy` → NCIP transport → `ORSApplianceHostLMS` |

## Core model

The two branches operate at different altitudes and compose vertically:

```
Workflow transition  (business milestone: REQUEST_PLACED_AT_BORROWING_AGENCY)
  └─ Lifecycle capability resolver         ◄── host + role + operation → strategy (spike seam)
       ├─ IMPERATIVE strategy → HostLmsClient
       │     ├─ Sierra / Polaris / FOLIO / Alma   (existing vendor adapters)
       │     └─ FoundationClient                  ◄── base protocol + per-op overrides (foundation seam)
       │           └─ NcipAdaptor / Sip2Adaptor / EvergreenOverride ...
       └─ DECLARATIVE strategy → DeclarativeRequestTransport(ncip-v202) → ORSApplianceHostLMS
```

`FoundationClient` **is** a `HostLmsClient`; the imperative strategy **calls** a
`HostLmsClient`. They do not compete. The unifying primitive is config-driven
capability resolution with a safe default and named-bean overrides — applied at two
nested scopes (lifecycle strategy, then protocol primitive).

## What already exists

- **Spike** delivered the full lifecycle layer: `AbstractHostLmsClient`,
  `CanPlace{Borrowing,Supplying}AgencyRequest`, `request.lifecycle.*`
  (roles/operations/strategy-type/tracking-mode/capability-resolver), `placement.*`
  (both strategies + resolvers + services + projectors + imperative impls),
  `ncip.*` (declarative transport, ORS appliance client, inbound controller, peer auth),
  `tracking.*` (policy + inbound message handler).
- **Foundation** delivered: `foundation.{FoundationClient, ProtocolAdaptor, NcipAdaptor,
  Sip2Adaptor}`, `foundation.strategies.{CirculationStrategy, PatronStrategy}`,
  `foundation.customisations.*` (Evergreen override).

## The two integration gaps that matter

1. **Capability config is global, not host-scoped.** `LifecycleCapabilityResolver`
   resolves per `LifecycleRole` from a single global `LifecycleCapabilitiesConfiguration`.
   It cannot express "host X imperative, host Z declarative" simultaneously. Making
   resolution host-scoped is the central integration PR (PR-2).
2. **`FoundationClient` returns raw `null`** from ~25 `HostLmsClient` methods — must
   extend `AbstractHostLmsClient` (PR-3) or it NPEs the workflow.

## PR sequence

- **PR-1** Land the lifecycle seam (imperative-only, zero behaviour change).
- **PR-2** Make capability configuration host-scoped. *(critical)*
- **PR-3** `FoundationClient extends AbstractHostLmsClient`, resolvable as imperative client.
- **PR-4** One shared NCIP protocol module, consumed by both the imperative
  `NcipAdaptor` and the declarative transport. **Scope refined during delivery:**
  the two implementations diverge at the payload layer (Foundation = hand-rolled
  imperative item-or-bib messages + full primitive set; spike = structured
  declarative bib-only records). The shared module is therefore the
  *non-divergent* layer — `NcipProtocol` constants + `NcipSchemaValidator`/XSD in
  `core.interaction.ncip` — not a merged payload builder (that would be
  over-engineering two different message models into one).
- **PR-5** Unified host-scoped `capabilities` config schema (outer lifecycle scope +
  nested `imperative` Foundation scope).
- **PR-6** Bring the declarative NCIP leaf + ORS appliance client onto the branch.
  **Decomposed during delivery** (the strategy bean's DI closure = transport +
  payload builder + identity/address config + peerauth, so it cannot land as one
  tiny slice without breaking DI for every `@MicronautTest`):
  - **PR-6a (done):** outbound declarative payload layer (`NcipPayloadBuilder` +
    payload records) + protocol-neutral transport contract
    (`DeclarativeRequestTransport`/`Request`/`Response`). Pure classes, no beans
    that break DI. Proven: declarative RequestItem/AcceptItem/LookupItemSet
    validate against the *same* shared `core.interaction.ncip` XSD validator the
    Foundation adaptor uses.
  - **PR-6b (done):** the live declarative flow — concrete
    `NcipDeclarativeRequestTransport` + `peerauth` JWT subsystem (Nimbus already
    on classpath via `micronaut-security-jwt`), `NcipBorrowing/SupplyingRequestStrategy`
    (register as DECLARATIVE beans; resolver already filters them by protocol),
    `ORSApplianceHostLMS`, `NcipController` + inbound evidence/tracking. Reconcile
    the spike's `request.lifecycle.ncip.NcipProtocol/SchemaValidator/SchemaPath`
    duplicates against PR-4's `core.interaction.ncip` versions (repoint imports,
    do not bring duplicates).
- **PR-7** Tracking capability + poll suppression (`nextScheduledPoll = null`) + inbound
  message handling.
- **PR-8** Full A–D acceptance harness + docs.

## Delivery status

Done: PR-1..PR-7, the supplying seam + declarative supplying, `ORSApplianceHostLMS`,
the inbound leaf (`/ncip/v2_02` + evidence), both follow-ons (supplier-side
protocol persistence; per-host SUPPLIER capability resolution), the PR-8 ArchUnit
boundary guardrails, and the integration guide
(`docs/unified-host-interaction-integration-guide.md`).

Also done:
- **Peer authentication (JWT/JWKS)** — no longer blocked. The module builds on the
  **Java 25** toolchain against `com.k_int.mn:ki-mn-peer-auth:1.4.0`, and peer auth
  is wired in both directions: outbound signing in `NcipDeclarativeRequestTransport`
  via `NcipPeerAuthorizationService`, inbound enforcement in `NcipController` via
  `NcipPeerAuthGuard`, with DCB's keys published at
  `/peer-auth/.well-known/jwks.json`. Per-peer trust is declared on the HostLms
  (`ncip-peer-auth-mode`, default `INSECURE`); DCB's own identity and the
  trusted-peer register live under `dcb.peer-auth.*` (disabled by default). See the
  integration guide §4.2.

Deferred:
- **Automated four-profile acceptance harness** — the manual + per-slice
  automated coverage is documented in the integration guide (§5–6); a single
  A–D MockServer harness remains to be written.

## Main merge & stabilisation (2026-07-24)

`main`'s Micronaut 5 / JDK 25 / ES9 / Jackson 3 upgrade was merged into the branch.
The merge took `main`'s side on ~80 overlapping build/platform files; the following
casualties and collisions were reconciled so the full suite passes. Both the
Foundation (imperative) and ORS-appliance (declarative) pathways compile and test
green against the MN5 APIs.

**Merge casualties restored** (dropped when the merge took `main`'s `build.gradle`):
- `com.k_int.mn:ki-mn-peer-auth:1.4.0` **and** the `nexus.libsdev.k-int.com`
  repository that hosts it — without both, `DcbPeerAuthFactory` lost every
  `com.k_int.peerauth.*` class. Peer auth is not on Maven Central.
- `com.tngtech.archunit:archunit-junit5:1.3.0` — the PR-8 boundary guardrails.
- `testcontainers-postgresql` promoted back to `testImplementation` (the test
  container builder imports `PostgreSQLContainer` at compile time).

**Landmines avoided** (spike carried MN4-era duplicate fixes with the *same* commit
messages as `main`'s MN5 fixes; `main`'s versions were kept in every case): Graal
pre-analysis crash, indexing transaction wait, native-image CI, and `AppTask`. Spike's
duplicate `services.k_int.*.AppTaskAwareScheduledMethodProcessor` is gone; the canonical
processor remains at `io.micronaut.scheduling.processor` (where it must live to compile —
see the scheduler-subclass memory) and `@AppTask` is `main`'s
`@Executable(processOnStartup = true)`. The obsolete spike guardrail
`appTaskMustRemainMarkerOnly` (which asserted `@AppTask` carries no `@Executable`) was
removed; its valid siblings — TrackingScheduler owns scheduling, services do not
self-schedule — remain.

**Test infrastructure — deliberate divergence from `main`.** `main` uses the
Micronaut Test Resources plugin to auto-provision Postgres; unified uses spike's
hand-rolled `DcbTestContainerContextBuilder` (a `@MicronautTest(contextBuilder = …)`
that starts a `PostgreSQLContainer` and sets `datasources.*` / `r2dbc.datasources.*`
explicitly, including the `options.protocol` key the plugin could not resolve). The
merge left an incoherent hybrid; stabilisation converged fully on the builder —
removed the `io.micronaut.test-resources` plugin, its `testResourcesService`
platform, and the `micronaut { testResources { } }` block. Consequence: any
`main`-authored test that relied on auto-provisioning (e.g.
`OpenSearchSharedIndexIntegrationTests`) must declare
`contextBuilder = DcbTestContainerContextBuilder.class` to get a DB.

**NCIP schema fix (correctness, platform-independent).** The
`https://openrs.org/ncip/fallback-host` namespace is split across two XSD files
(`openrs_ncip_extension.xsd` = `ShippingContext`/`dcb:`;
`openrs-ncip-fallback-host.xsd` = `FallbackHost*`/`openrs:`). `xs:import` loads only
the first schema document seen for a namespace and silently skips the second, so only
half the namespace ever validated — this was a latent bug, **not** a JDK 25
regression. Fixed by composing the two files with `xs:include` (additive) rather than
two imports; `NcipSchemaValidator` was simplified to a direct base-schema load.

**Other differences from `main`:**
- `.gitlab-ci.yml` deleted — spike-only on-prem pipeline calling gradle tasks
  (`dockerPushSpikeNative`) that `main`'s `build.gradle` no longer defines. Native
  image publishing goes through GitHub Actions `dockerPushNativeBinary`, matching
  `main`.
- `processResources` given `DuplicatesStrategy.INCLUDE` — a latent `git.properties`
  collision (CI echo + `gradle-git-properties` plugin) that Gradle 8 tolerated and
  Gradle 9 fails hard on.
- Governance/FSL docs preserved (`AGENTS.md` superset, `ARCHITECTURE.md`, `ADR-0001`,
  both delivery docs) — none exist on `main`; all survived the merge.

## Unified config schema

```yaml
# Profile A: NCIP-only, renewals broken in NCIP → custom override
hostlms:
  code: EVERGREEN-A
  client: foundation
  capabilities:
    borrowing-agency-request: { strategy: imperative }
    supplying-agency-request: { strategy: imperative }
    borrower-tracking:        { mode: scheduled-poll }
    imperative:                       # Foundation inner scope
      base-protocol: NCIP
      ncip-endpoint-url: https://evergreen-a/ncip
      overrides:
        renew: EvergreenExampleCustomOverride   # named CirculationStrategy bean

# Profile D: nothing usable → external ORS appliance, event-driven
hostlms:
  code: APPLIANCE-Z
  client: ors-appliance
  capabilities:
    supplying-agency-request: { strategy: declarative, protocol: ncip-v202 }
    borrowing-agency-request: { strategy: declarative, protocol: ncip-v202 }
    supplier-tracking:        { mode: event-driven,  protocol: ncip-v202 }
    borrower-tracking:        { mode: event-driven,  protocol: ncip-v202 }
```

Profile B = A with `overrides` pointing at API-backed beans. Profile C = existing
vendor `client`, no `capabilities` block → imperative default.

## Architecture invariants (ArchUnit, every PR)

1. Workflow/transition code imports `placement.*` + `LifecycleRole/Operation` only —
   never `HostLmsClient` primitives, never `ncip.*`.
2. `ncip.*` is removable: delete it + config ⇒ imperative behaviour intact, suite green.
3. Non-NCIP vendor adapters never import declarative NCIP classes.
4. Capability detection is `instanceof CanPlace*` / static — no reflection (GraalVM-safe).
5. Missing config always means imperative + scheduled-poll.

## Guardrail

New abstraction first → legacy implementation as default → new behaviour opt-in by
host + capability config. Nothing changes for existing vendor integrations at any step.

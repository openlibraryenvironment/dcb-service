# Shared System ("Shared Server") Support — Codebase Review

**Scope:** `dcb-service`, branch `discovery` @ `fcd5a9711`. Analysis only — no code changed.

**Question:** do we genuinely support multiple libraries co-existing on one Host LMS?

**Answer:** the *data model* supports it. The *runtime* supports it in patches, with two
different and inconsistent notions of "same system", at least four adapter-level 1:1
assumptions, and one filter that throws on two of the five adapters. Shared-system support
is real for Sierra/FOLIO and effectively absent for Koha and Alma.

---

## 1. The model is right

`DataAgency` → `DataHostLms` is `MANY_TO_ONE` (`core/model/DataAgency.java:66`). N agencies
per Host LMS is the intended shape and nothing in the schema fights it. The data structure
is not the problem.

Three mechanisms exist to make N:1 work, and all three are sound in principle:

| Mechanism | Where | What it does |
|---|---|---|
| Location → Agency reference value mappings | `core/svc/LocationToAgencyMappingService.java` | Maps a local branch/location code to a DCB agency. This is *the* shared-system primitive. |
| `contextHierarchy` | `LocationToAgencyMappingService:117-145` | Lets one Host LMS resolve mappings through an ordered list of contexts (`["SYS","GLOBAL"]`), so per-library overrides layer over consortial defaults. |
| Agency participation flags | `DataAgency.isSupplyingAgency` / `isBorrowingAgency` | Excludes non-participating co-tenants. Supply side enforced at `item/availability/LiveAvailabilityService.java:226`; borrow side at `request/fulfilment/ResolvePatronPreflightCheck.java:160`. |

Two guards exist specifically for shared servers:

- `request/resolution/SameServerItemFilter.java` — drops an item when the item's Host LMS
  and the borrower's Host LMS resolve to the same base URL but are *different* Host LMS
  records. i.e. "two DCB Host LMS entries, one physical Sierra" → don't pretend that's ILL.
- `RequestWorkflowContextHelper.setPatronRequestWorkflow` (`:428-448`) — when lender and
  pickup agencies resolve to the same `HostLmsClient`, force `RET-LOCAL` instead of
  `RET-STD`/`RET-PUA`, i.e. no virtual bib/item, place a real hold on the real item
  (`request/fulfilment/BorrowingAgencyService.java:534 placeSingularRequest`).

So: the intent is present and coherent. The execution has holes.

---

## 2. Holes

### H1 — `SameServerItemFilter` throws on Koha and Alma. Blocking.

`HostLmsService.qualifiedBaseUrl` (`core/HostLmsService.java:243`) reads the config key
`base-url`. The adapters do not agree on that key:

| Adapter | URL config key | `getHostLmsQualifiedBaseUrl` |
|---|---|---|
| Sierra | `base-url` | works |
| FOLIO | `base-url` | works |
| Polaris | `base-url` | works |
| Dummy | `base-url` | works |
| **Koha** | **`api-url`** (`koha/KohaClientConfig.java:17`) | returns `null` |
| **Alma** | **`alma-url`** (`alma/AlmaClientConfig.java:20`) | returns `null` |

`Mono.map` with a mapper returning `null` signals `NullPointerException` — it does not
complete empty. `SameServerItemFilter.fromSameServer` zips two of these, so for any Koha or
Alma borrower the filter errors. `PatronRequestResolutionService.filterItems:347` propagates
that straight out of `filterWhen`, aborting resolution for the whole cluster.

**This filter has zero test coverage** — `SameServerItemFilter` appears in exactly one file
in the repo, its own.

**Fix.** Stop asking the config map for a URL. The client already knows its own identity —
`getClientId()` is the abstraction. Delete `getHostLmsBaseUrl`/`getHostLmsQualifiedBaseUrl`
from the same-server path:

```java
// SameServerItemFilter
private Mono<Boolean> fromSameServer(Item item, String borrowingHostLmsCode) {
    final var itemHostLmsCode = getValueOrNull(item, Item::getHostLmsCode);

    if (itemHostLmsCode == null || borrowingHostLmsCode == null) {
        return raiseError(Problem.builder()
            .withTitle("Missing required value to evaluate item fromSameServer")
            .withDetail("Could not compare LMS codes")
            .with("itemHostLmsCode", itemHostLmsCode)
            .with("borrowingHostLmsCode", borrowingHostLmsCode)
            .build());
    }

    if (itemHostLmsCode.equals(borrowingHostLmsCode)) {
        return Mono.just(true); // same Host LMS record - N agencies on one system is legal
    }

    return Mono.zip(
            hostLmsService.getClientFor(itemHostLmsCode),
            hostLmsService.getClientFor(borrowingHostLmsCode))
        .map(function((itemClient, borrowingClient) -> {
            final var sameServer = itemClient.compareTo(borrowingClient) == 0;
            if (sameServer) {
                log.debug("Excluding item from same server: itemLms={}, borrowingLms={}",
                    itemHostLmsCode, borrowingHostLmsCode);
            }
            return !sameServer;
        }));
}
```

This also removes the `BASE_URL_QUALIFIER` special case, because qualification becomes the
adapter's job (see H2). And it makes the filter agree with the workflow router, which
already uses `compareTo`.

### H2 — `getClientId()` means three different things. Two are wrong.

`getClientId()` is the *only* system-identity primitive: `HostLmsClient.compareTo:152`
compares on it, and `RequestWorkflowContextHelper:430` routes RET-LOCAL vs RET-STD on that
comparison. Implementations:

| Adapter | `getClientId()` | Consequence |
|---|---|---|
| Sierra | `client.getRootUri()` (`SierraLmsClient.java:2017`) | correct — two Host LMS on one Sierra compare equal |
| FOLIO | `rootUri.resolve("/")` (`ConsortialFolioHostLmsClient.java:1366`) | correct |
| Polaris | base URL (+ app services override) | correct |
| `AbstractHostLmsClient` | `hostLmsCode` (`:200`) | never detects a shared server |
| **Koha** | **`hostLms.getCode()`** (`KohaHostLmsClient.java:82`) | two Koha Host LMS on one server are never detected as shared |
| **Alma** | **`return "";`** (`AlmaHostLmsClient.java:1096`) | **every Alma client compares equal to every other Alma client** |

Alma is the severe one. Two unrelated Alma tenants — different institutions, different
consortia members — satisfy `ls.compareTo(ps) == 0`, so a genuine three-party RET-STD
request between two Alma libraries is misrouted to `LOCAL_WORKFLOW`. That path calls
`placeSingularRequest`, which takes the **borrower's** client and places a hold using the
**supplier's** `localBibId`/`localItemId` (`BorrowingAgencyService.java:550-556`). Against a
different tenant those IDs are meaningless: best case a 404, worst case a hold on an
unrelated record.

**Fix.** Make `getClientId()` a documented contract — "a stable identifier for the physical
system this client talks to; two clients addressing the same system MUST return equal
values" — and implement it consistently:

```java
// AlmaHostLmsClient
@Override
public @NonNull String getClientId() {
    return config.getBaseUrl().resolve("/").toString();
}

// KohaHostLmsClient
@Override
public String getClientId() {
    return config.getApiUrl().resolve("/").toString();
}
```

Then move the `base-url-qualifier` idea (currently stranded in `HostLmsService:243`, used
only by `SameServerItemFilter`) into `AbstractHostLmsClient` as a shared decorator, so
adapters like the NCIP/ORS appliance that multiplex logical systems over one transport URL
can still disambiguate:

```java
// AbstractHostLmsClient
protected String qualify(String systemIdentity) {
    final var qualifier = getValue(getConfig().get(BASE_URL_QUALIFIER), Object::toString, "").trim();
    return qualifier.isEmpty() ? systemIdentity : systemIdentity + "#" + qualifier;
}
```

Net effect: one notion of "same system" instead of two, and `getHostLmsBaseUrl` /
`getHostLmsQualifiedBaseUrl` / `BASE_URL` / `BASE_URL_QUALIFIER` all leave `HostLmsService`.

### H3 — Workflow routing ignores the patron when lender and pickup share a system.

`RequestWorkflowContextHelper.setPatronRequestWorkflow:422-431` compares only the **lender**
and **pickup** clients. If they match, it sets `LOCAL_WORKFLOW` unconditionally — including
when the patron is on a completely different Host LMS.

Concrete: patron at a Sierra library, pickup at Koha branch A, lender is Koha branch B.
Lender client == pickup client → `LOCAL_WORKFLOW`. But `placeSingularRequest` resolves its
client from the **borrowing identity** (`BorrowingAgencyService:507 fetchClientFor`) — the
Sierra client — and hands it the Koha bib/item IDs. Guaranteed failure, at a state where the
request has already been accepted.

This should be `PICKUP_ANYWHERE_WORKFLOW`.

**Fix.** Add the patron system to the comparison:

```java
final Mono<HostLmsClient> resolvePatronLms = resolveHostLmsClientForAgency(
    "patron", patronAc, rwc.getPatronAgency());

return Mono.zip(resolveLenderLms, resolvePickupLms, resolvePatronLms)
    // RET-LOCAL only holds when all three parties are the same physical system
    .filter(TupleUtils.predicate((ls, ps, bs) ->
        ls.compareTo(ps) == 0 && ls.compareTo(bs) == 0))
    .map(_systems -> rwc.setPatronRequest(pr.setActiveWorkflow(LOCAL_WORKFLOW)))
    // ... unchanged onErrorResume / switchIfEmpty
```

### H4 — The patron→agency path resolves differently in preflight and in the state machine.

Two implementations of the same question:

- `LocalPatronService.findHomeLocationMapping:63` → `LocationToAgencyMappingService.findLocationToAgencyMapping`
  — context-hierarchy aware, `*` wildcard aware, raises the missing-mapping alarm.
- `ValidatePatronTransition.findAgencyForLocation:183` → `referenceValueMappingService.findMapping`
  directly — **no hierarchy, no wildcard, no alarm**.

Preflight uses the first, the state transition uses the second. On any shared system
configured with `contextHierarchy` or a `Location:*` fallback, preflight passes and
`ValidatePatronTransition` then misses the mapping and silently falls through to
`findDefaultAgencyCode` (`:164`) — attributing the patron to a **different agency than the
one preflight approved**, or erroring mid-workflow on an already-accepted request.

**Fix.** Delete `ValidatePatronTransition.findAgencyForLocation` and
`findOneAgencyByCode`; call the shared service:

```java
private Mono<PatronIdentity> resolveHomeLibraryCodeFromSystemToAgencyCode(
    String systemCode, String homeLibraryCode, PatronIdentity pi) {

    if (systemCode == null)
        throw new RuntimeException("Missing system code. Unable to accept request");

    return Mono.justOrEmpty(homeLibraryCode)
        .flatMap(code -> locationToAgencyMappingService
            .findLocationToAgencyMapping(systemCode, code)
            .map(ReferenceValueMapping::getToValue))
        .switchIfEmpty(Mono.defer(() ->
            locationToAgencyMappingService.findDefaultAgencyCode(systemCode)))
        .switchIfEmpty(UnableToResolveAgencyProblem.raiseError(homeLibraryCode, systemCode))
        .flatMap(agencyService::findByCode)
        .switchIfEmpty(Mono.defer(() -> Mono.error(new NoAgencyFoundException(
            "Unable to resolve patron home library code(" + systemCode + "/" + homeLibraryCode + ") to an agency"))))
        .map(pi::setResolvedAgency);
}
```

Drops `ReferenceValueMappingService` and `AgencyRepository` from the transition's
constructor. One resolution path, one answer.

### H5 — `default-agency-code` is a silent correctness hole on any shared system.

Both patron resolution paths fall back to a single per-Host-LMS
`default-agency-code` (`HostLmsClient.java:58`) when the home library code has no mapping.
On a single-tenant Host LMS that is a sensible convenience. On a shared system it means
**every unmapped patron in every co-tenant library is attributed to one agency** — including
patrons of libraries that are not in the consortium at all (scenario 2), who then pass
`ResolvePatronPreflightCheck.checkAgency` because the agency they were mis-attributed to
*is* participating.

The same hazard applies to the `Location:*` wildcard. `ReferenceValueMappingService:56` even
documents it — *"this will not be appropriate in all scenarios (Shared servers)"* — but
nothing enforces it.

**Fix.** Add a `shared-system: true` boolean to the Host LMS config and make it structurally
incompatible with the ambiguous fallbacks:

```java
// HostLmsClient
default boolean isSharedSystem() {
    return Boolean.parseBoolean(String.valueOf(getConfig().getOrDefault("shared-system", "false")));
}

default String getDefaultAgencyCode() {
    if (isSharedSystem()) {
        return null; // a shared system has no meaningful default agency
    }
    return (String) getConfig().get("default-agency-code");
}
```

and reject the wildcard for shared hosts in `LocationToAgencyMappingService:110`:

```java
return getContextHierarchyFor(fromContext)
    .zipWith(isSharedSystem(fromContext))
    .flatMap(function((sourceContexts, shared) -> {
        // Wildcards collapse every co-tenant onto one agency - never safe on a shared system
        final var lookupCodeList = shared ? List.of(locationCode) : List.of(locationCode, "*");
        return referenceValueMappingService.findMappingUsingHierarchyWithFallback(
            fromCategory, sourceContexts, lookupCodeList, "AGENCY", "DCB");
    }));
```

Then add the corresponding validation to `graphql/validation/HostLmsConfigValidator.java` so
the UI refuses `shared-system: true` together with `default-agency-code`.

### H6 — `LocationService.memoize` is dead code, and it is exactly the tool operators need.

Its javadoc (`core/svc/LocationService.java:69-75`) states the purpose:

> *"in shared systems it is useful to know which agency a location should be associated with.
> In order to do that it is useful to remember Locations we have seen so that they may be
> amended later on."*

That is the onboarding tool for 60 Koha branches. It does not work. The guard at `:80-84`
returns `Mono.empty()` unless `location.getAgency()` is non-null — and **no adapter ever
sets an agency on the item's `Location`**. Verified across all five item mappers:
`SierraItemMapper:116`, `ConsortialFolioItemMapper:70`, `PolarisItemMapper:217`,
`KohaHostLmsClient:527`, `DummyLmsClient:152` — code and name only. `enrichItemAgencyFromLocation`
sets `Item.agency`, never `Location.agency`.

So the guard rejects precisely the unmapped locations it was written to capture, and
`memoizeLocationFromItem` (`LiveAvailabilityService:257`) is a per-item no-op that still
costs a subscription.

**Fix.** Memoize from the item, not the location, and record locations *whether or not*
they resolved — an unresolved location is the interesting one. The `needsAttention` /
`ReviewDynamicLocation` workflow at `:94-106` already exists to surface them:

```java
// LiveAvailabilityService
private Mono<Item> memoizeLocationFromItem(Item item) {
    if (item.getLocation() == null) return Mono.just(item);
    return locationService.memoize(item.getLocation(), item.getAgency(), item.getSourceHostLmsCode())
        .thenReturn(item);
}

// LocationService
@Transactional
public Mono<Location> memoize(Location location, DataAgency agency, String hostLmsCode) {
    if (location == null || location.getCode() == null || hostLmsCode == null)
        return Mono.empty();

    location.setAgency(agency); // nullable - null is the signal that a human must map it

    if (location.getId() == null) {
        // Key on the Host LMS, not the agency: an unmapped location has no agency,
        // and location codes are only unique within a system.
        location.setId(UUIDUtils.generateLocationId(hostLmsCode, location.getCode()));
    }

    return Mono.from(locationRepository.findById(location.getId()))
        .switchIfEmpty(dynamicCreateLocation(location));
}
```

Note this changes the generated location ID keying. That needs a migration for existing
memoized rows — treat as a follow-on, not part of the fix.

Requires `Item.sourceHostLmsCode` to actually be populated; today only some adapters set it,
and `Item.getHostLmsCode()` derives from `agency.hostLms`, which is null exactly when the
mapping failed. Set `sourceHostLmsCode` in every adapter's item mapper.

### H7 — Unbounded per-item fan-out at the supplier, amplified by co-tenancy.

`KohaHostLmsClient.getItems:423-429`:

```java
return client.getItemsForBiblio(bibId)
    .flatMapMany(Flux::fromArray)
    .flatMap(this::mapKohaItemToDcbItem)      // each issues getActiveHoldsForItem
```

Unbounded `flatMap`. `mapKohaItemToDcbItem:509` calls `client.getActiveHoldsForItem` per
item. A title held at 60 branches on one Koha = 60+ concurrent API calls to a single server,
per availability check, per concurrent user. This is the "Do Not DDOS the Libraries" rule,
and shared systems are where it bites hardest — 60 libraries' worth of holdings arrive on
one connection pool instead of 60.

**Fix.** Bound the concurrency, and prefer a batch endpoint if Koha exposes one:

```java
private static final int ITEM_ENRICHMENT_CONCURRENCY = 4;

return client.getItemsForBiblio(bibId)
    .flatMapMany(Flux::fromArray)
    .flatMap(this::mapKohaItemToDcbItem, ITEM_ENRICHMENT_CONCURRENCY)
    .flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, getHostLmsCode()),
        ITEM_ENRICHMENT_CONCURRENCY)
    ...
```

Better still, make the limit a Host LMS config value so a shared server can be throttled
independently of a dedicated one.

### H8 — Item filter composition is order-dependent and can error mid-stream.

`AllItemFilters` (`request/resolution/AllItemFilters.java:29`) chains `List<ItemFilter>` in
whatever order Micronaut supplies. No `@Order` on any implementation.
`PatronRequestResolutionService:333-334` explicitly acknowledges the hazard —
*"validating within each filter introduces non-deterministic behaviour based upon order the
filters are applied"* — and hoists parameter validation out. But `SameServerItemFilter`
still raises `Problem` from inside a filter, so whether resolution errors or merely drops an
item depends on bean ordering.

**Fix.** `ItemFilter` implementations must be total predicates. Once H1 lands the only
remaining `raiseError` in a filter is the null-code guard, which should become a `false`
with a decision-log entry rather than an error. Add `@Order` to make the chain deterministic
and put cheap synchronous filters first.

### H9 — `Location.code` is declared globally unique.

`core/model/Location.java:65` — `@Column(unique = true)` on `code`. The Flyway schema
(`V1__Initial_schema.sql`) has no such constraint, so nothing enforces it at runtime, but
the annotation is a lie and `LocationRepository.findOneByCode` (used by
`LocationService.findByIdOrCode`) genuinely is ambiguous when two systems — or 60 Koha
branches — share a location code like `MAIN` or `STACKS`.

**Fix.** Drop the `unique = true`, and where code lookup is needed, scope it by host system.
Do not add the constraint to the DB.

---

## 3. Simplifications

Independent of the bugs, three things should collapse:

1. **One system-identity primitive.** `getClientId()` (H2) replaces
   `HostLmsService.getHostLmsBaseUrl`, `getHostLmsQualifiedBaseUrl`, `qualifiedBaseUrl`,
   `BASE_URL`, `BASE_URL_QUALIFIER` and the `SameServerItemFilter` base-URL comparison.
   Roughly 40 lines deleted and one class of divergence removed.

2. **One patron→agency resolver.** H4 deletes `findAgencyForLocation` /
   `findOneAgencyByCode` from `ValidatePatronTransition` and two constructor dependencies.
   `LocalPatronService` and `ValidatePatronTransition` then answer identically by
   construction.

3. **One shared-system flag.** `shared-system: true` (H5) is a single configuration fact
   from which the safe defaults follow: no `default-agency-code`, no `Location:*` wildcard,
   mandatory branch-level location mappings, throttled fan-out. Today an implementer has to
   know all four rules independently and nothing checks them.

---

## 4. Scenario 1 — one Koha, 60+ libraries

Modelled as one `DataHostLms` with 60+ `DataAgency` rows. **This does not work today.**
Blockers, in order:

### 1a. The Koha patron mapper drops the home library. Fatal.

`KohaHostLmsClient.mapKohaPatronToDcbPatron:269-277` never sets `localHomeLibraryCode`.
`KohaPatron.library_id` is modelled (`koha/dto/KohaPatron.java:35`) and is used when
*creating* a patron — it is simply not read back.

Consequence: every Koha patron has a null home library code, so both resolution paths skip
straight to `findDefaultAgencyCode`, and **all 60 libraries' patrons resolve to one agency.**
Every request is attributed to the wrong library. Combined with H5, they all pass the
borrowing participation check too.

```java
// KohaHostLmsClient
private Patron mapKohaPatronToDcbPatron(KohaPatron kohaPatron) {
    return Patron.builder()
        .localId(List.of(String.valueOf(kohaPatron.getPatronId())))
        .localNames(List.of(kohaPatron.getFirstname(), kohaPatron.getSurname()))
        .localBarcodes(kohaPatron.getCardnumber() != null ? List.of(kohaPatron.getCardnumber()) : List.of())
        .localPatronType(kohaPatron.getCategoryId())
        // The Koha branch is the only thing that distinguishes co-tenant libraries
        .localHomeLibraryCode(kohaPatron.getLibraryId())
        .isActive(true)
        .build();
}
```

### 1b. The Koha item mapper uses the shelving location as the branch. Fatal.

`KohaHostLmsClient.mapKohaItemToDcbItem:526-531` builds `Item.location` from
`kohaItem.getLocation()` — Koha's `location`, which is the **shelving location** (`STACKS`,
`REF`, `JUV`). `Item.java:93-100` is explicit that these are different concepts and that
`Location` must be the branch. `KohaItem.homeLibraryId` / `holdingLibraryId`
(`koha/dto/KohaItem.java:42,111`) are modelled and unused.

Consequence: `enrichItemAgencyFromLocation` tries to map `STACKS` → agency. `STACKS` exists
at all 60 branches, so either the mapping is impossible or every branch's items land on one
agency.

```java
// KohaHostLmsClient - inside mapKohaItemToDcbItem
// home_library_id is the owning branch. Koha's "location" is a shelving classifier
// shared across every branch and cannot identify an agency on a shared server.
final var owningBranch = kohaItem.getHomeLibraryId() != null
    ? kohaItem.getHomeLibraryId()
    : kohaItem.getHoldingLibraryId();

Location derivedLocation = owningBranch != null
    ? Location.builder().code(owningBranch).name(owningBranch).build()
    : null;

return Item.builder()
    ...
    .location(derivedLocation)
    .shelvingLocation(kohaItem.getLocation())
    .sourceHostLmsCode(getHostLms().getCode())
    .owningContext(getHostLms().getCode())
    .build();
```

### 1c. Virtual record placement is single-library by construction.

`virtual-item-library-code`, `virtual-item-location-code`, `sharing-library-code`
(`KohaClientConfig.java:20-24`) and `default-agency-code` (used as `library_id` in
`createPatron:131`) are all **scalars on the Host LMS**. With 60 tenants, every virtual bib,
item and patron lands at one branch regardless of who is borrowing. `createItem:443` hard-uses
`config.getVirtualItemLibraryCode()` for both `homeLibraryId` and `holdingLibraryId`.

Note the code comments already know: `// then we have the joy of the shared libraries ....`
(`:129`), `// 1 system` (`:115`).

**Fix.** These must become agency-scoped. `CreateItemCommand` already carries
`patronHomeLocation`; `PlaceHoldRequestParameters` carries `pickupAgency` and
`requestingAgencyCode`. Resolve the branch per-request with the Host LMS value as fallback:

```java
// KohaHostLmsClient
private String virtualItemLibraryFor(CreateItemCommand cic) {
    // On a shared server the virtual item must be created at the borrowing branch,
    // otherwise 60 libraries' virtual stock accumulates at one location.
    return firstText(cic.getPatronHomeLocation(), config.getVirtualItemLibraryCode());
}
```

with the same treatment for the virtual patron's `library_id`, driven off the resolved
borrowing agency rather than `default-agency-code`.

### 1d. Intra-Koha lending routes to RET-LOCAL. Verify this is intended.

Borrower agency A and supplier agency B, both on the one Koha Host LMS:
`SameServerItemFilter` keeps the item (same Host LMS code, not same-server-different-code),
and `setPatronRequestWorkflow` sets `LOCAL_WORKFLOW` because lender and pickup clients are
identical. `placeSingularRequest` then places a real hold on the real item with the real
patron — no virtual bib, no virtual item, no barcode collision, and Koha's own branch
transfer machinery does the delivery.

That is almost certainly the right behaviour and it is the reason the shared-Koha case is
tractable at all. It also means **DCB's loan policy and canonical item type mapping do not
constrain intra-Koha lending** — Koha's circulation rules do. That is a policy decision the
consortium needs to make explicitly, not discover in production.

It also means most of 1c only matters for Koha↔non-Koha traffic. 1a and 1b matter for
everything.

### 1e. Operational scale.

- **Alarms.** `LocationToAgencyMappingService:79` raises one alarm per
  `ILS.<host>.LOCATION_TO_AGENCY_FAILURE.Location.<CODE>`, and `AlarmsService.raise` POSTs to
  every configured Slack/Teams webhook on first sighting. 60 branches × N unmapped shelving
  locations on first ingest = a webhook flood. Bounded (DB-keyed, 5-day expiry) but hostile.
  Suggest batching notification for `LOCATION_TO_AGENCY_FAILURE` — one digest alarm per
  Host LMS listing the unmapped codes, rather than one per code.
- **Fan-out.** H7. Unavoidable without a concurrency limit.
- **Onboarding.** H6. Without working location memoization, mapping 60 branches means
  someone enumerating Koha's `libraries` endpoint by hand. Fix H6 and the
  `ReviewDynamicLocation` workflow does the discovery.

---

## 5. Scenario 2 — shared Sierra, one participant and one non-participant

Sierra is the best-supported case. The mechanisms are correct; the risk is entirely that a
misconfiguration silently *includes* the non-participant.

### Supply side — works, if configured correctly.

Two independent gates:

1. Any item whose location does not map to an agency is dropped —
   `LiveAvailabilityService:224` `.filter(Item::hasAgency)`.
2. Any item whose agency has `isSupplyingAgency != true` is dropped — `:226`
   `.filter(Item::AgencyIsSupplying)`.

So a non-participating library is excluded either by having no agency at all, or by an
agency with `isSupplyingAgency = false`. **Model it as an explicit agency with both flags
false.** That gives the operator something visible in the admin UI, keeps the alarm at
`LocationToAgencyMappingService:79` quiet, and makes exclusion an assertion rather than an
accident of missing config.

### Borrow side — enforced in one place only.

`ResolvePatronPreflightCheck.checkAgency:160` correctly rejects a patron whose agency has
`isBorrowingAgency != true`. But:

- The whole check is behind `dcb.requests.preflight-checks.resolve-patron.enabled`
  (`:35`) and can be switched off.
- `ValidatePatronTransition` — the state machine's own patron validation — does **not**
  re-assert it. Nothing between preflight and hold placement checks participation again.

**Fix.** Re-assert in the transition. Defence in depth on a check whose failure mode is
"we placed an ILL request for a library that isn't in the consortium":

```java
// ValidatePatronTransition, after the agency is resolved
.flatMap(agency -> {
    if (!Boolean.TRUE.equals(agency.getIsBorrowingAgency())) {
        return Mono.error(new AgencyNotParticipatingInBorrowingException(agency.getCode()));
    }
    return Mono.just(pi.setResolvedAgency(agency));
})
```

### The actual danger: the fallbacks (H5).

On a shared Sierra, either fallback silently converts a non-participant into a participant:

- `default-agency-code` set on the Host LMS → the non-participating library's patrons, who
  have no location mapping *by design*, resolve to the participating agency and pass the
  borrowing check.
- `Location:*` → `AGENCY:participating-agency` → the non-participating library's **entire
  catalogue** becomes suppliable.

Neither produces an error, a warning, or an alarm. Both are the documented "simplify config
for the most common case" shortcuts. **This is the single highest-risk finding for
scenario 2** and it is a configuration hazard, not a code bug — which is why H5 proposes
making it structurally impossible rather than documenting it.

### Two Host LMS records on one Sierra.

The other shape of scenario 2: both libraries in OpenRS, each with its own Host LMS record
(different Sierra logins/scopes) against one physical server. Here `SameServerItemFilter`
does its job — Sierra's `getClientId()` is the root URI, both records return the same value,
and cross-library items are excluded from resolution because DCB cannot meaningfully create
a virtual bib/item for an item that already lives in the same database.

Two caveats:

- The exclusion is silent. Add a decision-log entry so the reason surfaces in the
  resolution audit — `Item` already carries `decisionLogEntries` for exactly this.
- Ingest is not deduplicated. Both Host LMS records ingest the same Sierra bibs, producing
  duplicate source records and clustering both copies. Worth checking against
  `SourceRecordRepository` keying (`findByHostLmsIdAndRemoteIdLike` is host-scoped, so
  duplicates are expected by design) before recommending a change.

---

## 6. Priority

| # | Finding | Severity | Scenario |
|---|---|---|---|
| H1 | `SameServerItemFilter` NPEs on Koha/Alma; zero tests | **Blocker** | 1 |
| 1a | Koha patron mapper drops `library_id` | **Blocker** | 1 |
| 1b | Koha item mapper uses shelving location as branch | **Blocker** | 1 |
| H2 | `Alma.getClientId()` returns `""` — all Alma tenants compare equal | **Critical** | both |
| H5 | `default-agency-code` / `Location:*` silently include non-participants | **Critical** | 2 |
| H3 | RET-LOCAL routing ignores the patron's system | High | both |
| H4 | Two divergent patron→agency resolvers | High | both |
| 1c | Koha virtual record config is single-library | High | 1 |
| H6 | `LocationService.memoize` is dead code | Medium | both |
| H7 | Unbounded per-item fan-out | Medium | 1 |
| 2b | `isBorrowingAgency` enforced only in preflight | Medium | 2 |
| H8 | Filter chain order-dependent, errors mid-stream | Medium | both |
| H9 | `Location.code` declared globally unique | Low | both |

---

## 7. What has been implemented

Branch `feat/shared-system-support`, cut from `main`. Four changes: the system-identity
primitive, one patron→agency resolver, one shared-system flag, and the filter that depends
on the first of those.

### 7.1 One system-identity primitive (H2)

`HostLmsClient.getClientId()` now carries a written contract — *two clients addressing the
same system MUST return equal values, two addressing different systems MUST NOT* — together
with why it matters, because nothing at the adapter makes the consequences visible.

| Adapter | Before | After |
|---|---|---|
| Alma | `""` | `alma-url` resolved to `/` |
| Koha | `hostLms.getCode()` | `api-url` resolved to `/` |
| Sierra | root URI | root URI, qualified |
| FOLIO | root URI resolved | unchanged, qualified |
| Polaris | base URL resolved | unchanged, qualified |
| ORS appliance | (inherited Host LMS code) | NCIP endpoint qualified by `ncip-system-id` |
| `AbstractHostLmsClient` | Host LMS code | unchanged, qualified, documented as a fallback that cannot detect sharing |
| Dummy | Host LMS code | unchanged; `base-url-qualifier` now overrides it outright so a test can model two records on one notional server |

The `base-url-qualifier` mechanism moved from `HostLmsService` into
`HostLmsClient.qualifySystemIdentity`, where adapters that front several logical systems on
one transport URL can apply it themselves. `HostLmsService.getHostLmsBaseUrl`,
`getHostLmsQualifiedBaseUrl`, `qualifiedBaseUrl` and the `BASE_URL` / `BASE_URL_QUALIFIER`
constants are gone — `getHostLmsBaseUrl` had no callers at all.

ORS is the case that justifies keeping a qualifier: one appliance fronts several libraries
on one endpoint, and those libraries can genuinely lend to each other, so they must not
collapse into one identity. It defaults to the NCIP SystemId and still honours an explicit
`base-url-qualifier` (which `DcbProfileRegistrationService` sets to the peer's self slug).
Its `getClientId()` catches configuration failures and degrades to the Host LMS code rather
than throwing — this method feeds equality comparisons in workflow routing, and an exception
there would abort a patron request over a config gap.

### 7.2 One patron→agency resolver (H4)

`LocationToAgencyMappingService.resolveAgencyForPatronHomeLocation(hostLmsCode,
homeLibraryCode)` is now the single answer to "which library is this patron from".

- `LocalPatronService.findAgencyForPatron` delegates to it — `findHomeLocationMapping` and
  its local `findDefaultAgencyCode` are gone, and `AgencyService` has dropped out of that
  class's constructor.
- `ValidatePatronTransition.resolveHomeLibraryCodeFromSystemToAgencyCode` delegates to it.
  Its private `findAgencyForLocation` and `findOneAgencyByCode` are deleted, along with
  `ReferenceValueMappingService` and `AgencyRepository` from its constructor.

The transition previously called `referenceValueMappingService.findMapping` directly, with
no context hierarchy and no wildcard. Preflight and validation now cannot disagree by
construction.

One behavioural consequence worth stating: the transition used to raise
`NoAgencyFoundException` when a resolved agency code had no matching row, and
`UnableToResolveAgencyProblem` when no code resolved at all. It now raises
`UnableToResolveAgencyProblem` in both cases, matching what `LocalPatronService` always did.
That is what the preflight checks already catch (`PATRON_NOT_ASSOCIATED_WITH_AGENCY`), so
the failure surfaces more usefully than before. `NoAgencyFoundException` now has no
remaining users.

### 7.3 One shared-system flag (H5)

`shared-system: true` on a Host LMS config. From that one fact:

- `HostLmsClient.getDefaultAgencyCode()` returns `null` on a shared system, so neither
  patron resolution path can fall back to a single agency for an unrecognised location.
- `LocationToAgencyMappingService` drops the `*` wildcard from the lookup list on a shared
  system, so `Location:* → AGENCY:x` cannot sweep every co-tenant — including libraries
  outside the consortium — onto one agency.
- `HostLmsConfigValidator` rejects `shared-system: true` combined with
  `default-agency-code` outright, and stops *requiring* `default-agency-code` when the flag
  is set. Previously all four validated adapters demanded it unconditionally, which made a
  correctly-configured shared system unrepresentable through the admin UI.

The wildcard decision and the context hierarchy are read from a single `HostLmsClient`
rather than two lookups (`LocationLookupRules`), because this sits on the per-item
availability path.

Note the enforcement split: the *runtime* guards apply however the config arrived, but the
*validator* only guards the GraphQL create/update path. Config-file import and direct DB
seeding still bypass it.

### 7.4 `SameServerItemFilter` (H1, and part of H8)

Rewritten to compare `HostLmsClient`s rather than a config key, which is what made it fail
on Koha (`api-url`) and Alma (`alma-url`) — `Mono.map` signals `NullPointerException` when
its mapper returns null, and `filterWhen` propagated that straight out, aborting resolution
for the entire cluster.

It is now a total predicate. Missing or unresolvable Host LMS codes exclude the item and log
rather than raising, so no single unattributable item can abort a resolution, and the
outcome no longer depends on where the filter happens to land in the composite's ordering. A
short-circuit was added for the common case — same Host LMS code means the ordinary
shared-system shape (one Koha, sixty libraries) and needs no client lookup at all.

### 7.5 Tests

Neither the filter nor the identity contract had any test coverage. Both now do.

`SameServerItemFilterTests` (`@DcbTest`, 6 cases): co-tenant agency on one Host LMS is
included; separate server included; second Host LMS on the same server excluded; separately
qualified systems on one URL included; item with no Host LMS excluded without raising;
unknown and null borrowing Host LMS excluded without raising.

`HostLmsClientIdentityTests` (Mockito, 11 cases): separate Alma tenants no longer compare
equal and cosmetic URL differences no longer separate them; two Host LMS records on one Koha
do compare equal while separate Koha servers do not; qualifier separates logical systems on
one URL and a blank qualifier is inert; `shared-system` withholds the default agency code,
a dedicated system still provides it, and the flag parses from a string (config arrives as
JSON from the UI and YAML from imports).

### 7.6 Verification

`./gradlew :dcb:test` — **938 tests across 173 suites, 0 failures, 0 errors, 2 skipped**
(both skipped are pre-existing in `services.k_int.test.mockserver.ProxyTest`), 6m40s.

Nothing was changed to make existing tests pass. Of note, the tests that most directly cover
the reworked paths were already green and stayed green: `LocationToAgencyMappingServiceTests`
(wildcard behaviour preserved for dedicated systems), `ValidatePatronTests` — including
`shouldUseDefaultAgencyFallbackWhenNoHomeLibrary`, which exercises the default-agency route
that §7.3 now suppresses on shared systems only — `ResolvePatronPreflightCheckTests`, and
`PatronRequestResolutionServiceTests`.

---

## 8. What remains

Nothing below is started. Ordered by what blocks a real deployment.

### Blocking for scenario 1 (60+ libraries on one Koha)

**Koha still cannot tell its own tenants apart.** Findings 1a and 1b are untouched and are
the reason scenario 1 does not work today, independently of everything fixed above:

- `KohaHostLmsClient.mapKohaPatronToDcbPatron` still never sets `localHomeLibraryCode`, so
  every Koha patron resolves through the default-agency fallback. With `shared-system: true`
  that fallback now returns null, so instead of silently attributing all 60 libraries to one
  agency, patron resolution will fail outright with `UnableToResolveAgencyProblem`. **That is
  a better failure, not a fix** — it is loud instead of wrong, but Koha borrowing does not
  work until `library_id` is read back.
- `mapKohaItemToDcbItem` still uses Koha's `location` (a shelving classifier) as the branch
  while `home_library_id` sits unused.

Both fixes are written out in §4 and are small. They were left out of this pass because the
scope was the three cross-cutting primitives plus the filter, and because they want an
adapter-level test each, which needs Koha fixtures that do not exist yet (see below).

**Koha virtual-record config is still single-library** (1c). `virtual-item-library-code`,
`sharing-library-code` and the `default-agency-code` read at `KohaHostLmsClient:134` are
scalars on the Host LMS. That last one reads `getConfig()` directly rather than going
through `getDefaultAgencyCode()`, so it bypasses the shared-system guard entirely and yields
an empty `library_id`. Either route it through the accessor or — better, per §4 — resolve
the branch per request from the borrowing agency.

**No Koha or Alma test fixtures.** `HostLmsFixture` has helpers for Sierra, Polaris, dummy
and ORS but not Koha or Alma, so `SameServerItemFilterTests` proves the same-server rule
through Sierra records and the identity tests construct Koha and Alma clients directly with
Mockito. That is adequate for the identity contract and inadequate for anything that
exercises those adapters through the container. Adding the fixtures is a prerequisite for
the Koha work above.

Related: `KohaHostLmsClient` takes `KohaClientConfig` as a constructor dependency, but that
class carries no bean annotation and its only constructor takes a `HostLms`. Whether
Micronaut can construct a Koha client through `HostLmsService.getClientFor` at all needs
checking before writing container-level Koha tests — `KohaApiClientImpl` builds its own
config with `new KohaClientConfig(hostLms)`, which suggests the injected one may never have
worked.

### Correctness, both scenarios

**H3 — RET-LOCAL routing still ignores the patron's system.**
`RequestWorkflowContextHelper.setPatronRequestWorkflow` compares only lender and pickup
clients. Patron on one system with lender and pickup on another still routes to
`LOCAL_WORKFLOW`, where `placeSingularRequest` resolves its client from the borrowing
identity and sends it the other system's bib and item IDs. The three-way comparison is
written out in §2; it was left out because it changes workflow selection and wants its own
permutation tests over `RequestWorkflowContextHelper`, which has none today.

Note this bug's blast radius *grew* with §7.1: Alma clients used to compare equal to each
other by accident, and Koha clients never compared equal at all. Both now report system
identity honestly, so this branch of the router is reachable in configurations where it
previously was not.

**H6 — `LocationService.memoize` is still dead code.** The guard requires
`location.getAgency()`, which no adapter sets, so it rejects exactly the unmapped locations
it exists to capture. This is the onboarding tool for 60 branches and the discovery
mechanism for an unfamiliar shared Sierra. The fix in §2 changes the generated location ID
keying, so it needs a migration for existing memoized rows — that is why it is not in this
pass. It also depends on `Item.sourceHostLmsCode` being populated by every adapter, which
it currently is not.

**2b — `isBorrowingAgency` is still enforced only in `ResolvePatronPreflightCheck`**, which
is switchable via `dcb.requests.preflight-checks.resolve-patron.enabled`.
`ValidatePatronTransition` does not re-assert it. Now that both stages share a resolver
(§7.2), adding the assertion is a few lines in one place.

**H8 — filter ordering is still undefined.** `SameServerItemFilter` no longer raises, so the
specific hazard is gone, but no `ItemFilter` carries `@Order` and the composite's behaviour
still depends on Micronaut's bean ordering. Add `@Order` and put cheap synchronous filters
first.

**H9 — `Location.code` is still annotated `@Column(unique = true)`** while the Flyway schema
has no such constraint. Harmless at runtime, misleading to read. Drop the annotation; do not
add the constraint.

### Consistency and hygiene

- **Sierra's identity is not resolved to `/`** the way FOLIO, Polaris, Alma and Koha are, so
  `https://sierra.example.com` and `https://sierra.example.com/` would be treated as two
  systems. The existing comment claims resolution happens; it does not. Left alone
  deliberately — it is proven behaviour in production and normalising it could merge two
  Sierra instances that differ only by path. Worth doing, worth doing with a test.
- **`ValidatePatronTransition` resolves the agency twice.** `findLocalPatron` calls
  `localPatronService.findLocalPatronAndAgency`, which resolves the agency and discards it,
  then `resolveHomeLibraryCodeFromSystemToAgencyCode` resolves it again. That predates this
  work, but now that both go through the same resolver the second call is provably redundant
  and can take the agency from the tuple.
- **`NoAgencyFoundException` has no remaining users** and can be deleted.
- **Config-file import bypasses `HostLmsConfigValidator`** (§7.3). `DCBConfigurationService`
  should apply the same shared-system rules.
- **`PolarisConfig.defaultAgencyCode` is bound but never read.** Dead field; Polaris uses
  the interface default, so it does respect the shared-system flag.

### Scenario 2 specifics still open

- The same-server exclusion is silent. `Item` carries `decisionLogEntries` precisely so a
  resolution audit can explain itself; the filter should write one.
- Ingest is not deduplicated across two Host LMS records on one Sierra. Confirm whether the
  host-scoped `SourceRecordRepository` keying makes duplicate clustering intentional before
  changing anything.
- The `LOCATION_TO_AGENCY_FAILURE` alarm still fires one webhook per distinct unmapped
  location code on first sighting. Bounded, but a 60-branch first ingest is a notification
  flood. Batch into one digest alarm per Host LMS.

### Known gap in what was built

`LocationToAgencyMappingService.lookupRulesFor` falls back to `sharedSystem = false` when
the Host LMS client cannot be loaded, which re-enables the wildcard. This matches the
pre-existing behaviour of the context-hierarchy lookup it replaced, and reaching it requires
both a transient client failure *and* a wildcard mapping that should not exist on a shared
system — but it is a safety control failing open, and the safe direction is to suppress the
wildcard whenever the system cannot be identified.

### Test coverage to add alongside

- `SameServerItemFilterTests` — currently nonexistent. Same-code/same-server,
  different-code/same-server, different-server, null-code, and one case per adapter's
  `getClientId()`.
- `RequestWorkflowContextHelperTests` — the three-party permutation in H3.
- A shared-system integration test: one Host LMS, three agencies, borrow across two of them,
  assert RET-LOCAL and assert no virtual records are created.
- Koha: patron home library round-trip (1a) and item branch mapping (1b).

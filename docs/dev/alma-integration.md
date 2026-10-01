# Alma integration

How OpenRS DCB talks to Ex Libris Alma, and the constraints that shaped it. For developers
changing or debugging the adapter.

Consortium staff bringing an Alma institution into OpenRS want
[Setting up an Alma institution](../operational/alma-setup.md). Every Host LMS setting is in the
[hosting and configuration reference](../operational/alma-hosting.md#settings). Every Alma call
the adapter makes is listed in the [Alma API reference](alma-api-reference.md).

## Where the code is

| Class | Role |
|---|---|
| `org.olf.dcb.core.interaction.alma.AlmaHostLmsClient` | The `HostLmsClient` implementation. Everything DCB's request workflow asks of an ILS (place a hold, create a virtual patron, check an item out, track a request) is answered here. No Alma-specific behaviour exists outside this package. |
| `AlmaClientConfig` | The Host LMS settings the client reads, with their defaults. `getSettings()` is what DCB Admin shows and validates. |
| `AlmaClientFactory` | Builds one `AlmaApiClientImpl` per Host LMS. |
| `services.k_int.interaction.alma.AlmaApiClient` | An interface whose `default` methods build each Alma call (path, query parameters, paging) on four primitives: `get`, `post`, `put` and `delete`. |
| `AlmaApiClientImpl` | Implements those primitives over Micronaut's `HttpClient`: the base URL, the API key header, error parsing and the per-second-threshold retry. |
| `AlmaApiException` | A typed Alma error, carrying the method, path, HTTP status and every `errorCode` Alma returned. |
| `AlmaHostLmsClientException`, `DuplicateItemBarcodeException` | The adapter's own failures, with messages that name the cause. |
| `AlmaXmlGenerator` | The MARCXML for virtual bibs and the XML for virtual holdings, which Alma accepts only as XML. |
| `AlmaOaiPmhIngestSource` | Harvests the institution's records over OAI-PMH. |
| `services.k_int.interaction.alma.types` and the `Alma*` DTOs beside the client | Alma's JSON payloads. |

Keeping path construction in `AlmaApiClient` means Alma's wide and inconsistent API surface is
absorbed in one place, and DCB's workflow engine never sees it.

## The HTTP layer

### Two hosts

REST calls go to `alma-url`, the regional API gateway. OAI-PMH goes to `base-url`, the
institution's own Alma domain. Calling the API on the institution's domain returns an HTML
"401 Unauthorized" page from Tomcat for any key, which reads like a rejected credential and is
in fact the wrong host. If an Alma call fails with unexplained HTML, check `alma-url` first.

### The API key

Every call carries `Authorization: apikey {key}` and `Accept: application/json`. Alma also
accepts the key as an `apikey` query parameter; the adapter does not use it, so the key never
appears in a URL, a log line or an access log.

The key needs **Users** and **Bibs** read/write and **Configuration** read-only. User requests
sit in the Users area and item requests in Bibs; Alma has no separate Requests area. A key
missing an area fails only on the operations that need it, with `400 UNAUTHORIZED`, so a
partly permissioned key presents as an intermittent integration rather than a broken one.
Configuration is needed at request time, not only for the reports: `createItem` reads the
virtual item location from it.

### Errors

Alma reports failures as a JSON or XML error body carrying one or more `errorCode` values,
usually with HTTP 400. `AlmaApiClientImpl` parses the body and raises an `AlmaApiException`
exposing the codes, so callers branch on the code rather than on message text. The codes the
adapter acts on are named in `AlmaApiException.Code`:

| Code | Name | Handled as |
|---|---|---|
| `401861` | `USER_NOT_FOUND` | Empty, where DCB is checking whether a patron exists; `VirtualPatronNotFound` from `findVirtualPatron` |
| `401694` | `REQUEST_NOT_FOUND` | The request has left Alma: tracked as `MISSING` |
| `401129` | `NO_ITEM_CAN_FULFIL` | A fulfilment-rules refusal, explained through request options (below) |
| `401689` | `NO_ITEM_FOR_BARCODE` | The barcode is free |
| `401866` | `AUTHENTICATION_FAILED` | A rejected patron password |
| `PER_SECOND_THRESHOLD` | | Retried up to 3 times, backing off from one second |
| `DAILY_THRESHOLD` | | Not retried |

Several Alma conditions are indistinguishable by HTTP status alone. "User not found" is the
clearest: an ordinary outcome when DCB checks whether a virtual patron already exists, and an
error anywhere else.

```java
.onErrorResume(AlmaHostLmsClient::isVirtualPatronNotFoundError, error -> Mono.empty())
```

The exception and the log lines carry the path with the user segment replaced by `{user}`, and
never the request headers or body. No credential, PIN, barcode or token is logged at any level.

A 2xx response with no body is an `IllegalStateException` unless the call expected nothing.

### Identifiers in paths

Every identifier is encoded as a single path segment (`pathSegment`). Alma identifiers can
contain characters that would alter a URL's structure if interpolated raw.

### System identity

`getClientId()` is the root of `alma-url`, qualified by `base-url-qualifier` when set. DCB
compares client ids to decide whether two agencies are on the same system, for example when it
chooses a local workflow. Every institution in an Alma region shares a gateway, so two Alma
institutions in one consortium need different qualifiers, or DCB treats them as one system.

## Bounds

Ex Libris allows an institution 50 calls a second (10 on a sandbox) across all its
integrations. Every list the adapter reads is bounded, and every fan-out is limited:

| Read | Bound |
|---|---|
| A record's items | `GET /bibs/{mms_id}/holdings/ALL/items` in pages of 100 (`ITEM_PAGE_SIZE`), `total_record_count` deciding how many pages. Mapped at most 4 at a time (`ALMA_REQUEST_CONCURRENCY`), each mapping making one requests call. |
| A patron's active holds, to find one DCB placed | At most 5 pages of 100 (`MAX_HOLD_PAGES`) |
| A patron's loans, to renew or to confirm a checkout | At most 5 pages of 100 (`MAX_LOAN_PAGES`) |
| A patron's hold count | One call: `total_record_count` spans every page, so Alma's page size cannot cap it |
| Libraries' locations, for the configuration report | At most 2 libraries at a time (`LOCATION_FETCH_CONCURRENCY`) |

Alma truncates silently to a default page size (10 for user requests), so a list read in one
call is a defect. Three were found that way: a patron's loan not found when renewing, items
missing from availability on a large record, and holds miscounted.

The 500-record caps on holds and loans report "not found" when reached, the same as a search
that walked every page and found nothing.

## Operations

Each `HostLmsClient` operation, what it does in Alma, and where the request workflow uses it.

### Live availability: `getItems`

Reads every item on the bib through `holdings/ALL`, with `expand=due_date`. For each item:

1. **Status**, from the base status and process type: see [Item status](#item-status).
2. **Location**: an Alma item's DCB location is its **owning library**, never its shelving
   location. Alma's shelving locations (`STACKS`, `REF`) are a vocabulary every library on a
   tenant shares, so they cannot say which library an item belongs to. The shelving location is
   kept separately on the item.
3. **Agency**, from the Location mapping keyed on that library code.
4. **Item type**, from the physical material type, through the item-type mapping.
5. **Hold count**, from the item's requests. In live availability a count that could not be
   read is 0, as it was before the adapter read requests at all, with `holdCount: unread` in
   `rawDataValues` and a decision log entry so it can be told from a real 0. `getItem` and
   `getItemByBarcode` leave it null, because tracking must not read a failed call as no
   requests.

An item that cannot be mapped is returned with its reason in the decision log and marked not
requestable, rather than dropped, so one bad record neither hides its bib's other items nor
vanishes from the audit.

<a id="item-status"></a>
### Item status

Alma's base status has two values, `0` and `1`: not in place, in place. The process type says
why an item is not in place. `deriveItemStatus` reads both, against Alma's `PROCESSTYPE` code
table:

| Alma | DCB |
|---|---|
| base `1`, no process type | `AVAILABLE` |
| `LOAN` | `CHECKED_OUT` |
| `REQUESTED` with base `1` | `AVAILABLE` |
| every other process type, and base `0` with none | `UNAVAILABLE` |

`TRANSIT` is unavailable because Alma uses it both for an item going home and for one going to
fill another patron's hold; `ILL` because the item is lent through Alma's own resource sharing.
A code outside the table is unavailable and named in the item's decision log. The raw values are
kept in `rawDataValues` as `baseStatus` and `processType`. Due dates come from the loan, not
the holding record.

### Placing holds

All four placements build one Alma `HOLD` user request: `POST /users/{user_id}/requests?item_pid=`.
They differ only in the pickup location:

| Method | Pickup |
|---|---|
| `placeHoldRequestAtSupplyingAgency` | `sharing-circ-desk-code` as a desk pickup (`CIRCULATION_DESK`) when set; otherwise the `sharing-library-code` library, or `alternative-sharing-library-code` when the item's owning library is the sharing library. On the expedited (walk-up) workflow, the pickup location's library. |
| `placeHoldRequestAtBorrowingAgency` | The pickup location's local ID, an Alma library code. On the pickup-anywhere workflow, the sharing library, because the item never comes to this institution. |
| `placeHoldRequestAtPickupAgency` | The pickup location's local ID |
| `placeHoldRequestAtLocalAgency` | The pickup location's local ID |

**Why the supplier's pickup is constrained.** Alma decides what a scan-in does from where it
happens: an item scanned in at its hold's pickup library goes onto that library's hold shelf
instead of into transit. DCB sees a dispatch only as a transit, so the supplier's hold must be
for pickup somewhere the item will not be scanned in.

#### Idempotency: adopting a hold DCB already placed

If DCB places a hold and loses the response, through a timeout or a restart, a retry would
place a second one. So before creating, the adapter looks through the patron's active holds for
one it placed itself.

The match is on the request's `comment`, not the item id, for two reasons:

- **The patron id is not always DCB's.** At the supplying agency the local patron is a DCB
  virtual identity, so anything found under it belongs to DCB. At the borrowing agency it is the
  patron's real Alma account. A hold the patron placed for themselves, on the same item, sits in
  the same list, and matching on item id alone would adopt it. DCB would then track, and on
  finalisation cancel, a request it never placed.
- **The item id may not be there.** Alma's documented `user_requests` list payload does not
  include `item_id`. It also omits `request_id`, which Alma certainly returns, so the
  documentation describes the input shape rather than the response, and the presence of
  `item_id` in the list is unverified either way.

`comment` is documented in the list payload, is an input field on request creation, and
round-trips. DCB writes its own marker there:

```text
Consortial loan [DCB-REQUEST:0f9c2b7e-5a41-4c8e-9d33-21ab7c0e4f55]
```

The marker is bracketed so that one request id cannot match another it is a prefix of, and it
carries the DCB patron request id, so a hold placed for a different DCB request is not adopted
either. With no DCB request id to match on, the adapter does not search: it creates. A duplicate
hold is a visible, correctable fault; adopting a stranger's request is a silent one that ends in
cancelling a patron's own hold. `additional_id` would be the natural field and is not usable:
Alma documents it as output only.

#### Explaining `401129`

"No items can fulfill the submitted request" is a fulfilment-rules answer, not an availability
one: Alma found the item and refused it. The error names neither of the two things that decide
it, the pickup library and the patron's user group, so after a refusal the adapter asks
`GET …/items/{item_pid}/request-options?user_id=` and raises an error that says which:

- Alma offers this patron a hold on the item: the pickup library is the problem (not a pickup
  point for the item's fulfilment unit, or a resource-sharing library).
- Alma offers no hold: the patron's user group has no Request term of use for the item.

A failure to diagnose never replaces the refusal.

### Virtual patrons: `createPatron`, `findVirtualPatron`

A visiting patron becomes an Alma user with:

- `record_type` `PUBLIC`, `status` `ACTIVE`, `account_type` `EXTERNAL`;
- names `DCB` / `VPATRON`, unless the consortium's `VIRTUAL_PATRON_NAMES_VISIBLE` setting is on;
- `external_id` the patron's DCB unique id, and an identifier of type `user-identifier`
  (default `INST_ID`) with the same value;
- a `BARCODE` identifier for each of the patron's barcodes, prefixed with
  `virtual-patron-barcode-prefix`, skipping one equal to the external id (Alma refuses two
  identifiers with one value);
- `user_group` from the patron-type mapping from DCB to this Host LMS.

`findVirtualPatron` reads `GET /users/{unique id}`, which Alma resolves through any unique
identifier. A found user within 30 days of expiry has its expiry moved to 120 days ahead.

`updatePatron` changes the user group by reading the user and writing it back whole, with
`override=user_group`. Alma's user `PUT` replaces every field and list, so a partial payload
silently deletes data, and it keeps an external user's existing group unless the `PUT` names the
field in `override`.

### Patron sign-in: `patronAuth`

`POST /users/{user_id}?op=auth` with the password in the `Exl-User-Pw` header: Alma's documented
authentication operation, which checks a password held by the Ex Libris Identity Service. A 204
is a match; then the user is read. `BASIC/BARCODE+PASSWORD` and `BASIC/BARCODE+PIN` both check
that password, since an Alma patron has no separate PIN; any other profile is refused, because a
verifier that cannot check a credential must fail closed. A 4xx other
than 429 is a rejected credential (empty); anything else is an error, so an outage is not
reported as a wrong password.

### Patron mapping

An Alma user becomes a DCB `Patron` as follows:

| DCB | Alma |
|---|---|
| `localId` | `primary_id` |
| `uniqueIds` | `external_id` |
| `localBarcodes` | identifiers of type `BARCODE`, falling back to `primary_id` |
| `localNames` | `first_name`, `last_name` |
| `localPatronType` | `user_group` code |
| `localHomeLibraryCode` | `campus_code` |
| `expiryDate` | `expiry_date` |
| `isBlocked` | any user block with status `ACTIVE` |
| `isActive` / `isDeleted` | `status` |

- **The barcode fallback.** An institution's `primary_id` is often an SIS or IdP identifier
  rather than a card number. It stands in as a barcode only for a user with no barcode
  identifier, so that a patron without a card is still actionable.
- **An absent status means active.** Only an explicit `INACTIVE` or `DELETED` blocks the patron.
  Treating a missing status as inactive would lock out every user of a tenant that does not
  populate it.

### Virtual records: `createBib`, `createItem`

When an Alma institution's patron borrows, DCB creates a stand-in record for the book on its way:

1. **The bib**, as MARCXML: title and author escaped, suppressed from publishing, a 500 note
   ("Temporary record created by OpenRS DCB for resource sharing"), and no 001 or 005, which Alma
   writes itself. Placeholder catalogue fields are not sent.
2. **The barcode check.** The supplier's barcode is looked up in the borrower's Alma first. The
   stand-in must carry it, because that is the label staff scan, and Alma allows a barcode on one
   item per institution. An existing item raises `DuplicateItemBarcodeException` before anything
   is created; `401689` means the barcode is free; any other failure of the lookup lets the
   create go ahead and Alma decide.
3. **The location**, read from `GET /conf/libraries/{virtual-item-library-code}/locations/{virtual-item-location-code}`. A location Alma does not have stops the request here, naming it.
4. **The holding**, as XML, in that location, with call number `DCB_VIRTUAL_COLLECTION`. That
   call number is the only way to tell a virtual item from a real one.
5. **The item**, with the barcode, the mapped material type, `item-policy` (default `BOOK`), base
   status `1`, and notes naming it as DCB's. If the item cannot be created, the holding is
   deleted at once: DCB records no holding id until the item exists, so a holding left there
   could never be cleaned up.

### Moving items: `updateItemStatus`, `checkInItem`

| DCB state | Alma action |
|---|---|
| `TRANSIT` | Scan the item in (`POST …/items/{item_pid}?op=scan`) at its own library and `default-circ-desk-code`, which sends it towards its hold's pickup library. **Skipped** when the item's hold is already for pickup at its own library: Alma would shelve it, announcing a book still on its way. It goes onto the hold shelf when staff scan in the real book. |
| `RECEIVED`, `COMPLETED` | Scan the item in at its owning library |
| anything else | `UnsupportedOperationException` |

`checkInItem` scans the item in at `virtual-item-library-code`.

**A scan or checkout Alma applied but did not answer in time is a success.** On a sandbox an item
was on the hold shelf 52 seconds into a scan whose reply never came, and the transition went to
`ERROR`. So after a failed scan the item is read again: changed base status or process type is
the evidence it worked; unchanged keeps the error. After a failed checkout, the patron's loans are
searched for the item before the error is believed, so a retry cannot fail as "already on loan".

### Checking out: `checkOutItemToPatron`

Looks the item up by barcode to find the library it is in, then creates the loan at that library
with `pickup-circ-desk` as both the circulation and return desk, linked to the hold. That desk
must serve the item's library, or Alma refuses with `401153`.

### Tracking: `getRequest`, `getItem`

A user request's `request_status` is only ever `NOT_STARTED`, `IN_PROCESS` or `ON_HOLD_SHELF`
(`rest_user_request.xsd`):

| Alma | DCB |
|---|---|
| `NOT_STARTED`, `IN_PROCESS` | `HOLD_CONFIRMED` |
| `ON_HOLD_SHELF` | `HOLD_READY` |
| not found (`401694` or 404) | `MISSING` |

A cancelled or fulfilled Alma request leaves the user's active list, so asking for it by id fails
rather than answering with a cancelled status. Reading that as `MISSING` is what lets both
cancellation transitions notice a cancellation made in Alma; before, such a request ran on to the
56-day tracking cut-off.

`getItem` reads the item and its request count. An item that is not found is empty, which cleanup
reads as already gone. DCB records no holding id for a supplier item, so the adapter takes the
bib and holding ids from the item it reads: Alma's item read accepts any holding segment and
answers with the real one, but its requests and scan calls do not.

### Renewal: `renew`, `preventRenewalOnLoan`

`renew` finds the patron's loan of the item (within `MAX_LOAN_PAGES`) and posts `op=renew`.

`preventRenewalOnLoan` is done on the item, not the loan. `rest_item_loan.xsd?tags=PUT` marks
every field but `due_date` as output, and `POST` to a loan supports only `op=renew`, so there is
no writable renewal flag on an Alma loan. No adapter denies renewal on the loan in any case:
Sierra sets the item's renewal count to 255, Polaris posts a blocking note to the item's owning
organisation, and Koha stamps a collection code the library lists in `ItemsDeniedRenewal`. Each
changes the item so the ILS's own circulation rules refuse. Alma's equivalent is the item policy,
"the item's override policy for loan rules", which DCB already writes when creating the item.

1. Reads the virtual item by bib, holding and item id.
2. Refuses unless the holding carries `DCB_VIRTUAL_COLLECTION`, so it can never be applied to one
   of the library's own items.
3. Sets `item_data.policy` to `no-renew-item-policy` (default `DCB_NO_RENEW`).
4. Reads the item back, and fails if the policy did not take.
5. If any of that fails, writes a do-not-renew note to the virtual item's `fulfillment_note`,
   which Alma shows during circulation, and still raises the original error, so the workflow
   audits the request and marks it unsupported. The note is best effort.

The read-back proves the policy was written, not that renewal is denied: that rests on a loan
rule DCB cannot see. Without it, DCB sets the policy, reports success, and the renewal goes
through. `PreventRenewalCommand` carries `localBibId` and `localHoldingId` because Alma addresses
an item by all three and offers no lookup by pid alone.

An earlier implementation put the note on the library's own item and nothing removed it, so notes
accumulated on real holdings. Writing only to the virtual item, which DCB deletes at the end, is
what makes the fallback safe.

### Re-resolution: `updateHoldRequest`

When a request moves to a different supplier, the borrower's stand-in item must carry the new
book's barcode and material type. `updateHoldRequest` reads the virtual item and writes back a
minimal body: the new barcode and material type, plus the pid, policy, library and location Alma
requires. Alma's item update is sensitive to which fields are sent and answers some combinations
with an internal server error, so change this body only after testing against a live tenant.

### Cancelling: `cancelHoldRequest`, `deleteHold`

`DELETE /users/{user_id}/requests/{request_id}` with `reason` (`request-cancellation-reason`),
`override=true` and `notify_user=false`. Alma requires a reason; `notify_user` defaults to true,
and a cancellation DCB makes is housekeeping the patron did not ask for.

### Cleaning up: `deleteItem`, `deleteBib`

`deleteItem` withdraws the item and deletes its holding. `deleteBib` reads the bib and its
holdings first and deletes only while the bib is still suppressed, still carries DCB's 500 note,
and has no holding but DCB's own. Otherwise it raises, and the record stays: a library's own
processes can merge or overlay the virtual bib after DCB creates it, and the delete ignores
Alma's warnings. Virtual patrons are not deleted.

### Other operations

| Operation | Behaviour |
|---|---|
| `countHoldsForPatron` | `total_record_count` of the patron's active holds. A failure is empty, never zero. |
| `getPatronByLocalId`, `getPatronByIdentifier`, `getPatronByUsername` | `GET /users/{id}`. `getPatronByIdentifier` answers empty for an unknown user. |
| `getItemByBarcode` | `GET /items?item_barcode=`, for walk-up and the item lookup route. |
| `supplierPreflight` | Always true. |
| `fetchConfigurationFromAPI` | Libraries and their locations, for importing. |

## Implementation tools

| Endpoint | Adapter method | What it does |
|---|---|---|
| `GET /imps/ping` | `ping` | `GET /conf/test`. Proves the gateway answers and the key can read Configuration. |
| `GET /imps/configuration/{code}` | `checkConfiguration` | Checks the configured sharing library, desk and alternative, the virtual item library and location, and both item policies against Alma, and lists the vocabularies. A sharing library that is a resource-sharing library reads `MISSING`; so does a sharing desk without a hold shelf. |
| `GET /imps/configuration/{code}/mapping-value` | `checkMappingValue` | One value against one vocabulary. |
| `GET /imps/configuration/{code}/mappings` | `fetchVocabulary`, `readSideVocabularies` | Every saved mapping in both directions. Alma declares its read side as `Location` (owning library codes) and `patronType` (user group codes); its item types are keyed by agency and stay unchecked. |
| Request options, after a `401129` | `checkRequestOptions` | `GET …/request-options?user_id=` for one patron and one copy. For diagnosis only: Alma offered a hold on a copy it then refused, because the pickup location is not an input to its answer. |

The vocabularies are Alma's code tables `PhysicalMaterialType` (item types), `UserGroups`
(patron types) and `ItemPolicy`, plus the libraries list.

## Harvesting

`AlmaOaiPmhIngestSource` harvests `{base-url}/view/oai/{institution-code}/request`, with
`metadata-prefix` and `oai-set`, splitting record identifiers on `:`. A missing
`institution-code` fails construction naming it.

- `error_code 21` from OAI means the institution is not entitled to OAI at Ex Libris: an
  entitlement problem, not a configuration one.
- An empty harvest with no error usually means the OAI set is wrong or empty.
- OAI access is controlled separately from the API key, so an IP restriction needs DCB's address
  in both.

## Shared Alma tenants

A single Alma tenant can host several participating libraries: a Network Zone, or one
institution whose campuses are separate members. `shared-system: true` supports this and
disables two things that cannot be correct there: `default-agency-code` and the `Location: *`
wildcard. Agency resolution then depends on each patron resolving to their own library through
`localHomeLibraryCode`, which the adapter fills from `campus_code`.

`virtual-item-library-code` is a single value per tenant, so virtual items are created at one
library even on a shared system, where the borrowing patron's own library would be better. Koha
already prefers the patron's home branch; Alma does not yet.

## Tests

The adapter's tests are under `dcb/src/test/java/org/olf/dcb/core/interaction/alma`.

- `AlmaHostLmsClientHttpTests` and `AlmaHostLmsClientContractTests` run the client against
  MockServer with Alma-shaped responses: the HTTP contract, error parsing and paging.
- The `AlmaHostLmsClient*Tests` classes cover one operation each against a mocked
  `AlmaApiClient`.
- `AlmaItemStatusTests`, `AlmaItemMappingTests`, `AlmaRetrySafeCirculationTests`,
  `AlmaSupplierPickupLibraryTests`, `AlmaVirtualBibDeletionTests` and
  `AlmaSharingLibraryCheckTests` pin the behaviours described above.

Against a real sandbox: call the regional gateway, not the institution's domain, and check that
your address is in the key's allowed range. Sandboxes allow 10 calls a second.

## Known gaps

- **An item's loan policy is not read.** Requestability comes from the material type mapping
  alone, so a copy whose policy keeps it in the building reads as requestable.
- **`supplierPreflight` always answers true.**
- **One virtual item library per tenant**, even on a shared system.
- **The 500-record caps** on a patron's holds and loans read as "not found" when reached.
- **A failure after the virtual bib is created and before the hold is placed** can leave records
  in Alma that cleanup cannot find, because the ids are recorded only on success.
- **Re-resolution rewrites the virtual item's barcode;** if that write fails, the label on the
  book and the record disagree.
- **The ping proves Configuration access only,** not Users or Bibs.

## Changes since the 8.47.0 description

An internal description of the adapter as of DCB 8.47.0 is still in circulation. Where it and
this page differ, this page describes the current code.

| 8.47.0 described | Now |
|---|---|
| Patron sign-in by `GET /users/{id}?password=` | That call returns the user without checking the password. Sign-in is `POST /users/{id}?op=auth` with the password in the `Exl-User-Pw` header. |
| Settings `dcbSharingLibraryCode`, `pickupCircDesk`, `userIdentifier`, `itemPolicy` | Kebab-case keys: `sharing-library-code`, `pickup-circ-desk`, `user-identifier`, `item-policy`. |
| `defaultPatronLocationCode`, with a fallback when a location is closed or ambiguous | Removed. Holds go to the pickup location's library, and supplier holds to the sharing library or desk. |
| Sharing library defaults to `RES_SHARE` | No default, and `RES_SHARE` cannot work: Alma cannot deliver a patron hold to the Resource Sharing Library (`401129`). |
| Items read holding by holding, unpaged | `holdings/ALL/items`, paged by 100. |
| Hold counts always 0 | Read from each item's requests. Still 0 in live availability when they cannot be read, but marked `holdCount: unread`; null in tracking. |
| Update hold request not implemented | Implemented: rewrites the virtual item's barcode and material type. |
| Update item status a placeholder returning "OK" | Implemented: scan-ins for `TRANSIT`, `RECEIVED` and `COMPLETED`. |
| Prevent renewal not implemented | Implemented on the item policy. |
| Cancel by user request delete | The same call, now with a reason, `override=true` and `notify_user=false`. |
| A sandbox scan-in example with `?apikey=` in the URL | The adapter sends the key as a header. Either works on the gateway; neither works on the institution's domain. |
| Hold placement at the supplier for pickup at the sharing library | Also a sharing desk, or an alternative library for the sharing library's own items. |

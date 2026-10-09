# Alma hosting and configuration reference

For hosting providers and for whoever administers DCB for a consortium: every setting an Alma
Host LMS takes, what DCB needs from the network, what load it puts on an Alma institution, and
how to read the errors.

The Alma library's own steps are in [Setting up an Alma institution](alma-setup.md). How the
adapter works inside is in [Alma integration](../dev/alma-integration.md).

## Two addresses, for two purposes

An Alma Host LMS points at two different hosts, and confusing them is the most common setup
fault.

| Setting | Host | Used for |
|---|---|---|
| `alma-url` | The region's API gateway, such as `https://api-na.hosted.exlibrisgroup.com` (others: `api-eu`, `api-ap`, `api-aps`, `api-ca`, and `api-cn.hosted.exlibrisgroup.com.cn`) | Every REST API call: patrons, holds, items, loans, configuration |
| `base-url` | The institution's own Alma domain, such as `https://yourinstitution.alma.exlibrisgroup.com` | OAI-PMH harvesting only, at `{base-url}/view/oai/{institution-code}/request` |

If `alma-url` is set to the institution's domain, every API call is refused with an HTML
"HTTP Status 401 – Unauthorized" page, whether or not the key is valid. Measured against a
sandbox institution, the same key that was refused there was accepted by the regional gateway.

DCB sends the API key as an `Authorization: apikey …` header. Alma also accepts it as an
`apikey` query parameter; DCB does not use that, so the key never appears in a URL or an access
log.

<a id="settings"></a>
## Host LMS settings

The Host LMS class is `org.olf.dcb.core.interaction.alma.AlmaHostLmsClient`, with ingest
source `org.olf.dcb.core.interaction.alma.AlmaOaiPmhIngestSource`.

DCB Admin validates an Alma Host LMS when it is created, and again whenever its settings or
class change. A change that leaves out a required setting is refused, naming what is missing.
Renaming a Host LMS, or giving it a ruleset, does not re-validate its settings.

The REST route `POST /hostlmss` does not validate at all: a Host LMS created or updated through
it is saved with whatever settings it was given, and a missing one surfaces only when DCB first
needs it, as "Missing required configuration property". Create Alma Host LMSs through DCB Admin,
or check one made over REST with the configuration report in [Checking a configuration](#checking-a-configuration).

<a id="upgrading"></a>
### Upgrading an existing Alma Host LMS

Earlier releases required `alma-url`, `apikey`, `base-url`, `institution-code` and, unless the
system is shared, `default-agency-code`. This release requires four more, and refuses an
`alternative-sharing-library-code` that is the same as `sharing-library-code`:

| Setting | Before this release |
|---|---|
| `sharing-library-code` | Not checked when saved, but lending failed without it |
| `virtual-item-library-code` | Not checked when saved, but borrowing failed without it |
| `virtual-item-location-code` | Not checked when saved, but borrowing failed without it |
| `request-cancellation-reason` | Did not exist |

A Host LMS that lacks them keeps working after the upgrade: one without
`request-cancellation-reason` cancels requests without a reason, as it did before. But the next
change to its settings in DCB Admin, including rotating its key, is refused until all four are
present. Add them as part of the upgrade, not when a key next needs changing.

`shelf-location` is no longer read. A Host LMS that still has it is unaffected.

### Connection and harvesting

| Setting | Required | Default | What it is |
|---|---|---|---|
| `alma-url` | Yes | — | The regional API gateway: see above. |
| `apikey` | Yes | — | The institution's API key. Users and Bibs read/write, Configuration read-only. |
| `base-url` | Yes | — | The institution's Alma domain, for OAI-PMH. |
| `institution-code` | Yes | — | The OAI institution code in the harvest path. Not always the same string as the Alma institution code. |
| `metadata-prefix` | For harvesting | — | The OAI metadata format, normally `marc21`. Without it the harvest cannot start. |
| `oai-set` | No | none | The OAI set the institution publishes for OpenRS. |
| `ingest` | No | `true` | `false` stops harvesting without removing the Host LMS. |
| `base-url-qualifier` | No | none | Distinguishes two Alma institutions on the same regional gateway. DCB identifies a system by its `alma-url`, so without a qualifier two such institutions are one system to DCB and neither can supply the other's patrons. Use the institution code. |

### Agencies

| Setting | Required | Default | What it is |
|---|---|---|---|
| `default-agency-code` | Yes, unless shared | — | The agency for items whose library has no Location mapping, and for patrons. Not allowed on a shared system. |
| `shared-system` | No | `false` | Several member libraries in one Alma tenant. Each library's items and patrons then resolve through Location mappings and `campus_code`, with no fallback agency: see [Consortia sharing one Alma tenant](alma-setup.md#shared-tenant). |

### Lending

| Setting | Required | Default | What it is |
|---|---|---|---|
| `sharing-library-code` | Yes | — | The library every lending hold is placed for pickup at. Must hold none of the items lent, and must not be the Resource Sharing Library: see [A library to lend from](alma-setup.md#lending-library). |
| `sharing-circ-desk-code` | No | none | A hold-shelf desk in the sharing library. When set, lending holds go to the desk rather than the library. |
| `alternative-sharing-library-code` | No | none | Where holds go for items the sharing library owns itself, when no desk is set. Must differ from `sharing-library-code`. |
| `request-cancellation-reason` | Yes | — | A code from the RequestCancellationReasons code table, sent with every cancellation DCB makes. |
| `user-identifier` | No | `INST_ID` | The identifier type DCB tags visiting patrons' accounts with. |
| `virtual-patron-barcode-prefix` | No | empty | Prefix on visiting patrons' barcode identifiers, where the institution's barcodes collide with another member's. |
| `pickup-circ-desk` | No | `DEFAULT_CIRC_DESK` | The desk DCB checks items out at, to a visiting patron or to a local patron collecting elsewhere. |

### Borrowing

| Setting | Required | Default | What it is |
|---|---|---|---|
| `virtual-item-library-code` | Yes | — | The library stand-in items are created in. |
| `virtual-item-location-code` | Yes | — | The location in that library. Must exist: DCB reads it from Alma each time it creates a stand-in item. |
| `item-policy` | No | `BOOK` | The item policy stand-in items are created with. |
| `no-renew-item-policy` | No | `DCB_NO_RENEW` | The item policy DCB sets to deny renewal. Needs a loan rule in Alma that refuses renewal for it: see [Preventing renewal](alma-setup.md#preventing-renewal). |
| `default-circ-desk-code` | No | `DEFAULT_CIRC_DESK` | The desk DCB scans items in at. |

The generic Host LMS settings, such as `roles` and `contextHierarchy`, work as they do for every
system.

## Network

DCB makes outbound HTTPS calls only. Nothing in Alma calls DCB.

- **To the regional API gateway**, for every request-time operation. If the institution
  restricts its API key by IP range, the range must include DCB's outbound address.
- **To the institution's Alma domain**, for OAI-PMH. Alma controls OAI access separately from
  the API key, so an IP restriction there needs DCB's address too.

A hosting provider should publish DCB's outbound address to its Alma institutions, and keep it
stable: a change of egress address stops every institution that restricts by IP at once.

## Keys

- **One key per environment.** A sandbox key against a production Host LMS, or the reverse, is
  the easiest way to write test data into a live catalogue. The ping names the environment
  (`sandbox` or `production`) and institution the key reaches.
- **Rotation.** Change `apikey` on the Host LMS in DCB Admin; the change takes effect on the next
  call. Because that is a settings change, it is validated, so every required setting must
  already be present: see [Upgrading an existing Alma Host LMS](#upgrading).
- **Logging.** DCB never logs the key. Alma errors recorded in a request's audit trail carry the
  method, the path and Alma's error, not the request headers.

## Load on an Alma institution

Ex Libris limits each institution to 50 API calls a second (10 on a sandbox) across every
integration it runs, plus a daily limit. DCB's share of that is bounded:

| Operation | Cost |
|---|---|
| Live availability for one record | One call per 100 items on the record, plus one call per item to count its requests. At most 4 items are processed at a time. |
| Placing a hold | One call, after up to 5 reads of the patron's active holds (100 each) to find one DCB already placed. Two more if Alma refuses it with `401129`, to ask why. |
| Tracking a request | Per tracking cycle, one read of the request; for an item, one read of the item and one of its requests. |
| Configuration report | The libraries list, then each library's locations, at most 2 at a time; plus code tables. |
| Harvest | OAI-PMH, paged, on DCB's ingest schedule. |

When Alma refuses a call for exceeding the per-second limit, DCB retries it up to 3 times,
backing off from one second. Alma refuses such a call without processing it, so the retry cannot
repeat a write. A daily-limit refusal is not retried.

## What DCB leaves in the institution's Alma

| Record | Created | Removed |
|---|---|---|
| Virtual patron (a user for each visiting patron) | When the institution first lends to that patron | Not removed. Reused for later loans; its expiry is extended to 120 days ahead whenever it is found within 30 days of expiring. Named `DCB VPATRON` unless the consortium shows real names. |
| Hold on the institution's own item | When it lends | Fulfilled by the checkout, or cancelled by DCB with the configured reason, without notifying the patron. |
| Virtual bib, holding and item | When the institution's patron borrows | Deleted when the request finishes. The bib only while it is still suppressed, still carries DCB's 500 note, and has no holdings but DCB's own; otherwise it is left, and the request's audit trail says why. |
| Item policy change on a virtual item | When renewal is denied | With the virtual item. |

## Checking a configuration

All three need an administrator or interop-tester login.

| Endpoint | What it proves |
|---|---|
| `GET /imps/ping?code={host LMS code}` | Alma's own test calls for each area DCB uses: Configuration read, Users and Bibs read and write. A failure names the area. When all pass, the version reads `ALMA API v1 ({environment}, {institution})` from Alma's general configuration, which tells a sandbox key from a production one. DCB also pings every Host LMS when it is saved and once a day, raising a ping-failure alarm on `ERROR`. |
| `GET /imps/configuration/{host LMS code}` | The sharing library, desk and alternative, the virtual item library and location, and both item policies exist in Alma. Lists the institution's material types, user groups, item policies, libraries and shelving locations. |
| `GET /imps/configuration/{host LMS code}/mappings` | Every saved mapping against Alma's codes, in both directions: see [Checking the configuration](alma-setup.md#checking). |

None of them can see fulfilment unit rules: whether a user group may request, or which
libraries a fulfilment unit accepts as pickup locations. Only Alma staff can check those.

## Troubleshooting

### Alma error codes

| Code or response | Means | Look at |
|---|---|---|
| HTML "HTTP Status 401 – Unauthorized" | The call went to the institution's Alma domain, not the API gateway | `alma-url` |
| `400` with `UNAUTHORIZED`, "API-key not defined or not configured to allow this API" | The key lacks the API area this call needs, or is a key for another environment | The key's permissions: Users and Bibs read/write, Configuration read |
| `403` with `INVALID_REQUEST` or `FORBIDDEN` | An invalid key, or a call from an address outside the key's allowed range | The key; DCB's outbound address |
| `429`, `PER_SECOND_THRESHOLD` | Over 50 calls a second (10 on a sandbox). DCB retries | Other integrations sharing the institution's limit |
| `429`, `DAILY_THRESHOLD` | The institution's daily allowance is spent. Not retried | Other integrations; the size of the harvest |
| `401129` "No items can fulfill the submitted request" | A fulfilment rule refused the hold, not an availability problem. DCB asks Alma's request options which it was and records the answer: the pickup library is not a pickup point for the item's fulfilment unit, or the patron's user group has no Request term of use | The sharing library (never the Resource Sharing Library); the Request rules; the pickup location's local ID |
| `401153` | The checkout desk cannot loan items from that library | `pickup-circ-desk` must serve the library the item is in |
| `401851` | Creating a visiting patron with an identifier another Alma user already has | Overlapping barcodes between members: `virtual-patron-barcode-prefix` |
| `401861` | User not found. Normal when DCB looks for a visiting patron that does not exist yet | Only an error at sign-in or tracking |
| `401866` | Patron password rejected | The patron's internal password |
| `401689` | No item has that barcode. DCB checks this before creating a stand-in item; this answer means the barcode is free | — |
| `401694` | Request not found. DCB treats a request that has left Alma as missing, which ends tracking | Whether staff cancelled or fulfilled it in Alma |

### Symptoms

| Symptom | Cause |
|---|---|
| A lent item goes onto the hold shelf when scanned in, and the request never moves | The scan happened at the sharing library, or the item belongs to it. See [A library to lend from](alma-setup.md#lending-library). |
| Every borrowing request fails when the stand-in item is created | `virtual-item-location-code` does not exist in `virtual-item-library-code`, or the key cannot read Configuration. |
| A borrowing request stops with "barcodes clash" | One of the borrowing institution's own items already has the lender's barcode. A consortium decision: see [Before your patrons can borrow](alma-setup.md#borrowing). |
| Items reach no agency, or vanish from availability | Location mappings keyed on shelving locations, or a library with no mapping. The mapping audit shows `READ_FROM_SYSTEM` rows as `MISSING`. |
| A renewal goes through after DCB reported it denied | No loan rule refuses renewal for `no-renew-item-policy`. |
| Staff cancelled a request in Alma | Alma removes a cancelled request from the patron's list. DCB reads it as gone and handles it as a cancellation. |
| An empty harvest with no error | The OAI set is wrong or empty. |
| OAI `error_code 21` | The institution is not entitled to OAI-PMH at Ex Libris. Not a configuration fault. |
| "Missing required configuration property" | A required setting from [Host LMS settings](#settings) is absent. |

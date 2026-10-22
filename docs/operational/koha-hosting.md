# Koha hosting and configuration reference

For the hosting provider or in-house administrator of a Koha that is joining an OpenRS
consortium, and for the OpenRS team configuring it. The library-facing setup guide is
[Setting up a Koha library](koha-setup.md).

Supported Koha versions: 25.05, 25.11 and 26.05. Everything below was checked against the
25.11 OpenAPI specification and the 26.05 source; where a version differs it is said.

## Two addresses

Koha is the only system in DCB that needs two URLs, because its two front doors are served
by different hosts on most hosted installations.

| Setting | What it is | Credentials |
|---|---|---|
| `api-url` | Where the REST API is served, with or without `/api` or `/api/v1` on the end. A path prefix such as `/koha` is kept and is part of what identifies the server. At ByWater this is the OPAC host. | OAuth2 client credentials |
| `base-url` | The OPAC origin, with no path. `oai.pl` is always served by the OPAC. | None |

## Host LMS settings

| Key | Required | Value |
|---|---|---|
| `api-url` | yes | the REST API address |
| `client_id`, `client_secret` | yes | the API key pair generated on the DCB API patron (underscores, not hyphens) |
| `sharing-library-code` | yes | the branch that stands for "outside this Koha": where supplier holds are collected and where virtual patrons belong. It must not be a branch that lends; see the setup guide |
| `virtual-item-library-code` | yes | the branch a virtual item is created at when the borrowing patron's own branch is unknown |
| `default-agency-code` | unless `shared-system` | the agency a patron or item falls to when no branch mapping matches |
| `base-url`, `metadata-prefix` | for ingest | the OPAC origin; `marcxml` |
| `oai-set`, `oai-path`, `page-size` | no | harvest scoping; the OAI path if the site rewrites URLs; harvest page size |
| `virtual-patron-category` | no, default `DCB` | the category a visiting patron is created in when their patron type has no mapping yet |
| `no-renew-collection-code` | no, default `DCB_NO_RENEW` | must equal the value in this Koha's `ItemsDeniedRenewal` |
| `do-not-renew-note` | no | the internal note DCB writes on an item it has stopped being renewed |
| `virtual-patron-email` | no | the consortium mailbox given to virtual patrons |
| `honour-opac-hidden-items` | no, default `true` | leave out the copies `OpacHiddenItems` hides from the OPAC, read anonymously from the public API. Only `false` turns it off, for a Koha that keeps its public API closed; the OpenRS team then mirrors `OpacHiddenItems` in the item rule |
| `shared-system`, `contextHierarchy` | for a co-tenanted Koha | see [shared systems](../shared-systems.md) |

## Access DCB needs, and why

Koha's OAuth2 token carries every permission of the patron that owns the key (OAuth2
scopes are not implemented), so the API patron holds exactly this set and nothing else.
Permissions are the `x-koha-authorization` values from the REST specification.

| Koha permission | Routes DCB calls | Why | Role |
|---|---|---|---|
| `catalogue` | items for a biblio, item by id or barcode, libraries, item types | live availability, tracking, walk-up lookup, ping, vocabularies | all |
| `borrowers: list_borrowers` | patron search, patron by id | find a patron by barcode or userid; find the virtual patron | all |
| `borrowers: edit_borrowers` | create patron, a patron's holds | create the virtual patron | supplier, pickup |
| `borrowers` (the whole flag) | update patron | change the virtual patron's category when the mapping changes | supplier, pickup |
| `borrowers: api_validate_password` | password validation | sign-in with barcode and password | borrower |
| `reserveforothers` (the whole flag) | place hold | holds for real and virtual patrons; the `place_holds` sub-permission alone does not allow POST | all |
| `reserveforothers: place_holds` | list, read and cancel holds | tracking, hold counts, cancellation | all |
| `circulate: circulate_remaining_permissions` | checkout, availability, checkouts list, renewals, renewability | loan the real item to the virtual patron, renewals, the renewal-prevention check, the patron's loans | supplier, borrower |
| `editcatalogue: edit_catalogue` | create and delete biblio, delete item | the virtual bib and item | borrower, pickup |
| `editcatalogue: edit_items` | create and update item | the virtual item; renewal prevention; re-resolution | borrower, pickup |
| `parameters: manage_patron_categories` | patron categories | the configuration check's category list | setup |
| `updatecharges: remaining_permissions` | patron account | charges on the discovery account page; optional, loans are still shown without it | borrower |
| `parameters: manage_sysprefs` | system preferences (Koha 26.05 and later) | the configuration check reads the preferences above and says which are not as DCB needs; optional, and absent on older Kohas | setup |

OAI-PMH needs no credentials; the OPAC must be reachable from DCB.

## Keys

1. Administration, System preferences, Web services: `RESTOAuth2ClientCredentials` = Enable.
   Leave `RESTBasicAuth` disabled.
2. Create a staff patron for DCB in the sharing branch, with the permissions above, an
   expiry far in the future and no messaging preferences.
3. On that patron: More, Manage API keys, generate. The client secret is shown once.

With `IndependentBranches` enabled, the API patron must be allowed to act across branches
(superlibrarian, or the provider's equivalent), or holds and item edits for other branches
are refused. Confirm this on a test server before onboarding a shared Koha.

## System preferences DCB depends on

| Preference | Value | Why |
|---|---|---|
| `RESTOAuth2ClientCredentials` | Enable | the only credential DCB uses |
| `OAI-PMH` | Enable | the harvest |
| `OAI-PMH:archiveID` | site-specific, set before the first harvest | DCB mints record ids from it; changing it later duplicates the catalogue in DCB |
| `OAI-PMH:DeletedRecord` | persistent | deletions reach the shared index |
| `OpacSuppression` | Hide, once at least one record carries `942$n` | hidden records stay hidden; DCB's virtual bibs carry the flag. Turning it on after the first harvest reaches DCB only for records edited afterwards, or when the OpenRS team runs the vanished sweep |
| `OpacHiddenItems` | any | a copy it hides is not offered to the consortium: DCB reads the OPAC's own view of each title's copies. A record it hides entirely (with `OpacHiddenItemsHidesRecord`) is withheld from the harvest once the record is next edited, or when the OpenRS team runs the vanished sweep |
| `RESTPublicAPI`, `RESTPublicAnonymousRequests` | Enable (Koha's default) | DCB reads the OPAC's view of a title's copies anonymously. With either off, every availability check for this Koha fails, unless `honour-opac-hidden-items` is `false` |
| `ItemsDeniedRenewal` | contains `ccode: [DCB_NO_RENEW]` | renewal prevention; DCB verifies it on each use and fails loudly when it is missing |
| `AllowHoldPolicyOverride` | Allow | DCB sends `x-koha-override: any` on holds for its own virtual patron; Koha honours the header only with this on, and then skips its hold policies for that hold. Holds for real patrons never send it |
| `IndependentBranches` | off, or the API patron acts across branches | on, the API patron may only edit items and patrons of its own branch |
| `canreservefromotherbranches` | on, if `IndependentBranches` is on | off, a patron may hold only their own branch's items: supplier holds and co-tenant local requests are refused with `cannotReserveFromOtherBranches` |
| `ReservesNeedReturns` | the default ("don't automatically") | a filled hold must go through transit to the sharing branch |
| `UseBranchTransferLimits` | if on, allow transfers to and from the sharing branch | otherwise supplier holds cannot be filled |
| `RESTdefaultPageSize` | any | DCB pages explicitly |

## Network

Outbound from DCB to the API address over HTTPS, and to the OPAC for OAI-PMH. Nothing
inbound. SIP2 is not used by the standard, local or supplying flows.

## Load on the Koha

| Path | What DCB does | Bound |
|---|---|---|
| availability | one paged item read and one paged anonymous public item read per title that has copies here, per search, plus one active-holds read per copy | 100 items a page, 500 a title, each read; four copies at a time per Host LMS |
| tracking | one hold read and one item read per request DCB is tracking here, per sweep | the sweep interval |
| harvest | `ListRecords` with resumption tokens | `OAI-PMH:MaxCount` records a request |
| sign-in | one password validation and one patron read per sign-in | per request |
| lending | one patron search and at most one patron creation, one hold, and when the borrower lends it one checkout, re-sent once with a confirmation after one availability read; one checkouts read and one renewal per borrower renewal | per request |

## What DCB leaves behind

Virtual patrons: one per visiting patron, in the DCB category, never deleted by the
workflow. Virtual bibs and items are created per request and deleted when the request is
finalised; they land in `deletedbiblio` and `deleteditems` like any staff deletion.

## Troubleshooting

| Symptom | Cause | Remedy |
|---|---|---|
| `Koha refused the hold`, reason `cannotReserveFromOtherBranches` | `IndependentBranches` on without `canreservefromotherbranches` | allow holds from other branches, or `AllowHoldPolicyOverride` for DCB's virtual patron |
| `Koha refused the hold`, reason `libraryNotPickupLocation` | the pickup location's `localId` is a branch Koha does not flag for pickup | the configuration check lists the pickup branches and judges each DCB pickup location against them |
| `Koha refused the hold`, reason `noReservesAllowed`, `notReservable` or `tooManyReserves` | the circulation rule for the branch, category and item type | the setup guide's circulation rules |
| `Koha refused the hold`, reason `debt_limit`, `expired`, `restricted`, `hold_limit`, `card_lost` | the patron's own block | for a real patron the block stands; for DCB's virtual patron, `AllowHoldPolicyOverride` |
| 409 `debt_limit`, `expired`, `restricted` on a supplier hold | the virtual patron is blocked, and the override header was not honoured | the DCB category's enrolment period and fines rules |
| `Koha refused the checkout`, blocked `ITEM_LOST`, `NOT_FOR_LOAN` or another key | Koha will not lend the copy to anyone | the item's own status |
| `Koha wants a confirmation DCB does not give`, confirmations `DEBT`, `TOO_MANY`, `BORRNOTSAMEBRANCH` or another key | a prompt Koha shows staff that DCB does not answer for a consortium loan; DCB answers only `TRANSFERRED`, `RESERVED` and `RESERVE_WAITING` | the `DCB` category's circulation rule (no fines, no loan limit); with `IndependentBranches`, the API patron acts across branches |
| `Koha has no loan to renew` | the renewal reached Koha before the virtual patron's loan existed, or after the item was returned | nothing: the loan is renewed once it exists |
| a hold answers 404 while the request is open | the hold was filled or cancelled and moved to Koha's old holds | nothing: DCB reads the old hold and acts on it |
| virtual bibs appear in the OPAC or the shared index | the Koha predates the suppressed virtual bib, or `OpacSuppression` is off | enable `OpacSuppression`; DCB's ruleset drops them on the next harvest |
| `Koha would not say which copies its OPAC hides` | `RESTPublicAPI` or `RESTPublicAnonymousRequests` is off | turn both on, or set `honour-opac-hidden-items` to `false` and have the OpenRS team mirror `OpacHiddenItems` in the item rule |
| `Koha still allows this loan to be renewed` | `ItemsDeniedRenewal` does not list the collection code | add `ccode: [DCB_NO_RENEW]` |
| every supplier request stalls before transit | the sharing library is a lending branch, or `ReservesNeedReturns` is automatic | a dedicated sharing branch |
| twenty copies reported for a title with more | the DCB release predates paged item reads | upgrade |

## Hosting providers

| | ByWater Solutions | Equinox Open Library Initiative | Self-hosted |
|---|---|---|---|
| API address | the OPAC host | ask the provider | whichever virtual host serves `/api/v1` |
| Keys | self-service, as above | by support request | self-service |
| Upgrade cadence | tracks the community release within months; general upgrades are announced with test servers first | with the hosting contract | the library's |
| SIP2 | available for self-check vendors; not needed for these flows | available | the library's |

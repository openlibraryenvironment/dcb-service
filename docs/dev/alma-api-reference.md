# Alma API reference

Every Alma REST call the adapter makes, the `AlmaApiClient` method that makes it, and the DCB
operation that uses it. Paths are relative to `alma-url`, the regional API gateway. Ex Libris
documents each one in the [Alma REST APIs](https://developers.exlibrisgroup.com/alma/apis/).

How the calls fit together is in [Alma integration](alma-integration.md).

## Users

| Call | `AlmaApiClient` method | Used by |
|---|---|---|
| `GET /almaws/v1/users/{user_id}` | `getUserDetails` | `getPatronByLocalId`, `getPatronByIdentifier`, `getPatronByUsername`, `findVirtualPatron`, `patronAuth` (after authenticating), `updatePatron` (before writing) |
| `POST /almaws/v1/users/{user_id}?op=auth`, password in the `Exl-User-Pw` header | `authenticateUser` | `patronAuth` |
| `POST /almaws/v1/users` | `createUser` | `createPatron` |
| `PUT /almaws/v1/users/{user_id}` | `updateUserDetails` | `updatePatron` (with `override=user_group`), virtual patron expiry extension |
| `DELETE /almaws/v1/users/{user_id}` | `deleteUser` | `deletePatron` |
| `GET /almaws/v1/users`, `GET /almaws/v1/users?q=external_id~{id}` | `retrieveUsers`, `getUsersByExternalId` | Not used |

## User requests (holds)

| Call | `AlmaApiClient` method | Used by |
|---|---|---|
| `POST /almaws/v1/users/{user_id}/requests?item_pid={item_pid}` | `createUserRequest` | All four `placeHoldRequestAt…Agency` methods |
| `GET /almaws/v1/users/{user_id}/requests?request_type=HOLD&limit=100&offset={n}` | `retrieveUserHoldRequestsPage` | Hold placement, to find one DCB already placed (at most 5 pages) |
| `GET /almaws/v1/users/{user_id}/requests?request_type=HOLD` | `retrieveUserHoldRequests` | `countHoldsForPatron` (`total_record_count`) |
| `GET /almaws/v1/users/{user_id}/requests/{request_id}` | `retrieveUserRequest` | `getRequest` |
| `DELETE /almaws/v1/users/{user_id}/requests/{request_id}?reason=&override=true&notify_user=false` | `cancelUserRequest` | `cancelHoldRequest`, `deleteHold` |

## Loans

| Call | `AlmaApiClient` method | Used by |
|---|---|---|
| `GET /almaws/v1/users/{user_id}/loans?limit=100&offset={n}` | `retrieveUserLoansPage` | `renew`, and confirming a checkout Alma did not answer (at most 5 pages) |
| `POST /almaws/v1/users/{user_id}/loans?item_pid={item_pid}` | `createUserLoan` | `checkOutItemToPatron` |
| `POST /almaws/v1/users/{user_id}/loans/{loan_id}?op=renew` | `renewLoan` | `renew` |

## Bibliographic records and inventory

| Call | `AlmaApiClient` method | Used by |
|---|---|---|
| `GET /almaws/v1/bibs/{mms_id}/holdings/ALL/items?limit=100&offset={n}&expand=due_date` | `retrieveItemsPage`, through `retrieveAllItems` | `getItems` (live availability) |
| `GET /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/{item_pid}` | `retrieveItem` | `getItem`, `updateItemStatus`, `updateHoldRequest`, `preventRenewalOnLoan`, and the reads that confirm a scan took effect |
| `GET /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/{item_pid}/requests` | `retrieveItemRequests` | Hold counts in `getItems` and `getItem`; the hold's pickup library before a transit scan; `getItemByBarcode` |
| `GET /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/{item_pid}/request-options?user_id=` | `retrieveItemRequestOptions` | `checkRequestOptions`, after a `401129` |
| `GET /almaws/v1/items?item_barcode={barcode}` (redirects to the item) | `retrieveItemBarcodeOnly` | `checkOutItemToPatron` (to find the item's library), the barcode check before `createItem`, `getItemByBarcode`, request options for a supplier item |
| `POST /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/{item_pid}?op=scan&library=&circ_desk=` | `scanIn` | `updateItemStatus`, `checkInItem` |
| `PUT /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/{item_pid}` | `updateItem` | `updateHoldRequest`, `preventRenewalOnLoan` and its staff-note fallback |
| `POST /almaws/v1/bibs` (MARCXML) | `createBibRecord` | `createBib` |
| `GET /almaws/v1/bibs/{mms_id}` | `retrieveBib` | `deleteBib`, before deleting |
| `DELETE /almaws/v1/bibs/{mms_id}` | `deleteBibRecord` | `deleteBib` |
| `GET /almaws/v1/bibs/{mms_id}/holdings` | `retrieveHoldings` | `deleteBib`, before deleting |
| `POST /almaws/v1/bibs/{mms_id}/holdings` (XML) | `createHoldingRecord` | `createItem` |
| `DELETE /almaws/v1/bibs/{mms_id}/holdings/{holding_id}` | `deleteHoldingsRecord` | `deleteItem`; `createItem` when the item could not be created |
| `POST /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items` | `createItem` | `createItem` |
| `DELETE /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/{item_pid}` | `withdrawItem` | `deleteItem` |

## Configuration

Read only. DCB writes nothing under `/conf`.

| Call | `AlmaApiClient` method | Used by |
|---|---|---|
| `GET /almaws/v1/conf/libraries/{library}/locations/{location}` | `retrieveLocation` | `createItem`, to read the virtual item location |
| `GET /almaws/v1/conf/libraries` | `retrieveLibraries` | Configuration report, vocabularies, location import |
| `GET /almaws/v1/conf/libraries/{library}/locations` | `retrieveLocations` | Configuration report, location import |
| `GET /almaws/v1/conf/libraries/{library}/circ-desks/{desk}` | `retrieveCirculationDesk` | Configuration report, to check the sharing desk has a hold shelf |
| `GET /almaws/v1/conf/code-tables/{table}` | `retrieveCodeTable` | Vocabularies: `PhysicalMaterialType`, `UserGroups`, `ItemPolicy` |
| `GET /almaws/v1/conf/test` | `test` | `ping` |

## OAI-PMH

Not on the API gateway: `{base-url}/view/oai/{institution-code}/request`, with the standard
OAI-PMH verbs, `metadataPrefix` from `metadata-prefix` and `set` from `oai-set`. Made by
`AlmaOaiPmhIngestSource`.

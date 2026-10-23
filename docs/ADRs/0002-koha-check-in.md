# ADR 0002: How DCB checks an item in at a Koha

Status: proposed, 2026-10-21. Decided for the borrower milestone only in so far as it is
not needed there; the implementation is scheduled with pickup-anywhere and walk-up.

## Context

`checkInItem` is called on a Koha only when it is the borrower in the pickup-anywhere or
walk-up workflows (`HandleBorrowerRequestReturnTransit`), to return a virtual item DCB
itself checked out. The standard and own-library workflows never call it: the library's
staff check the virtual item in at the desk.

Koha's REST API has no check-in operation on 25.05, 25.11 or 26.05. The checkouts
resource offers nine operations and none returns an item. Bug 24401 adds `POST /checkins`
and `GET /checkins/availability`; it passed QA on 2026-09-04 but depends on bug 42418,
which no longer applies (2026-10-21), so it can reach 26.11 at the earliest and is not
recorded for backport (bug 19406 was closed as its duplicate in 2020). When a Koha DCB
supports has it, it replaces the route below. The alternatives that look like a return are not one: `DELETE
/items/{id}` answers 409 `book_on_loan`, `DELETE /patrons/{id}` answers 409
`has_checkouts`, and setting `lost_status` relies on `MarkLostItemsAsReturned` and leaves
a lost statistic on a record DCB deletes.

One REST operation performs a return as a side effect. `POST
/items/{host}/bundled_items` with `{"external_id": "<barcode>", "force_checkin": true}`
calls `C4::Circulation::AddReturn(barcode, <API patron's branch>)` before linking the
item into a bundle (`Koha::Item::add_to_bundle`, present on 25.05.x, 25.11.00 and
26.05.00), and `DELETE /items/{host}/bundled_items/{item_id}` unlinks it and resets the
item's `notforloan` to 0. Both need only the `catalogue` permission. The item must not be
checked out to the bundle host or on hold at the moment of the call; those are 409s.

## Decision

`checkInItem` uses the bundle route above, against a permanent DCB-owned host item at the
sharing branch named by `bundle-host-barcode`. The side effects (a return recorded at the
API patron's branch, which may open a transfer home; `BundleNotLoanValue` briefly on the
item) land on a virtual item DCB deletes at finalisation, and neither blocks the delete
(`Koha::Item::safe_to_delete` checks loans, waiting holds, analytics and last-item-for-hold).
`ignore_holds` is sent with `force_checkin`: Koha bundles inside one transaction, and a
hold on the item would otherwise roll the return back.

SIP2 was the intended alternative for a site that will not set `BundleNotLoanValue`. It
waits for a SIP2 transport: `Sip2Adaptor` has none, and every operation on it fails. When
there is one, a `checkin-method` setting chooses between the two; until then there is
nothing to choose and no such setting.

No Koha plugin is part of this decision or depends on one. Two exist that would do it: ByWater's
`koha-plugin-ncip-server` answers NCIP CheckInItem (Koha 25.11 or later, shared-token
authentication), which DCB's `NcipAdaptor` already speaks; `koha-plugin-offline-circulation`
returns through `ProcessOfflineReturn`, which skips `AddReturn` and its hold and transfer
handling. ILS-DI has no return service, and Koha's ILL module does not return items.

Known limits of the route, from Koha's source: the return is recorded at the API patron's
home branch; it does not trap holds (`ModReserveAffect`), which on a virtual item with no
other holds changes nothing; Koha passes both the bundled barcode and, in the unlink, the
itemnumber through `barcodedecode` (bug 38398), so a site with an `itemBarcodeInputFilter`
or a barcode-transform plugin may not find the virtual item by the lender's barcode. An
unlink that misses leaves a link the finalising delete removes, because `item_bundles`
cascades on delete.

A Koha with none configured is a complete member for the standard and own-library
workflows, and `checkConfiguration` says which workflows it supports.

## Consequences

- No new port, credential or plugin is needed.
- The behaviour of `add_to_bundle` when `BundleNotLoanValue` is unset, and whether the
  return opens a transfer home for the virtual item, are verified on each supported
  version before the route is relied on; the sandbox checklist carries both.
- The bundle host item is part of the administrator's setup, documented in
  [the setup guide](../operational/koha-setup.md).

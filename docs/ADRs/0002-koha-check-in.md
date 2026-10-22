# ADR 0002: How DCB checks an item in at a Koha

Status: proposed, 2026-10-21. Decided for the borrower milestone only in so far as it is
not needed there; the implementation is scheduled with pickup-anywhere and walk-up.

## Context

`checkInItem` is called on a Koha only when it is the borrower in the pickup-anywhere or
walk-up workflows (`HandleBorrowerRequestReturnTransit`), to return a virtual item DCB
itself checked out. The standard and own-library workflows never call it: the library's
staff check the virtual item in at the desk.

Koha's REST API has no check-in operation on 25.05, 25.11 or 26.05. The checkouts
resource offers nine operations and none returns an item; bug 19406, asking for one, has
been open since 2017. The alternatives that look like a return are not one: `DELETE
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

No Koha plugin is part of this decision or depends on one.

A Koha with none configured is a complete member for the standard and own-library
workflows, and `checkConfiguration` says which workflows it supports.

## Consequences

- No new port, credential or plugin is needed.
- The behaviour of `add_to_bundle` when `BundleNotLoanValue` is unset, and whether the
  return opens a transfer home for the virtual item, are verified on each supported
  version before the route is relied on; the sandbox checklist carries both.
- The bundle host item is part of the administrator's setup, documented in
  [the setup guide](../operational/koha-setup.md).

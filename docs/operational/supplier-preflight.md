# The supplier preflight

Before DCB places a hold at a supplying library, it checks whether that library can take the
request. A library whose own setup rules it out is skipped, and the request goes to another
library, instead of failing at the supplier and ending in ERROR.

The check uses what DCB already holds. It does not call the library's system, except to repeat a
ping, described below.

## When a supplier cannot supply

A supplier is ruled out when one of these is true:

| Fault | Checked when DCB chooses a copy | Checked when DCB places the hold |
|---|---|---|
| DCB cannot build a client from the Host LMS configuration: an unknown client class, or settings the adapter refuses | yes | yes |
| The Host LMS's last ping was **refused** (the system rejected DCB's credentials) or **misconfigured** (DCB could not build a client for it) | yes | yes |
| The requesting patron's DCB patron type has no patron type mapped at the supplier | no | yes |
| The library system's own check refuses it | no | yes |

No library system has a check of its own yet: each answers "not checked", which never rules a
supplier out.

These do **not** rule a supplier out:

- **Unreachable at its last ping.** The hold is placed anyway. If that ping is under 15 minutes
  old, the request's audit records "Supplier unreachable at its last check: placing anyway". An
  older one is ignored rather than taken again: the scheduled ping runs once a day, and re-taking it
  during placement would add a timeout while the supplier is down.
- **A ping that failed for another reason**, such as the system answering with a server error,
  or a Host LMS never pinged.
- **A setting the adapter declares mandatory but does not need to supply.** Sierra's `ingest` is
  one.
- **Anything DCB cannot establish.** If DCB cannot read its own records, or a patron type lookup
  fails for another reason, the hold is placed.

## The ping behind the check

DCB pings every Host LMS two minutes after it starts, then once a day
(`dcb.task.housekeeping.interval`, default `24h`). It also pings a Host LMS whenever it is saved in
DCB Admin.

A refused or misconfigured ping more than 15 minutes old is not trusted by itself. DCB pings
that Host LMS again, waiting up to 10 seconds, before ruling it out. If the new ping succeeds,
the supplier is used. If it cannot finish, the supplier is not ruled out.

A Host LMS's last ping, with its full detail, is shown in DCB Admin to that Host LMS's
administrators. The reasons recorded on a request give only the kind of failure and when it was
seen, because borrowing libraries' staff read them.

## What happens to the request

**When DCB chooses a copy,** copies at a supplier that is ruled out are skipped. The resolution
audit records each as excluded by `ExcludeSupplierThatCannotSupplyItemFilter`.

**When DCB places the hold,** before any placement method runs, NCIP included:

- no hold is placed, and the supplier request is cancelled;
- the request's audit records "Supplier cannot supply: request not placed", with the supplier,
  the reason and `howToFix`, what that library's staff need to do;
- DCB raises the alarm `ILS.<Host LMS code>.SUPPLIER_CANNOT_SUPPLY`, which lasts 7 days and
  notifies only when it is first raised;
- the request moves to NOT_SUPPLIED_CURRENT_SUPPLIER.

What follows depends on the consortium's `RE_RESOLUTION` setting:

- **On:** DCB looks for a copy at another library, leaving out every library already tried.
- **Off:** the request ends as NO_ITEMS_SELECTABLE_AT_ANY_AGENCY.

A request that ends with no copy because suppliers were ruled out explains why:

- its error message reads "Failed to find a valid item: " followed by the first reason;
- its audit records "Failed to find a valid item", listing each supplier with its reason and
  `howToFix`.

## A copy someone chose

A request for a copy chosen in a discovery interface or by staff is refused before it exists if
the copy's library cannot supply because of its client or its last ping. The refusal is the
preflight failure `SUPPLIER_CANNOT_SUPPLY`, and its message reads "The library that holds the
chosen copy cannot supply it at the moment, because of a problem with its setup. Choose another
copy, or ask that library to check its DCB configuration."

If a chosen copy's library is ruled out later, when DCB chooses the copy or places the hold (by a
missing patron type mapping, for example), the request goes to ERROR with the reason. DCB never
swaps a chosen copy for another.

To turn off the refusal before a request exists (placement still checks):

```yaml
dcb:
  requests:
    preflight-checks:
      chosen-copy-supplier:
        enabled: false
```

or `DCB_REQUESTS_PREFLIGHT_CHECKS_CHOSEN_COPY_SUPPLIER_ENABLED=false`.

## Turning it on

It is **off by default**. While it is off, no supplier is ruled out: no copy is skipped when DCB
chooses one, every hold is placed, and a chosen copy is never refused, as before this check
existed. DCB says which at startup: "Supplier preflight is off" or "Supplier preflight is on".

Turn it on once the pings have been read (see "After upgrading"):

```yaml
dcb:
  requests:
    supplier-preflight:
      enabled: true
```

or `DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED=true`, then restart. Setting it back to false, or
removing it, turns it off again.

The switch does not affect the rule below, which uses only DCB's own records and is always on.

## A copy the patron already holds

When DCB chooses a copy, it skips one the same patron already holds at its supplier through
another request. That means a request that:

- is not finalised or archived;
- has a hold placed at the supplier;
- has a hold whose last recorded status is not cancelled or missing.

The supplier would refuse a second hold by the same patron on the same copy: FOLIO answers "This
requester already has an open request for this item", and Sierra "already on hold for or checked
out to you". This applies to every request, staff's and re-resolution's included, and covers a
patron retrying after a request went to ERROR with its supplier hold still in place. Another copy
of the same title can still be chosen. If DCB cannot read its records, the copy is not skipped.

This is separate from the [duplicate-request check](duplicate-request-check.md), which refuses a
patron's second request for a title before it exists, is off by default and never refuses staff.

## After upgrading

The scheduled ping runs two minutes after DCB starts whether or not the preflight is on, and the
first one after an upgrade may be the first time DCB has checked each system's credentials. With
the preflight still off, open each Host LMS in DCB Admin and read its last ping. Correct and save
any that were refused or misconfigured, and confirm no library that supplies today is among
them. Then turn the preflight on.

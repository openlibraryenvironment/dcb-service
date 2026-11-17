# The duplicate-request check

Refuses a request a **patron** places for a title they already have an active request for. A request a library's staff place is never refused, so staff can always place a request the
check turned away.

## What counts as a duplicate

The same patron, at the same library system, already has a request for the same title (cluster)
and the same volume, and that request is:

- in flight (submitted, resolved, placed, confirmed, in transit, awaiting pickup) or on loan; and
- still tracked: a request flagged as stuck for too long does not count.

Volumes are compared the way resolution compares them, so "v.2", "Vol. 2" and "Vol [2]" are the
same volume. A request for volume 2 never blocks one for volume 3 of the same set, and a request
without a volume never blocks one with a volume, or the other way round. A blank volume counts as
none.

A **request for a chosen copy** is compared by copy, not by title. A patron who picks a copy in a
discovery interface, rather than letting DCB choose one, means to request that copy even if they
already have the title on request, so it is refused only when that same copy (the same Host LMS
and item) is already coming to them: chosen by an earlier request, or the copy an earlier request
is being supplied with. Another copy of the same title is never refused, which covers a patron
choosing a different volume of a set catalogued as one record. A chosen copy must name its item,
system and agency; one missing any of them is placed, and checked, as a request for the title.

A **multi-volume title** is not one thing, so a request for it without a volume is never refused
as a duplicate of the title, whatever the patron already has on request: only the same chosen copy,
or the same named volume, is. DCB takes a title to be multi-volume when any of its records'
physical descriptions (MARC 300, as ingested) is of more than one volume: "3 v.", "v. 1-4", or an
open entry such as "v. :". "2 v. in 1" is one item, and "v, 423 p." is one volume with roman
numbered front matter. A record without a physical description counts as a single volume.

On a single-volume title, a request for the title made after any active request for it, including
one for a chosen copy, is refused: it would be a second copy of the same thing.

A request that failed, was cancelled, found no items or has finished never blocks a new one, so a
patron can always retry. By default a second request is refused for as long as the first is
active; an active window narrows that (see "How strict").

The patron sees "Sorry. It appears that you have already requested this item. If you think this
is a mistake, please see library staff for assistance." The refusal is recorded in the event log
like every failed preflight check, so it is still counted.

If DCB cannot read the patron's requests, the request is allowed.

## Who counts as staff

A request placed with a token issued to one of the **staff applications**, DCB Admin and DCB Admin
for Libraries, is staff's. Walk-up requests are always staff's. Roles do not decide this: discovery
services that place patrons' requests, EBSCO Locate among them, hold admin roles too.

## Turning it on

```yaml
dcb:
  requests:
    preflight-checks:
      duplicate-requests:
        enabled: true
```

or `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ENABLED=true`.

Staff are recognised by the OIDC client the two applications sign in through: the `azp` claim
of their tokens. By default those are `dcb-admin` and `dcb-admin-for-libraries`. If a deployment's
clients are named differently, set them:

```yaml
dcb:
  requests:
    staff-client-ids: <DCB Admin client id>,<DCB Admin for Libraries client id>
```

or `DCB_REQUESTS_STAFF_CLIENT_IDS`. **Check this before enabling the check.** A staff
application whose client is not in the list is treated as a patron, so its requests can be
refused. DCB logs the client ids in use at startup: "Duplicate request check is ON; requests
placed through clients [...] are staff's".

`staff-client-ids` must name at least one client: an empty or blank list stops DCB starting,
with a message naming this setting. It is not an off switch; `enabled: false` is.

## How strict

One optional setting, the **active window**, chooses how strict the check is:

| Level | Setting | Refuses a patron's repeat request for a title when an earlier one is… |
|---|---|---|
| Off | `enabled: false` (the default) | never refused |
| Recent | `enabled: true`, `active-window: 24h` (any positive duration: `15m`, `7d`) | still active, **and** placed within the window |
| Active | `enabled: true`, no `active-window` | still active, however old |

```yaml
dcb:
  requests:
    preflight-checks:
      duplicate-requests:
        enabled: true
        active-window: 24h
```

or `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ACTIVE_WINDOW=24h`.

The levels are nested: Recent is Active limited to recent requests, so a shorter window is always
less strict, and no level refuses a request a stricter one would allow. At every level, staff are
never refused, a chosen copy is compared only with the same copy, volumes are compared as above, and a failed, cancelled or
finished request never counts. A window of zero or less fails startup.

**What a window gives up.** A request outside the window is placed even though the patron's
first request for the title is still in transit or on loan. DCB then sends a second copy, or the
supplier refuses the second hold and the request goes to ERROR. In one production consortium's
data, 85 of 150 repeat requests came more than 15 minutes after the patron's previous request
for the title. Use a window as a rollout step, watching refusals in the event log before
removing it, or where a consortium's policy allows a patron a repeat request over time.

The startup log names the level: "Duplicate request check is ON (active requests placed within
PT24H)", or "(while the earlier request is active)".

The `request-window` setting of earlier releases is no longer read, and DCB logs a warning at
startup if it is still set. It counted any request, whatever its state, in the last 15 minutes,
so it refused a patron's retry after a failure. `active-window` never does.

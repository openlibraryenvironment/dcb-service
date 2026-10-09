# The duplicate-request check

Refuses a request a **patron** places for a title they already have an active request for. A request a library's staff place is never refused, so staff can always place a request the
check turned away.

## What counts as a duplicate

The same patron, at the same library system, already has a request for the same title (cluster),
placed without a volume, and that request is:

- in flight (submitted, resolved, placed, confirmed, in transit, awaiting pickup) or on loan; and
- still tracked: a request flagged as stuck for too long does not count.

Two kinds of request are never refused:

- **A request for a chosen copy.** A patron who picks a copy in a discovery interface, rather than
  letting DCB choose one, means to request that copy even if they already have the title on request.
- **A request for a volume.** DCB does not yet choose a copy by volume, so it cannot tell a second
  request for the same volume from a request for another volume of the same set. Until it can, a
  request that names a volume is not checked. A blank volume counts as none.

A request that failed, was cancelled, found no items or has finished never blocks a new one, so a
patron can always retry. There is no time window: a second request is refused for as long as the
first is active.

The patron sees the standard "you already have an existing request for this resource" message. The
refusal is recorded in the event log like every failed preflight check, so it is still counted.

If DCB cannot read the patron's requests, the request is allowed.

## Who counts as staff

A request placed with a token issued to one of the **staff applications**, DCB Admin and DCB Admin
for Libraries, is staff's. Walk-up requests are always staff's. Roles do not decide this: discovery
services that place patrons' requests, EBSCO Locate among them, hold admin roles too.

## Turning it on

```yaml
dcb:
  requests:
    staff-client-ids: <DCB Admin client id>,<DCB Admin for Libraries client id>
    preflight-checks:
      duplicate-requests:
        enabled: true
```

or `DCB_REQUESTS_STAFF_CLIENT_IDS` and `DCB_REQUESTS_PREFLIGHT_CHECKS_DUPLICATE_REQUESTS_ENABLED`.

The client ids are the OIDC clients the two applications sign in through (the `azp` claim of their
tokens). Until they are set, the check stays off even when enabled, and DCB logs that at startup:
without them, staff could not place a request the check refused.

The `request-window` setting of earlier releases no longer exists.

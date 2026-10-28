# Patron checks

`POST /patrons/eligibility-checks` lets library staff ask why one of their own patrons would be
refused, without signing in as the patron and without seeing the patron's record. It is decision
D-23 in the OpenRS for Libraries plan; DCB Admin for Libraries calls it from **Check a patron**.

## The request and the answer

```json
POST /patrons/eligibility-checks
{ "identifier": "21000000000777", "agencyCode": "AVL" }
```

`agencyCode` is optional when the caller's token names one library, and required when it names
several. The identifier travels in the body only, so it never reaches a URL or an access log.

```json
{ "outcome": "REFUSED", "reasons": ["BLOCKED", "OVER_LOAN_LIMIT"] }
```

`outcome` is `ELIGIBLE` or `REFUSED`. `reasons` come from a fixed list, `PatronCheckReason`,
mapped from the failure codes placement already uses; a code with no mapping is `UNCLASSIFIED`.
No name, address, contact or any other field of the patron's record is ever returned. A patron
whose home library is not one of the caller's is `NOT_FOUND`, so a library on a shared system
learns nothing about its co-tenants' patrons.

The rules are placement's own: `PatronEligibilityChecks` is shared by `ResolvePatronPreflightCheck`
and the check, so the two cannot disagree.

## Who may call it

`LIBRARY_ADMIN` only (LIBRARY_STAFF once it exists), for their own libraries. A library they do
not hold is refused with 403 `AGENCY_NOT_PERMITTED`. Other roles are refused as unauthenticated.

## Limits

Every check is a live read of a library system DCB does not own.

| Setting | Default | |
| --- | --- | --- |
| `dcb.patron-checks.per-caller-per-minute` | 10 | per member of staff, by token subject |
| `dcb.patron-checks.per-host-lms-per-minute` | 30 | per library system |
| `dcb.patron-checks.timeout` | 10s | the library system's answer |
| `dcb.patron-checks.enabled` | true | `false` removes the route |

Past a limit the answer is 429 with `Retry-After: 60` and a code, `TOO_MANY_CHECKS` or
`LIBRARY_SYSTEM_BUSY`. The counts are per DCB instance: with three instances behind a balancer, a
member of staff can make up to three times the per-caller figure. Each window opens at the first
check and lasts a minute; both caches are bounded (10,000 callers, 1,000 systems).

`Retry-After` is listed in the CORS exposed headers (`micronaut.server.cors.configurations.all`),
because the admin apps run on another origin and a browser hides it from them otherwise.

## Logging

DCB never logs the identifier: `PatronCheckApiTests` raises DCB's own loggers to TRACE and fails
if any line carries it. Micronaut's HTTP client is different: at DEBUG it prints every outbound
URL, and a Sierra patron search puts the barcode in its URL. `logback.xml` pins
`io.micronaut.http.client` at WARN; do not raise it in production, for this check or for patron
sign-in, which makes the same search.

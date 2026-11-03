# Insights per library on a shared system

Every `/insights/**` endpoint that takes `requestedLibraryCode` also takes `requestedAgencyCodes`:
one agency code, or several separated by commas, at most 50. With it, the figures are those
libraries' own rather than their whole Host LMS's. Without it, nothing changes.
[insights.md](insights.md) has the rest of the surface.

## Who may ask for what

`StatsScopeGuard.resolve(authentication, requestedLibraryCode, requestedAgencyCodes)`:

- A library caller may name only agencies in their own `code` claim; any other is refused (403),
  as a library code they do not administer is.
- A consortium caller may name any.
- The named agencies' own Host LMS codes become the library filter, so they take precedence over
  `requestedLibraryCode`.
- A blank code, a trailing comma or more than 50 codes is a 400.

## What "a library's own" means in each query

| Side | Counted when | Kept on |
|---|---|---|
| Borrowing | the request's patron belongs to one of the agencies | the requesting patron identity's resolved agency |
| Lending | one of the agencies supplied it | `patron_request.local_item_agency_code` |

Each of the 43 places a query keeps its own system's requests on a side gains the matching agency
condition beside it. A comparison with other systems ("not ours", in trading partners and the
lifeline queries) stays by system, and so does every check of what a system holds: DCB keeps a
system's bib records, not which of its libraries holds a copy.

**The borrowing side is by the patron's current library.** The identity's agency is set at each
validation, so a patron who moves library carries their past requests with them, and a patron
whose home library code is not mapped is counted at the system's default agency. The lending side
is the library that supplied, fixed when it was chosen. Say "by your patrons' current library"
beside borrowing figures.

## Cost

The borrowing condition is an `EXISTS` on `patron_identity` and `agency` by primary key for each
request already in the window; the lending condition compares a column. No index is added and none
is needed to match today's cost: the Host LMS filters these sit beside have no index either, and
every one of these queries already reads the requests in its window. The lending analysis cache is
keyed on the agencies too, under its existing size bound.

## Tests

`InsightsPerAgencyQueriesTests` puts two libraries on one system and checks borrowing, lending,
the demand heatmap, net flow and fill rate for each and for both. `StatsScopeGuardTests` covers who
may name which agencies.

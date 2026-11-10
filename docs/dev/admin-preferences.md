# Staff preferences: for developers

Operations: [docs/operational/admin-preferences.md](../operational/admin-preferences.md).

## API

Every route is under `/preferences`, secured to `ADMINISTRATOR`, `CONSORTIUM_ADMIN`,
`LIBRARY_ADMIN` and `LIBRARY_READ_ONLY`, and acts on the caller's own key. No route takes an
owner. `LIBRARY_READ_ONLY` may write, deliberately: these are the person's settings, not library
data.

| Route | Answer |
|---|---|
| `GET /availability` | Always 200: `{available, maxSavedViews, maxWatched}`. A 404 therefore means a dcb-service without the feature |
| `GET /` | The record, 404 when nothing is stored yet, 503 when off |
| `PUT /display` | The display fields, whole. A null field is "not chosen", which is not the default |
| `PUT /apps/{app}` | `admin` or `libraries`: replaces that app's slice of `app_state` and leaves the other's alone |
| `DELETE /` | Settings, saved filters and watches. Idempotent |
| `GET /views?viewType=`, `POST /views`, `PUT /views/{id}`, `DELETE /views/{id}` | Saved filters. 409 at the limit or for a name already used on that grid |
| `GET /watched?pageno=&pagesize=` | Ids and dates, newest first, 100 a page at most |
| `PUT /watched/{id}` | Watch. Idempotent; 409 at the limit |
| `DELETE /watched/{id}` | Stop watching. Idempotent |
| `PUT /watched/{id}/viewed` | Mark as seen now |

A refusal is problem+json with a `code`: `INVALID_PREFERENCE`, `NO_SUBJECT`, `QUOTA_REACHED`,
`DUPLICATE_NAME`, `NOT_FOUND` or `PREFERENCES_UNAVAILABLE`.

**Watching follows the read rule** (`RequestInvolvement`, through `RequestNoteAccess`): a library
user may watch a request their library borrowed, supplies or is the pickup for, and gets 403 for
any other, existing or not, so ids cannot be probed. Consortium staff may watch any request and get
404 for one that does not exist. The apps load watched requests through their own scoped GraphQL
query, so a request the caller can no longer see simply does not come back.

## Vocabularies

`PreferenceVocabulary` holds every allow-list:

- display values;
- the keys a grid may hold (`filterModel`, `sortModel`, `columnVisibilityModel`, `pageSize`);
- the fields each app's grids may remember a filter or sort on, and the fields each saved filter
  type may use (`admin.patronRequests`, `admin.supplierRequests`, `libraries.borrowing`,
  `libraries.supplying`).

A column added to a grid is not remembered until it is added here. That is the safe direction:
a new column that carries patron data cannot be stored by accident. `patron` is the only patron
field, and its values must be internal patron ids, at most ten. `patronBarcode`, `requesterNote`,
`noteText`, `fullName` and `lastEditedBy` are not in any list, and `quickFilterValues` is refused
anywhere in a document.

## Schema

The `admin_preferences` and `admin_watched_request` migrations create new
tables only. `admin_watched_request` has no foreign key onto `patron_request`, so the migration
takes no lock on it; the daily sweep removes watches whose request has gone.

# Notes and tags on patron requests

Staff at the libraries involved in a patron request can add notes and tags to it. Every note and
tag names the **library** it speaks for, or the consortium, and never the person who added it.

## Who can see a request

A library can see a patron request, its audit trail, notes and tags when it is involved in the
request. A library is involved when it is:

- the borrowing library;
- the library now supplying the item; or
- the pickup library.

Consortium staff (`ADMIN`, `CONSORTIUM_ADMIN`) see every request.

**This is wider than before:** pickup libraries used to see nothing in DCB Admin for Libraries for
a request they were part of. A library whose earlier attempt to supply did not go ahead is not
involved: it never handled the item, so it sees neither the request nor the patron.

Acting on a request's workflow (forcing a clean-up, checking for updates) is unchanged: only the
borrowing library and the current supplier can do it.

## Who can write

| | Notes | Tags |
|---|---|---|
| `LIBRARY_ADMIN` at an involved library | add; retract its own library's | apply and remove its own library's |
| `ADMIN`, `CONSORTIUM_ADMIN` | add as "the consortium"; retract any | apply and remove the consortium's |
| `LIBRARY_READ_ONLY` | read only | read only |

- **Which library a note is written as.** DCB takes the agencies in the user's token that are
  involved in the request. When there is exactly one, that is the library. A user holding several
  involved agencies, such as staff on a shared system, must say which to write as, and can only
  choose among those.
- **Consortium staff always write as the consortium**, even if their account also names a library.
- **Retracting** clears the note's text and shows who removed it ("Removed by \<library\>" or
  "Removed by the consortium"). Any `LIBRARY_ADMIN` of the library that wrote a note can retract
  it, because DCB does not record which person wrote it. Only consortium staff can retract the
  consortium's notes. Notes cannot be edited.

## Tags

Tags come from a list the consortium manages, not free text, so they can be filtered on and do not
collect personal details. Each library applies a tag in its own name, so a tag shows as, for
example, "Damaged: Library A, Library B".

DCB starts with five tags: `DAMAGED`, `MISSING_PIECES`, `PATRON_CONTACTED`, `RETURN_DISPUTED` and
`SLOW_TO_SHIP`. Consortium staff add tags with `createRequestTag` and change or retire them with
`updateRequestTag`. A retired tag (`active: false`) stays on the requests that have it but cannot be
applied again. An update changes what it names: leave `active` or `description` out and the
stored value stays, and send `description: null` to clear it. `deleteRequestTag` deletes a tag only
while no request carries it, so a tag added by mistake can go; once a request carries a tag, retire
it instead. The list holds at most 100 tags.

## Limits

- A note is plain text of up to 2,000 characters. It is shown as text, never as markup.
- A request holds up to 200 notes. They are read newest first, at most 50 at a time.

## Personal data

- DCB stores the library or "the consortium", the text and the time. It does not store the
  username, email or identity-provider subject of the person who wrote a note, or anything derived
  from them.
- **"Not identified" means not identified in DCB.** The identity provider's session log and any
  ingress access log still show which account sent which request, and when. An operator with those
  logs could link a note to a person. A user of the admin apps cannot.
- Notes are visible to every library involved in the request and to consortium staff. Staff should
  write them with that in mind: they will often mention the patron.
- Notes and tags are never shown to patrons or through any discovery route.

## Retention

Once a day DCB deletes the notes and tags of requests that ended (`FINALISED`, `CANCELLED`,
`COMPLETED` or `ARCHIVED`) more than 12 months ago, measured from when the request entered that
status. It also deletes notes and tags whose request no longer exists. A request in `ERROR` is not
treated as ended, because it can still be rolled back into the workflow.

## Hosting

- The tables are `patron_request_note`, `patron_request_tag` and `request_tag`, created by the
  `patron_request_notes_and_tags` migration. They have no foreign key onto `patron_request`, so the migration takes no lock on it.
- `supplier_request` has no index on `patron_request_id`, so finding a request's current supplier
  for notes scans that table, as other lookups of a request's supplier already do. Check the cost
  on a large consortium before relying on it at scale.

## API

GraphQL, on the endpoint both admin apps already use:

- `PatronRequest.notes(pageno, pagesize)`, `PatronRequest.tags` and
  `PatronRequest.involvedAgencyCodes`;
- `addPatronRequestNote`, `retractPatronRequestNote`, `tagPatronRequest`, `untagPatronRequest`;
- `requestTags`, `createRequestTag`, `updateRequestTag`, `deleteRequestTag`.

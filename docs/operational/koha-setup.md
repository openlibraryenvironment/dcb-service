# Setting up a Koha library with OpenRS DCB

For the systems librarian of a Koha library joining an OpenRS consortium, and for the
OpenRS team configuring it in DCB Admin. The settings reference for hosting providers is
[Koha hosting and configuration](koha-hosting.md).

Supported Koha versions: 25.05, 25.11 and 26.05.

## What OpenRS gives a Koha library

- Your patrons search the consortium's shared index and request titles held by other
  members, from your discovery service or the consortium's, signing in with their Koha
  card number and OPAC password.
- A requested item arrives at the branch the patron chose, as a hold on a virtual item
  DCB creates in your Koha. Your staff check it in, check it out and check it in again
  exactly as they would a local hold; the virtual item is removed when the loan is over.
- Other members' patrons can request your items. Their requests arrive as holds for a
  virtual patron at a dedicated branch, which your staff fill from the holds queue.

## What OpenRS asks of your Koha, and why

Everything DCB does in your Koha goes through its REST API as one staff patron whose
permissions are listed in [the hosting guide](koha-hosting.md#access-dcb-needs-and-why).
It reads your catalogue over OAI-PMH, which needs no credentials. It never writes to a
real patron's record, never writes to a real item, and never places a hold a real
patron's own blocks would stop.

## Before you join: a checklist for Koha staff

In this order. Steps 1 to 8 are enough for a library that borrows and lends in the
standard flow; step 9 is only for pickup-anywhere and walk-up when your library is the
borrower.

1. **Version.** 25.05, 25.11 or 26.05.
2. **A branch for the consortium.** Administration, Libraries: a new library such as
   `OPENRS`, flagged as a pickup location, not public, and never one that lends. This is
   the sharing library: where supplier holds are collected for dispatch and where DCB's
   virtual patrons belong. It must be its own branch: Koha puts a filled hold in transit
   only when the pickup branch differs from the branch checking the item in, and DCB
   waits for that transit.
3. **A patron category for visiting patrons.** `DCB`, with an enrolment period long
   enough never to expire a virtual patron, and messaging preferences off; otherwise
   every consortium hold and checkout notice goes to the consortium mailbox.
4. **The DCB API patron and its keys.** A staff patron in the sharing branch with the
   permissions in the hosting guide; `RESTOAuth2ClientCredentials` enabled; keys generated
   under Manage API keys. The secret is shown once. With `IndependentBranches` on, the API
   patron must be allowed to act across branches.
5. **An item type for incoming loans.** `DCB_VIRTUAL`: the type DCB's virtual items carry,
   so you have one circulation rule for consortium borrowing and can hide the items from
   the OPAC with `OpacHiddenItems` (`itype: [DCB_VIRTUAL]`).
6. **Authorised values.** `NOT_LOAN` 42 for copies not shared with the consortium;
   `CCODE` `DCB_NO_RENEW`; `942$x` added to every bibliographic framework in use for
   local-only titles; `942$n` pointed at `YES_NO` for OPAC suppression.
7. **System preferences.** `OAI-PMH` on, `OAI-PMH:archiveID` set before the first harvest,
   `OAI-PMH:DeletedRecord` persistent, `OpacSuppression` on once a record carries
   `942$n`, `ItemsDeniedRenewal` containing `ccode: [DCB_NO_RENEW]`, `ReservesNeedReturns`
   at its default, and transfer limits (if used) allowing transfers to and from the
   sharing branch. The hosting guide says why for each.
8. **Circulation rules.** Sharing branch, `DCB` category, all item types: holds and
   on-shelf holds allowed, the consortium's loan period and renewals, no fines. All
   branches, all categories, `DCB_VIRTUAL`: one hold per record, the consortium's loan
   period, fines as you charge your own patrons for a late item. For your own patrons'
   requests on your own items (below), on-shelf holds allowed for their categories.
9. **For pickup-anywhere or walk-up as borrower:** a bundle host item for DCB (one
   never-lent item of type `DCB_VIRTUAL` on a staff-only record at the sharing branch),
   with `BundleNotLoanValue` set. See [ADR 0002](../ADRs/0002-koha-check-in.md).
10. **Verify from outside** with no credentials: `oai.pl?verb=Identify` and
    `?verb=ListMetadataFormats` (which must list `marcxml`); and with the token,
    `GET /api/v1/libraries?_per_page=1`.

## What your OpenRS team will need

| Value | Where it comes from |
|---|---|
| the REST API address | the hosting guide's two addresses |
| the OPAC address | the same |
| the client id and secret | step 4 |
| the sharing branch code | step 2 |
| the `DCB` category code and the `DCB_VIRTUAL` item type code | steps 3 and 5 |
| every branch code, and which branches may be collected from | Administration, Libraries |
| every patron category code and item type code | Administration, Patron categories and Item types |

## Configuring your library in DCB Admin

### The Host LMS record

Created once by your OpenRS team with the values above; the full key list is in the
hosting guide. The ingest keys (`base-url`, `metadata-prefix`) are warnings rather than
errors at creation, because a member that only borrows contributes nothing to the
shared index.

### Mappings

A Koha library needs three kinds of mapping before it can lend or borrow. All three
are reference value mappings, all three go in through DCB Admin, and none of them
is Koha-specific machinery — what is Koha-specific is *which Koha value* goes in
each one, and getting that wrong produces a mapping nothing ever consults.

| What | Category | Koha value | Direction |
|---|---|---|---|
| Item types | `ItemType` | itemtype code, e.g. `BK` | both ways |
| Patron types | `patronType` | patron category code, e.g. `AD` | both ways |
| Locations | `Location` | **branch** code, e.g. `BRANCH-N` | Koha → agency only |

#### 1. The shape every mapping has to have

Reference value mappings are directional pairs of *context*, *category* and
*value*. `DCBConfigurationService` enforces, and rejects the line if:

- One side's context is the Host LMS code and the other is exactly `DCB`. Both
  sides being the Host LMS, or both `DCB`, is refused.
- The category you chose in DCB Admin matches `fromCategory` or `toCategory`.
- `Location` mappings have `toContext` = `DCB` and `toCategory` = `AGENCY`.
- The `DCB` side of an `ItemType` mapping is one of `CIRC`, `NONCIRC`, `CIRCAV`.
- The `DCB` side of a `patronType` mapping is one of `ADULT`, `CHILD`, `FACULTY`,
  `GRADUATE`, `NOT_ELIGIBLE`, `PATRON`, `POSTDOC`, `SENIOR`, `STAFF`,
  `UNDERGRADUATE`, `YOUNG_ADULT`.

Rejected lines are reported per line as ignored items rather than failing the
upload, so **read the import result** — a file can report success having imported
nothing useful.

**A mapping only works in the direction it is written.** Every lookup is
`findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext`, so
Koha → DCB and DCB → Koha are two separate rows. The `reciprocal` column on the
mapping suggests otherwise and is not consulted by any lookup; it is a note, not
behaviour. Mapping one direction and assuming the other follows is the single
most common way a Koha ends up able to supply but not borrow, or the reverse.

#### 2. Item types

Two separate mappings, because the two directions do different jobs and one does
not imply the other:

- **Koha → DCB** decides what a real Koha item looks like to the consortium.
  `getItems` reads Koha's `effective_item_type_id` (falling back to
  `item_type_id`) and looks up `ItemType`/`<koha code>`/`<itemtype>` →
  `ItemType`/`DCB`. Without it the item is contributed with a canonical type of
  `UNKNOWN_NO_MAPPING_FOUND`, which resolution will not lend.
- **DCB → Koha** decides what item type the *virtual* item gets when this Koha
  borrows. `createItem` refuses outright without it, so a library with only the
  inbound direction mapped can supply but never borrow.

Get the codes from Administration → Item types, or
`GET /api/v1/item_types`.

```csv
fromContext,fromCategory,fromValue,toContext,toCategory,toValue
KOHA-EXAMPLE,ItemType,BK,DCB,ItemType,CIRC
KOHA-EXAMPLE,ItemType,DVD,DCB,ItemType,CIRC
KOHA-EXAMPLE,ItemType,REF,DCB,ItemType,NONCIRC
DCB,ItemType,CIRC,KOHA-EXAMPLE,ItemType,DCB_VIRTUAL
DCB,ItemType,CIRCAV,KOHA-EXAMPLE,ItemType,DCB_VIRTUAL
DCB,ItemType,NONCIRC,KOHA-EXAMPLE,ItemType,DCB_VIRTUAL
```

A dedicated Koha item type for the inbound direction — `DCB_VIRTUAL` above — is
worth creating rather than reusing `BK`. It gives the library one circulation
rule to point at for incoming loans, and makes DCB's items obvious in the staff
interface.

#### 3. Patron types

Also both ways: inbound to decide whether a Koha borrower may request at all,
outbound to pick the category DCB's virtual patron is created under.

**Koha patron categories are short codes, not numbers.** Sierra's patron types
are numeric and are mapped with *numeric range* mappings; a Koha library
following that guidance ends up with numeric range mappings the Koha client never
reads — `findCanonicalPatronType` and `findLocalPatronType` both go to reference
value mappings only. Use `patronType` reference value mappings.

Get the codes from Administration → Patron categories, or
`GET /api/v1/patron_categories`.

A visiting patron whose DCB patron type has no mapping yet is created in the
category named by `virtual-patron-category`, `DCB` when unset. Koha refuses the
patron unless that category exists, so create it or point the setting at one
that does. The Host LMS configuration check reports it as missing when it is not
in Koha's list.

```csv
fromContext,fromCategory,fromValue,toContext,toCategory,toValue
KOHA-EXAMPLE,patronType,AD,DCB,patronType,ADULT
KOHA-EXAMPLE,patronType,YA,DCB,patronType,YOUNG_ADULT
KOHA-EXAMPLE,patronType,CH,DCB,patronType,CHILD
KOHA-EXAMPLE,patronType,ST,DCB,patronType,STAFF
KOHA-EXAMPLE,patronType,B,DCB,patronType,NOT_ELIGIBLE
DCB,patronType,ADULT,KOHA-EXAMPLE,patronType,DCB
```

Map the categories that should *not* borrow to `NOT_ELIGIBLE` rather than
leaving them out. An unmapped category fails the request with "not mapped to a
DCB canonical patron type", which reads like a configuration fault; mapping it to
`NOT_ELIGIBLE` is a deliberate refusal.

As with item types, a Koha category of its own for DCB's virtual patrons —
`DCB` above — keeps consortial borrowers out of the library's own statistics and
gives them their own circulation rules.

#### 4. Locations

This is the mapping that decides **which library owns an item**, and the one most
easily got wrong on a Koha.

The value is the Koha **branch** — `home_library_id`, falling back to
`holding_library_id` for an item away from home. It is *not* Koha's `location`
field. Koha's `location` is a shelving classifier — `REF`, `STACKS`, `JUV` — and
every branch on a shared Koha uses the same set of them, so it cannot identify a
library. DCB carries it separately as the item's shelving location and never maps
agencies from it.

Get the codes from Administration → Libraries, or `GET /api/v1/libraries`.

```csv
fromContext,fromCategory,fromValue,toContext,toCategory,toValue
KOHA-EXAMPLE,Location,BRANCH-N,DCB,AGENCY,example-north
KOHA-EXAMPLE,Location,BRANCH-S,DCB,AGENCY,example-south
```

The same mappings do double duty: they resolve an item's owning agency, and they
resolve a *patron's* agency from `library_id` on their Koha record. So a branch
that lends must be mapped even if no patron belongs to it, and a branch whose
patrons borrow must be mapped even if it lends nothing.

One branch per line. The `*` wildcard that means "every location on this system
belongs to one agency" is ignored on a Host LMS marked as a shared system, which
is what a Koha serving several consortium members is — see
`LocationToAgencyMappingService.lookupCodesFor`. Rely on it there and every
co-tenant library, including ones outside the consortium, collapses onto one
agency.

An unmapped branch leaves the item with no agency rather than guessing, and
raises one accumulating `ILS.<code>.LOCATION_TO_AGENCY_FAILURE.Location` alarm
listing the codes that failed — which is the fastest way to find what a new
Koha still needs mapped.

### Pickup locations

A pickup location is a `Location` row, not a mapping, and it is what a patron
picks in discovery. Add them in DCB Admin under the library, or upload them as a
`Locations` file.

#### What Koha needs in one

| Field | Value |
|---|---|
| `localId` | **The Koha branch code**, e.g. `BRANCH-N` |
| `code` | DCB's own code for the location, free-form |
| `name` / `printLabel` | What the patron sees |
| `type` | `Pickup` — DCB creates nothing else |
| `agencyCode` | The agency this branch belongs to |
| `latitude` / `longitude` | Required, and used for distance-ordered pickup lists |

**`localId` is the whole thing.** It is the only field that carries a Koha branch
code, and it becomes `pickup_library_id` on the hold DCB places. Get it from
Administration → Libraries — the same codes as the location mappings above, and
Koha marks which of them may be collected from with its own `pickup_location`
flag (`GET /api/v1/libraries` returns it). Do not offer a branch Koha says is not
a pickup location.

It is tempting to assume the location's `code` is enough, because it is what the
other kinds of mapping are keyed on. It is not: `pickupLocationCode` on a hold
carries the Location's *UUID* on the borrowing path and the pickup *agency* code
on the supplying and pickup-agency paths, and Koha's `library_id` is at most 10
characters, so neither can name a branch. DCB now refuses to create a Koha pickup
location without a `localId`, and refuses one longer than 10 characters, rather
than accept it and place holds at the wrong branch.

A pickup location whose `localId` is missing on an existing installation sends
the hold to `sharing-library-code` and logs a warning naming the location. The
hold succeeds and the book goes to the wrong branch, so treat that warning as an
error.

#### Uploading them in bulk

The `Locations` file has fixed columns, and `localId` is the **last** one:

```csv
Agency Code,Location Code,Display Name,Print Name,DeliveryStop_Ignore,Lat,Lon,isPickup,LOCTYPE,CHK_Ignore,Address_Ignore,id
example-north,PICKUP-NORTH,North Branch,North Branch,,53.4084,-2.9916,true,Pickup,,,BRANCH-N
example-south,PICKUP-SOUTH,South Branch,South Branch,,53.3900,-2.5900,true,Pickup,,,BRANCH-S
```

Two things about this upload that are not obvious:

- **It replaces, not merges.** `cleanupLocations` deletes every existing location
  for that Host LMS before importing, so the file has to be the complete set.
- Both `Location Code` and `id` must be unique within the file; duplicates are
  reported as ignored lines rather than failing the upload.

#### Verifying it end to end

The mappings and the pickup location are only proven together, by a request:

1. Availability for a clustered bib shows the Koha items → the `Location`
   mappings resolve, and the items carry a canonical item type → the inbound
   `ItemType` mappings resolve.
2. A Koha patron passes validation → the inbound `patronType` mapping resolves,
   and their `library_id` maps to an agency.
3. Place a request with the Koha branch as pickup. In Koha, the hold appears at
   that branch, not at `sharing-library-code` — the check that the `localId`
   actually arrived.
4. Where the Koha is borrowing, a virtual item appears under the DCB item type →
   the outbound `ItemType` mapping resolves, and a virtual patron under the DCB
   category at the sharing library → the outbound `patronType` mapping resolves.

`KohaMappingAndPickupLocationTests` covers all of the above against a stand-in
Koha, so a change that breaks one of these lookups fails there first.

### Your agency

The agency's auth profile is one of `BASIC/BARCODE+PASSWORD` (the patron's card number
and OPAC password, which Koha validates; the default for a Koha library),
`BASIC/BARCODE+PIN` (the same, for a consortium that calls it a PIN) or
`BASIC/BARCODE+NAME` (card number and surname, for patrons who sign in to Koha only
through single sign-on and have no password). Mark the agency as supplying, borrowing or
both.

## Before your patrons can borrow

Run the Host LMS configuration check in DCB Admin: it lists your item types, patron
categories and branches, and confirms the sharing branch, the virtual item branch and
the `DCB` category exist. Then verify end to end as the pickup locations section above
describes.

What DCB checks about a patron before it asks any supplier: that Koha knows them, that
their category is mapped and not mapped as ineligible, that they are not restricted and
their card is not lost, and that their account has not expired. Debt is not checked by
DCB: Koha refuses the hold itself and the patron is told why.

## Workflows

### When your library is the borrowing library

1. The patron requests a title. DCB creates a suppressed virtual record and a virtual
   item of type `DCB_VIRTUAL` at the patron's home branch, with the supplier's barcode,
   and places a hold on it for the patron at the branch they chose.
2. The item arrives. Staff check it in at the pickup branch; Koha traps the hold and
   puts it on the shelf; DCB sees the waiting hold and tells the supplier.
3. Staff check the item out to the patron. DCB sees the loan and checks the real item
   out at the supplier. Renewals the patron makes are reflected there; if the owning
   library needs the item back, DCB marks the virtual item with the `DCB_NO_RENEW`
   collection code and Koha refuses further renewals.
4. The patron returns the item. Staff check it in; DCB sees the item available again,
   tells the supplier it is on its way back, and once the supplier has it, deletes the
   hold, the virtual item and the virtual record.

### When a patron requests your own library's item

With the consortium's own-library borrowing setting on, a patron may request a title
your own library holds, or one held by another library on the same Koha. DCB places one
hold on the real item for the real patron at the branch they chose, with no virtual
record, no virtual item and no override of the patron's blocks, confirms Koha accepted
it, and hands the request off: from then on it is an ordinary local hold that Koha and
your staff manage, and DCB tracks nothing. A request for an available copy is an
on-shelf hold, so the circulation rule for the patron's category and the item's type must
allow on-shelf holds, or Koha refuses it with "Hold not allowed" and the patron is told.

### When your library is the lending library

A hold for a virtual patron appears in your holds queue with the sharing branch as its
pickup branch. Staff pull the item and check it in; Koha puts it in transit to the sharing
branch, and DCB tells the borrower it is coming. When the borrower lends it, DCB checks
it out to the virtual patron. When it comes back, staff check it in; Koha closes the loan
and sends the item home.

## Virtual patron notices

The member system sends a virtual patron hold and pickup notices like any other
patron. Set `virtual-patron-email` to a mailbox the consortium reads, and the
notices of every virtual patron created after that go there; one created before
keeps the address it was created with. Without it they have no address, and a
value that is not an address is not sent. It must be an operational
address, never a patron's own: every virtual patron on this system shares it.

## Stopping renewals

When the owning library places a hold on an item DCB has lent, DCB sets the
item's collection code to `no-renew-collection-code` (default `DCB_NO_RENEW`).
Koha's `ItemsDeniedRenewal` preference must name that code. DCB also adds
`do-not-renew-note` to the item's internal notes, so staff know why. The default
is "Hold placed by owning library. Do not renew." Set it in the language this
library's staff work in.

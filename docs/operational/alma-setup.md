# Setting up an Alma institution with OpenRS DCB

A guide for consortium staff and Alma institutions joining an OpenRS resource-sharing
network. It covers:

- what to configure in Alma;
- what your OpenRS implementation team needs from you;
- what you configure yourself in DCB Admin;
- how the day-to-day workflows run.

Related pages:

- [Alma hosting and configuration reference](alma-hosting.md): every Host LMS setting,
  network access, load on your Alma, and troubleshooting by error code. For hosting
  providers and whoever administers DCB.
- [Moving a member library to Alma](alma-migration.md): for a library already in the network
  on another system.
- [Alma integration](../dev/alma-integration.md): how the adapter works, for developers.

## What OpenRS gives an Alma library

OpenRS DCB brokers lending between libraries running different library systems. Your patrons
request items held anywhere in the consortium and collect them from a library that suits them.
Your holdings become requestable by every other member.

Alma remains your system of record throughout. DCB creates the temporary records a loan
between institutions needs, and removes them when the transaction completes.

<a id="what-we-ask"></a>
## What OpenRS asks of your Alma, and why

| What | Detail | Why |
|---|---|---|
| **API key** | One for your sandbox, a separate one for production | |
| Users: read/write | Users and user requests | To check a library-account password, look up patrons, create and update the accounts OpenRS lends to on behalf of visiting patrons, place, read and cancel holds, and check items out and renew them. |
| Bibs: read/write | Bibliographic records, holdings, items and item requests | To read your items and their requests for live availability, find an item by barcode, create and delete the temporary record, holding and item when your patrons borrow, and scan items in. |
| Configuration: read-only | Libraries, locations, circulation desks, code tables | To look up the virtual item location each time a stand-in item is created, and for the configuration report and mapping audit, which check what OpenRS holds against your Alma. OpenRS creates nothing in your configuration. |
| Every other area | Acquisitions, Analytics, Electronic, Courses, Partners, Task lists | Not used. Leave them off the key. |
| Allowed IP range | The address of the server running OpenRS | So a copied key is useless anywhere else. |
| **In your Alma** | | |
| A sharing library or desk | An OpenRS library, or an OpenRS circulation desk with a hold shelf: see [A library to lend from](#lending-library) | Every lending hold is placed for pickup there. Scanning the item in anywhere else puts it in transit, which is how OpenRS knows it is on its way. |
| A user group for visiting patrons | Usually your consortial borrower group, with a role assignment rule that gives its users the Patron role | OpenRS creates an account in it for each patron you lend to. Its Request rules decide whether the hold can be placed. |
| Fulfilment rules | Request rules that allow that group to place holds with pickup at the sharing library or desk, and on the virtual item location with pickup at each of your pickup libraries; and your own patrons' groups to place holds on the virtual item location with pickup at your pickup libraries and at the sharing library: see [Collecting from a different member](#collect-elsewhere) | Alma refuses a hold its rules do not allow, with error `401129`, even when the item is on the shelf. |
| A virtual item library and location | Where OpenRS creates the stand-in item when your patron borrows: see [Before your patrons can borrow](#borrowing) | The hold for your patron is placed on that item. |
| Two item policies | One for stand-in items (default `BOOK`); one that a loan rule refuses to renew (default `DCB_NO_RENEW`): see [Preventing renewal](#preventing-renewal) | Your loan rules read the first. The second is how OpenRS stops a loan being renewed when another library wants the book. |
| A request cancellation reason | Ideally one of the spare codes in your RequestCancellationReasons table, enabled and renamed for OpenRS: see [The request cancellation reason](#cancellation-reason) | Alma requires one whenever OpenRS cancels its own hold, and it is what your staff see in the request's history. |
| Circulation desks for OpenRS to act at | A desk to scan in at and one to check out at, which can be the same; default `DEFAULT_CIRC_DESK` | OpenRS scans items in, checks a lent item out to the visiting patron, and checks items out to your own patrons on walk-up and collect-elsewhere requests. |
| A user identifier type | Default `INST_ID` | OpenRS tags each visiting patron's account with it. |
| An OAI-PMH integration profile and a publishing profile | The set of records you share, your OAI institution code and your Alma domain | OpenRS harvests your records into the shared catalogue. |
| A staff user for troubleshooting | See [An Alma staff user](#staff-user) | So the OpenRS team can see what Alma did with a request. |
| **From your patrons and records** | | |
| Internal passwords | For patrons who sign in with a library account | OpenRS checks the password against Alma's own identity service. |
| `campus_code` on user records | Only where several members share one Alma: see [Consortia sharing one Alma tenant](#shared-tenant) | It is how OpenRS tells which member a patron belongs to. |
| Item barcodes that do not clash | Checked against the barcode ranges of the libraries you borrow from | A stand-in item carries the lending library's barcode, and Alma allows each barcode on one item only. |

<a id="onboarding-checklist"></a>
## Before you join: a checklist for Alma staff

Work through this in your sandbox first, then in production.

1. **Decide how you will lend.** Create an OpenRS library, or an OpenRS circulation desk with a
   hold shelf in an existing library ([A library to lend from](#lending-library)). Never the
   Resource Sharing Library.
2. **Choose the visiting-patron user group,** and make sure a role assignment rule gives its
   users the Patron role.
3. **Set the Request rules** that let that group place holds for pickup at your OpenRS library
   or desk. If other members' patrons may collect from you, also allow holds on the virtual item
   location for pickup at each of your pickup libraries ([Collecting from a different
   member](#collect-elsewhere)).
4. **For borrowing,** confirm the virtual item library and location exist. Their Request rules
   must let your own patrons' groups place holds with pickup at every library you will offer as
   a pickup point, and at the sharing library if your patrons may collect at another member
   ([Before your patrons can borrow](#borrowing)).
5. **Create the two item policies,** and a loan rule that refuses renewal for the second
   ([Preventing renewal](#preventing-renewal)).
6. **Enable a request cancellation reason** for OpenRS ([The request cancellation
   reason](#cancellation-reason)).
7. **Check the circulation desks** OpenRS will scan in and check out at exist in the libraries
   it will act in.
8. **Set up OAI-PMH:** an integration profile and a publishing profile defining the set to
   share.
9. **Issue the API keys:** Users and Bibs read/write, Configuration read-only, restricted to the
   OpenRS server's address. One for the sandbox and one for production.
10. **Whitelist the OpenRS server** for both the REST API and OAI-PMH, if your Alma restricts
    either ([IP whitelisting](#ip-whitelisting)).
11. **Create the troubleshooting staff user** ([An Alma staff user](#staff-user)).
12. **Compare barcode ranges** with the other members, and agree as a consortium whether
    visiting patrons' accounts carry a prefix.
13. **Give your OpenRS team the values** in [Configuration values](#configuration-values).
14. **When they have configured you,** run both reports in [Checking the
    configuration](#checking). Every row should read `PRESENT` and no mapping `MISSING`. Then,
    on the sandbox, lend one item and borrow one, end to end: the lent item must go into
    transit when scanned in.
15. **Only then turn on lending and borrowing** for your agency ([Your agency](#your-agency)).

## Getting started in Alma

These steps happen in Alma first.

<a id="lending-library"></a>
### A library to lend from

When another member borrows from you, OpenRS places a hold in Alma for pickup at one library,
the **sharing library**. Staff scan the item in at their own desk, Alma puts it in transit to
the sharing library, and that transit is what tells OpenRS the item is on its way. The item
then goes to the borrowing library, not to the sharing library.

The sharing library must meet two conditions:

- **Alma can deliver a hold to it.** It has a circulation desk with a hold shelf that serves as
  a pickup point for the fulfilment units your lendable items belong to.
- **It holds none of the items you lend.** Alma decides what a scan-in does from where the scan
  happens. An item scanned in at the hold's pickup library goes onto that library's hold shelf,
  not into transit, and OpenRS then waits for a transit that never comes. So the sharing
  library must not be a library whose own items you lend. That rules out the obvious choice,
  the library whose staff dispatch OpenRS items.

Set up one of these two:

1. **An OpenRS library.** A library created in Alma for OpenRS lending, owning no items, with a
   circulation desk that has a hold shelf and is a pickup point for every fulfilment unit whose
   items you lend. Set `sharing-library-code` to it. This meets both conditions for every item.
2. **An OpenRS circulation desk.** A desk with a hold shelf, inside one of your existing
   libraries, that nobody scans items in at. A Fulfillment Administrator can add one without
   creating a library. Set `sharing-library-code` to the library it belongs to and
   `sharing-circ-desk-code` to the desk. OpenRS then sends every lending hold to that desk, so
   a scan at any other desk, even in the same library, puts the item in transit. Try one hold
   on your sandbox first: scan the item in at another desk in that library and check that Alma
   reports it in transit rather than on the hold shelf.

If you have neither yet and your sharing library lends items of its own, set
`alternative-sharing-library-code` to a second library that is also a pickup point for those
items. OpenRS then sends holds for the sharing library's own items there, so that scanning them
in puts them in transit. Alma shows such an item as in transit to that second library, which
never receives it, until OpenRS records the loan; tell that library's staff to expect it.
Without either, staff must scan in the sharing library's own items with their Alma location set
to a different library.

> **Important:** Do **not** use Alma's Resource Sharing Library, often coded `RES_SHARE`. It
> exists for Alma's own resource-sharing workflow, its only locations are internal ones such as
> `IN_RS_REQ` and `OUT_RS_REQ`, and Alma cannot deliver a patron hold to it. Every lending
> request then fails with Alma error `401129`, "No items can fulfill the submitted request",
> even though the item is on the shelf. The configuration report in [Checking the
> configuration](#checking) flags it. There is no default sharing library: DCB Admin will not
> save an Alma Host LMS without one.

<a id="ip-whitelisting"></a>
### IP whitelisting

If your Alma restricts access by IP address, whitelist the address of the server running
OpenRS DCB, in two places:

- **The REST API**, through the API key's allowed IP range.
- **OAI-PMH**, which DCB uses to harvest your records. It is controlled separately from the
  REST API, so whitelisting one does not cover the other, and without the harvest none of your
  items can be lent.

If you do not know the address, ask the OpenRS team or your hosting provider.

## What your OpenRS team will need

Provide the following to your OpenRS implementation team.

### An Alma API key

With **Users** and **Bibs** read/write and **Configuration** read-only, and nothing else, and
its allowed IP range set to the OpenRS server. Issue separate keys for your sandbox and for
production, so a test can never write to production. See [What OpenRS asks of your
Alma](#what-we-ask) for what each area is used for.

<a id="staff-user"></a>
### An Alma staff user for troubleshooting

With permissions for finding and managing users, circulation, fulfilment, and virtual bibs,
items and holdings. We believe this corresponds to these
[user roles](https://knowledge.exlibrisgroup.com/Alma/Product_Documentation/010Alma_Online_Help_(English)/050Administration/030User_Management/060Managing_User_Roles):

- Fulfilment Administrator
- User Manager
- Circulation Desk Manager
- Repository Manager
- Catalog Manager

Granting these in full gives the most help with troubleshooting. If that is not possible,
read-only access, except for User Manager, still lets us tell you what needs changing in Alma,
or what in Alma is causing a DCB issue.

<a id="configuration-values"></a>
### Configuration values

The Host LMS setting each value becomes is in brackets. The full list, with defaults, is in the
[hosting and configuration reference](alma-hosting.md#settings).

| Value | Notes |
|---|---|
| Regional Alma API URL (`alma-url`) | Your region's API gateway, such as `https://api-na.hosted.exlibrisgroup.com`: see "Calling Alma APIs" in the Ex Libris [developer documentation](https://developers.exlibrisgroup.com/alma/apis/). **Not** your institution's own Alma address: API calls sent there are refused with an HTML "401 Unauthorized" page, whatever the key. Every Alma institution in a region shares this URL, and DCB uses it to tell one system from another. Where two Alma institutions in the consortium use the same regional URL, give each a different base URL qualifier (`base-url-qualifier`), such as its institution code. Without it DCB treats the two as one system and neither can supply the other's patrons. |
| Your Alma domain (`base-url`) | Your institution's own address, such as `https://yourinstitution.alma.exlibrisgroup.com`. Used only to harvest your records over OAI-PMH. |
| OAI-PMH institution code and set (`institution-code`, `oai-set`) | The set you wish to make available to OpenRS. The OAI institution code is not always the same string as your Alma institution code. |
| Sharing library code (`sharing-library-code`) | The Alma library that lending holds are sent to. A pickup location that holds none of the items you lend, and never the Resource Sharing Library: see [A library to lend from](#lending-library). |
| Sharing circulation desk, optional (`sharing-circ-desk-code`) | A desk in the sharing library, with a hold shelf, that lending holds are sent to instead of the library as a whole. With a desk, the alternative sharing library is not needed. |
| Alternative sharing library, optional (`alternative-sharing-library-code`) | A second pickup library, used only for items the sharing library owns itself, when no sharing desk is set. Needed only when the sharing library lends items of its own. |
| Virtual item library and location (`virtual-item-library-code`, `virtual-item-location-code`) | The library and location where DCB creates the stand-in item your patron's hold is placed on when you borrow. Both must already exist in Alma: see [Before your patrons can borrow](#borrowing). |
| Circulation desks (`default-circ-desk-code`, `pickup-circ-desk`) | The desk OpenRS scans items in at, and the desk it checks items out at, whether to a visiting patron or your own. Both default to `DEFAULT_CIRC_DESK`, and must exist in each library OpenRS acts in. |
| Item policies (`item-policy`, `no-renew-item-policy`) | The policy for stand-in items (default `BOOK`) and the one that denies renewal (default `DCB_NO_RENEW`): see [Preventing renewal](#preventing-renewal). |
| User identifier type (`user-identifier`) | The identifier type OpenRS tags visiting patrons with. Default `INST_ID`. |
| Request cancellation reason (`request-cancellation-reason`) | **Required.** DCB Admin refuses to save a change to an Alma Host LMS's settings without it, and there is no default. A code from your RequestCancellationReasons code table: see [The request cancellation reason](#cancellation-reason). |
| Virtual patron barcode prefix, optional (`virtual-patron-barcode-prefix`) | Empty by default, so nothing changes at your desk unless you choose it: see below. |

**The virtual patron barcode prefix.** A visiting patron's virtual account carries their own
card barcode, which is what your staff scan when the patron collects an item here or borrows
one on the spot.

Setting a prefix such as `OpenRS-` is a reasonable choice, and some consortia will want it.
Alma will not let two users share an identifier, so where your barcodes overlap another
member's, a visiting patron cannot be created at all and you cannot lend to them. A prefix
removes that, at the cost of the direct scan: staff type the prefix before scanning the card,
or search for it. Agree it consortium-wide, tell desk staff what to expect, and try it at a desk
before rolling it out.

This lets the OpenRS team set up your institution's basic configuration in DCB. Consortium
staff then add the rest through DCB Admin.

<a id="cancellation-reason"></a>
#### The request cancellation reason

OpenRS cancels a hold it placed in your Alma whenever a request ends early: the patron cancels
it, another library supplies instead, or the request is cleaned up. Alma will not cancel a
request without a reason from your **RequestCancellationReasons** code table, and OpenRS sends
the one you name here on every cancellation. It asks Alma not to notify the patron.

The reason is what your staff see in the request's history, so make it say where the
cancellation came from:

1. In Alma's fulfilment configuration, open the **Request Cancellation Reasons** code table.
2. Enable one of the spare codes, `AdditionalReason01` to `AdditionalReason10`, and set its
   description to something like "Cancelled by OpenRS".
3. Give that code, not its description, to your OpenRS team.

If you would rather not add one, choose an enabled code that reads sensibly for every case
above, such as `CannotBeFulfilled`. Avoid codes that name a specific cause, such as
`CancelledAtPatronRequest`, which would be wrong whenever OpenRS cancels for another reason.
The code must be enabled in your table; the configuration report does not check it.

## Configuring your institution in DCB Admin

### Mappings

Visit the **Mappings** section for your library.

**Item types.** Provide the item types (Alma physical material types) you use, mapped to
either `CIRC` (available for request through OpenRS) or `NONCIRC`.

**Patron types.** Map each Alma user group **code** your patrons can be in to a DCB patron
type: `UNDERGRADUATE`, `GRADUATE`, `FACULTY`, `STAFF`, `PATRON` and the others DCB Admin offers,
or `NOT_ELIGIBLE` for a group that may not borrow through OpenRS. A patron in a group with no
mapping cannot be verified and cannot borrow. Map only groups that exist in your Alma: rows
copied from another library's system match nobody.

**Visiting patrons.** When you lend, OpenRS creates an Alma user for the borrowing patron. Map
each DCB patron type to the Alma user group that user should belong to: usually the group you
already use for consortial borrowers. That group's Request rules decide whether the hold can
be placed at all, so do not map visiting patrons into a local group such as your own students.

> **Tip:** Use the **codes**, not the names. A mapping to a user group's description, such as
> `Consortial Express Patron` where the code is `CONSORTIAL`, is accepted by DCB Admin and fails
> in Alma. If you cannot find the codes in the Alma UI, they are available from
> `/code-tables/UserGroups` in the Alma API, and the mapping audit in [Checking the
> configuration](#checking) reports any that Alma does not recognise.

**Locations.** Map each Alma **library** code (`MAIN`, `LAW` and so on) to your agency.
OpenRS places an Alma item at its owning library, not its shelving location, so a mapping keyed
on a shelving location never matches. Every library holding items you want to be requestable
needs a mapping. Mappings made before DCB 9.0.0 were keyed on shelving locations and stopped
matching on upgrade. Replace them with library codes, and delete the old rows so nobody mistakes
them for working ones. Leave the Resource Sharing Library unmapped: its items are Alma's own
resource-sharing records, not copies to lend.

These mappings say which agency **owns** an item. They are separate from the pickup locations
below, which say where patrons **collect**: a library can hold lendable items without being a
pickup point, and the reverse.

You can use the "New Mapping" button or the upload feature, which accepts TSV and CSV. See the
[import user guide](https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/3201400850/Importing+mappings+in+DCB+Admin).

<a id="pickup-locations"></a>
### Pickup locations

Visit the **Locations** section and add one pickup location for each Alma library your
patrons, and visiting patrons, may collect from. You can bulk-import or add them individually.

| Field | What to put |
|---|---|
| Local ID | The Alma **library** code, exactly as Alma has it: `MAIN`, not `Main Library`. This is the only field OpenRS sends to Alma: every hold for pickup there is placed with that library as its pickup location. Never a circulation desk code, a shelving location or the Resource Sharing Library. |
| Code and name | What patrons and staff see when choosing where to collect. The code need not match anything in Alma, but using the library code keeps the two easy to compare. |
| Agency | Your agency. |

Each pickup library needs a circulation desk with a hold shelf, which is where Alma shelves the
item when it arrives. It must also be allowed as a pickup location by the Request rules
described in [Before your patrons can borrow](#borrowing). A library that is not added here
cannot be chosen, however well it is set up in Alma.

It is fine for a pickup library to be the virtual item library too. When the lending library
ships, OpenRS leaves a stand-in item alone if its hold is already for pickup at its own library,
so it goes onto the hold shelf only when staff scan in the real book.

A library code that does not exist, or the Resource Sharing Library, is accepted here and
refused by Alma when the first hold is placed. Neither report in [Checking the
configuration](#checking) reads pickup locations, so compare them by hand: the configuration
report lists your Alma libraries under **Libraries**, and every pickup location's local ID must
be one of those codes.

<a id="your-agency"></a>
### Your agency

The OpenRS team creates your agency, the record that stands for your library in DCB, with the
authentication profile `BASIC/BARCODE+PASSWORD`, the only one DCB supports for Alma.

Lending and borrowing are separate switches on the agency: supplying and borrowing. DCB treats
a switch that was never set as off. An agency lends only once someone has turned supplying on,
and its patrons can request only once borrowing is on.

DCB Admin's new-library form starts both switches on, so turn them off when the library is
created, and on again after the end-to-end test in the [checklist](#onboarding-checklist)
passes. A switch left on during setup offers your items to the whole consortium before your
Alma is ready.

### Information about your institution

On the **Libraries** page, add your full name, opening hours, primary contacts, and latitude
and longitude.

<a id="borrowing"></a>
## Before your patrons can borrow

Lending works as soon as the sharing library and mappings are right. Borrowing needs more,
because OpenRS then works inside your own Alma: it creates a stand-in item for the book on its
way to you, and places your patron's hold on it.

1. **The virtual item library and location exist.** Create the location first if you need a new
   one. A location code that Alma does not have stops every borrowing request at the point
   OpenRS creates the item.
2. **That location's fulfilment unit lets your patrons request, with pickup at your pickup
   libraries.** The hold is placed for your own patron, in their own user group, on an item in
   that location. Check the fulfilment unit's Request rules for each group that can borrow
   through OpenRS, and that every library you offer as a pickup location is allowed. Only Alma
   staff can confirm this: no Alma API reports which pickup locations a fulfilment unit accepts.
3. **The item policy exists,** the configured default being `BOOK`, and suits a loan from
   another library, since it is what your loan rules see.
4. **Your item barcodes do not overlap those of the libraries you borrow from.** The stand-in
   item carries the lending library's barcode, because that is the label on the book your staff
   will scan. Alma allows a barcode on only one item, so if one of your own items already has
   it, the request stops before anything is created and the audit trail says the barcodes
   clash. That is for the consortium to resolve, by barcode ranges or a member prefix on the
   labels themselves.
5. **Renewal can be stopped.** See [Preventing renewal](#preventing-renewal).
6. **Your patrons can sign in.** OpenRS checks a library-account password against Alma, so a
   patron who signs in with a library account needs an internal Alma password held by the Ex
   Libris Identity Service. A patron who signs in through your institution's identity provider
   can place requests once that provider releases the value Alma matches users on, which your
   OpenRS team sets up with you.

The bibliographic record OpenRS creates is suppressed from publishing and carries a 500 note,
"Temporary record created by OpenRS DCB for resource sharing". When the request ends, OpenRS
deletes it only while it is still suppressed, still carries that note, and has no holdings but
its own. If one of your processes has merged, overlaid or published it, OpenRS leaves it in
place and records why on the request's audit trail, so that nothing of yours is deleted. Keep
import profiles and merge routines from matching on that note.

<a id="checking"></a>
### Checking the configuration

Two reports ask your Alma directly whether what DCB holds exists there. Both need an
administrator or interop-tester login.

- **`GET /imps/configuration/{host LMS code}`** checks the sharing library, the sharing desk
  and alternative sharing library when set, the virtual item library and location, and the item
  policies. Every row should read `PRESENT`. A sharing or alternative library reads `MISSING` if
  it is a resource-sharing library, and a sharing desk if it has no hold shelf, with the reason.
- **`GET /imps/configuration/{host LMS code}/mappings`** checks your saved mappings against the
  codes Alma holds. It should report no `MISSING` rows.

Neither can see fulfilment unit rules, so step 2 of [Before your patrons can borrow](#borrowing)
still needs someone in Alma.

The mapping audit checks both directions:

- Rows marked `SENT_TO_SYSTEM` are values OpenRS sends to Alma: the user group for visiting
  patrons and the item type for stand-in items.
- Rows marked `READ_FROM_SYSTEM` are values OpenRS reads from Alma: your Location mappings,
  checked against your library codes, and the patron-type mappings from your user groups. A
  Location mapping keyed on a shelving location such as `STACKS` reads `MISSING`: items arrive
  with their owning library, so that mapping never matches and those items reach no agency.

Your item-type mappings and your pickup locations are not checked; the audit's `notChecked`
list names what it skipped.

## Workflows

### When Alma is the borrowing institution

1. An Alma patron places a request through an OpenRS-compatible discovery service.
2. DCB places a hold at the supplying library and creates a virtual patron representing your
   patron there. At your library, DCB creates a virtual item representing the real one, and
   places a hold on it for your patron.
3. The supplier puts their item in transit; the DCB request moves to `PICKUP_TRANSIT`.
4. When the real item arrives, **scan it in in Alma at the patron's pickup library.** The hold
   moves to the hold shelf.
5. Check the item out to the patron in Alma. A loan appears under Loans and the hold closes.
6. When the patron returns the item, scan it in again. The request moves to `RETURN_TRANSIT`.
7. Once the supplier checks the item in, the request is `FINALISED` and the virtual bib,
   holding and item are deleted. The virtual patron accounts are kept.

<a id="collect-elsewhere"></a>
### When a patron collects from a different member

A patron may collect from a member other than their own. An Alma institution can take part in
two ways.

**Your patron collects at another member.** DCB creates the stand-in item as usual, and places
your patron's hold on it for pickup at the sharing library, because the item never comes to
you. So your own patrons' user groups need a Request rule allowing pickup at the sharing
library. The request shows in the patron's Alma account throughout.

When they collect the book at the other library, OpenRS checks the stand-in item out to them in
Alma, at `pickup-circ-desk` in the virtual item library, so the loan appears on their account.
That desk must serve the virtual item library, or Alma refuses with `401153`. If the checkout
fails, the request is flagged for attention rather than stopped, since the patron already has
the book.

**Another member's patron collects from you.** DCB creates a visiting-patron account and a
stand-in item in your virtual item location, and places a hold on it for pickup at the library
the patron chose. So the visiting-patron group needs a Request rule allowing that. When the
book arrives, scan it in at that library; it goes onto the hold shelf. Check it out to the
visiting patron at the desk, and scan it in again when they return it.

A patron collecting at another library of their own Alma is an ordinary borrowing request, and
none of this applies.

Where several members share one Alma, two cases cannot run yet:

- the book and the patron are in that Alma, collecting at a member outside it;
- the book and the collecting library are in that Alma, with the patron outside it.

Either would put a second item with the book's barcode into the Alma that holds the book, so
DCB stops the request before creating anything.

### When Alma is the lending institution

1. A patron at another member library places a request through the discovery service.
2. DCB places a hold at your institution, for pickup at the sharing library or desk, and creates
   a virtual patron.
3. Scan the item in, in Alma, at a desk outside the sharing library, to put it in transit. If
   Alma places it on the hold shelf instead, the scan happened at the sharing library: see
   [A library to lend from](#lending-library).
4. When the borrower's library checks the item out to its patron, OpenRS checks it out to the
   virtual patron in your Alma, at `pickup-circ-desk`.
5. When the borrower returns it, the request shows `RETURN_TRANSIT` in DCB Admin.
6. Scan the item in again to complete the request.

## Live availability

OpenRS shows real-time item status across the consortium. Alma's base status only says whether
an item is in place; its process type says why one is not. OpenRS reads both:

| Alma | Shown as |
|---|---|
| In place, no process type | `AVAILABLE` |
| Loan (`LOAN`) | `CHECKED_OUT`, with the loan's due date |
| Requested (`REQUESTED`) while still in place | `AVAILABLE` |
| Any other process type: transit, hold shelf, in transit to remote storage, resource sharing, missing, lost, claimed returned, acquisition, work order, technical | `UNAVAILABLE` |
| Not in place, with no process type | `UNAVAILABLE` |

A copy on loan can be supplied only where the consortium enables `SELECT_UNAVAILABLE_ITEMS`;
the hold then waits in Alma until the copy comes back. A copy in transit is shown as
unavailable, because Alma uses the same code for an item going home and one on its way to fill
another patron's hold. An item lent through Alma's own resource sharing is unavailable too.

The raw Alma values are kept with each item, as `baseStatus` and `processType`, in the live
availability response and in the resolution audit. A process type OpenRS does not recognise is
treated as unavailable and named in the item's decision log.

Live availability also reports how many requests each item has in Alma.

> **Note:** OpenRS does not yet read an item's loan policy. A copy whose policy keeps it in the
> building, such as reference, still reads as requestable if its material type is mapped to
> `CIRC`. Until that changes, map the material types of non-lending copies to `NONCIRC`, or
> keep them out of the OAI set.

<a id="primo"></a>
## Linking to OpenRS from Primo

A patron searching Primo finds nothing at your library, but the consortium holds it. You can
put a link on the Alma services page that carries them straight into a pre-filled OpenRS
search, where they can request it.

Alma already has a slot for this. Broker-based resource sharing defines a **Submit Request**
integration point, and systems such as Tipasa and ReShare occupy it as a **General Electronic
Service**: a link that appears in Primo's "How to get it" area, built from the record's OpenURL
metadata. OpenRS uses the same slot.

### The link

In Alma, go to **Configuration Menu > Fulfillment > Discovery Interface Display Logic >
General Electronic Services** and add a service. This needs the General System Administrator or
Fulfillment Administrator role.

| Field | Value |
|---|---|
| URL template | `https://{your-openrs-discovery-host}/?q={rft.normalized_isbn}&field=isbn` |
| Display in | **Getit & How to Getit** for Primo VE. |
| Public note | What the patron reads under the link, for example "Request from another OpenRS library". |
| Availability rules | Show it only on records with an ISBN and, if you want it only where it helps, only when there is no available local inventory. |

`{rft.normalized_isbn}` sends the ISBN as digits only, so nothing in the link needs escaping.
Nothing needs building on the OpenRS side: the search route accepts these parameters as they
are.

> **Note:** A General Electronic Service shows to everyone using that Primo view. When you set
> one up on a sandbox for a demonstration, a link to a discovery service only you can reach is a
> dead link for everyone else, so label it and remove it afterwards.

### The parameters

| Parameter | Meaning |
|---|---|
| `q` | What to search for. An ISBN for a monograph; an ISSN works the same way. |
| `field` | Which field to search: `isbn`, `issn`, `title`, `author`, `subject`, `publisher`, `publicationYear`, `format` or `keyword`. An unrecognised value falls back to a keyword search rather than failing, so a mistyped template still finds the book, less precisely. |
| `library` | **Optional, and usually wrong here.** It restricts results to one library's holdings. See below. |

> **Important:** Do **not** put your own library's code in `library`. It filters results *to*
> that library, so the patron would get the same empty result Primo just gave them. A
> consortium-wide search, which is what "we do not have it, but somebody does" means, is what
> you get by leaving `library` out entirely. Use it only to point at one named partner library.

There is currently no way to say "everywhere except my library": `library` includes, it cannot
exclude. The consortium-wide search includes your own holdings, which is usually what a patron
wants to see anyway.

### What the patron sees

Results carry an obtainability label computed for your deployment, so a record held elsewhere
in the consortium reads **Request to borrow** without any further lookup. Signing in is needed
to place the request, not to search: a patron arriving from Primo can look before they
authenticate.

Placing the request makes your library the borrower, so everything in [Before your patrons can
borrow](#borrowing) has to be in place first. Without it the link works and the request fails.

This link resolves by searching. A dedicated OpenURL resolver endpoint is planned, which will
accept full OpenURL 1.0 and 0.1 requests and resolve DOIs and PMIDs as well as ISBNs. The link
above needs no change when it arrives.

<a id="shared-tenant"></a>
## Consortia sharing one Alma tenant

Where several member libraries share a single Alma tenant, such as a Network Zone or one
institution whose campuses join as separate members, the Host LMS is configured as a shared
system (`shared-system: true`). Three things follow:

1. **Populate `campus_code` on user records.** This is how DCB tells which member library a
   patron belongs to. Where it is empty, that patron resolves to no agency and cannot borrow.
2. **Map each campus code to its agency** through Location mappings.
3. **Leave the default agency unset.** A shared system cannot have a fallback agency; each
   library's locations must map explicitly. The configuration validator rejects the combination
   rather than guessing. A `*` wildcard Location mapping is ignored on a shared system for the
   same reason.

> **Note:** Virtual items are still created at one configured library for the whole tenant,
> rather than at each borrowing patron's own library. This is a known limitation.

<a id="preventing-renewal"></a>
## Preventing renewal

When another library requests an item one of your patrons has on loan, OpenRS asks your system
to stop that loan being renewed.

Alma exposes no writable renewal flag on a loan (the only field a loan update can change is the
due date), so OpenRS denies renewal on the **item** instead, as it does for Sierra and Koha. It
sets the virtual item's item policy to a code you nominate, and your own loan rules do the rest.

Two things are needed from you:

1. An **item policy** for OpenRS to use. The default code is `DCB_NO_RENEW`; if you use a
   different one, set `no-renew-item-policy` on the Host LMS configuration.
2. A **Fulfilment Unit loan rule** matching that item policy which disallows renewal.

> **Important:** Without the loan rule, OpenRS will set the policy, report success, and the
> renewal will still go through. The policy is only a label: the rule is what refuses.

If OpenRS cannot set the policy at all, most often because the item policy code does not exist
in your Alma, it falls back to writing a "please do not renew" note on the virtual item, where
Alma shows it to staff during circulation, and records the request as one it could not protect.
That fallback only covers a failed write. It does not fire when the policy is set successfully
but no loan rule acts on it, because from OpenRS's side nothing went wrong.

OpenRS only ever changes items it created itself. A virtual item carries the
`DCB_VIRTUAL_COLLECTION` call number, and a request to deny renewal on anything else is refused
rather than applied to one of your own items.

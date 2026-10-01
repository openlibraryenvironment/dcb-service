# Moving a member library to Alma

A guide for consortium staff and for a library that already lends and borrows through OpenRS
DCB on another system, Sierra for example, and is moving to Alma while staying in the network.

Everything a new Alma library does applies to you too: work through
[Setting up an Alma institution](alma-setup.md) first, on your Alma sandbox. This page covers
what is different because DCB already knows your library under your old system.

## What changes, and what does not

DCB ties each library, an **agency**, to one Host LMS: the record holding your system's
address, credentials and settings. Moving to Alma means a new Host LMS for Alma, and pointing
your agency at it. Your agency code, your library's name and details, and your place in the
consortium stay as they are.

| Tied to | What happens when you move |
|---|---|
| Your agency's Host LMS | Read live: patron sign-in, where your patrons' holds are placed, and where requests collected at your library are handled all follow the agency to Alma the moment it is re-pointed. |
| Requests already in progress | Each stores the Host LMS it started on, and tracking follows that stored value, except the pickup side, which follows the agency. A request that straddles the switch has DCB sending your old system's record ids to Alma. So requests are finished before the switch, not carried across it: see [Finishing requests on the old system](#drain). |
| Mappings | Held per Host LMS code, so none carry over. Sierra's item and patron types are numeric ranges; Alma's are codes. Alma needs a complete set of its own. |
| Pickup locations | Held per agency and location code. Re-importing them with the same codes keeps any request that points at one valid; each location's local ID changes to its Alma library code. |
| Your patrons' DCB records | Keyed on the patron's ID in their home system: the record number in Sierra, the primary ID in Alma. The same person under a new ID is a new DCB patron: see [Your patrons](#patrons). |
| Your records in the shared catalogue | Each belongs to the Host LMS it was harvested from. Alma's records are harvested afresh; the old system's stay until they are removed: see [After the switch](#old-records). |

<a id="patrons"></a>
## Your patrons

Decide this before you go live, because it cannot be changed afterwards.

When one of your patrons first requests after the move, DCB finds no patron under their Alma
primary ID and creates a new one. Their requests from before the move stay with the old record.
At the libraries they borrow from, it depends on the lender's system:

- **Sierra, Alma and Koha lenders** find a visiting patron by the patron's DCB identity, which
  includes their home ID. They create a second account for the patron, and the old one is left
  behind.
- **Polaris and FOLIO lenders** find one by barcode, so they reuse the old account if the
  patron's barcode has not changed.

There are two ways to keep continuity instead:

1. **Load each patron's old record number as their Alma primary ID.** DCB then sees the same
   patron. This is a decision for your migration with Ex Libris, and it shapes your Alma data
   for good.
2. **Have the OpenRS team re-key patron records** to the new IDs after the move. DCB has no
   function for this today, so it is a database task, done once with a list of old and new IDs.

Otherwise, accept new records and tell the other members they may see a second visiting
account for some of your patrons, theirs to remove once no loan is attached.

**Sign-in changes too.** Sierra checks a barcode and PIN. Alma checks a barcode and an internal
password held by the Ex Libris Identity Service, and DCB supports only that for Alma. A PIN is
not an Alma password unless your migration loads it as one, so confirm with Ex Libris what your
patrons will sign in with on day one. Patrons who sign in through your institution's identity
provider are not affected by the PIN, but the provider must release the value Alma matches
users on: see the sign-in step in [Before your patrons can
borrow](alma-setup.md#borrowing).

## Before the switch

These can happen weeks ahead, while you still lend and borrow on the old system.

1. **Complete the Alma checklist on your sandbox,** up to but not including the end-to-end
   test: [Before you join](alma-setup.md#onboarding-checklist).
2. **The OpenRS team creates your Alma Host LMS** under a new code, with record harvesting off
   (`ingest: false`) and `default-agency-code` set to your existing agency code.
3. **Load the Alma mappings** for that code: each Alma library code to your agency, your Alma
   item types, and your Alma user groups in both directions. See [Configuration
   values](alma-setup.md#configuration-values) and the Mappings section of the setup page.
   Translate from your old system rather than copying:
   - **Locations.** A Sierra location code becomes the Alma **library** that owns the item.
     Several Sierra locations usually become one Alma library.
   - **Item types.** A Sierra item type number becomes an Alma material type code, mapped to
     `CIRC` or `NONCIRC`.
   - **Patron types.** A Sierra patron type number becomes an Alma user group code: to DCB's
     patron types for your own patrons, and from them for the visiting patrons you will lend to.
4. **Plan your pickup locations:** the Alma library code for each of your current pickup
   locations. Keep the location codes themselves.
5. **Agree the patron decision** in [Your patrons](#patrons).
6. **Agree a date** with the OpenRS team and tell the members you lend to and borrow from. Your
   library is out of the network from the start of [Finishing requests on the old
   system](#drain) until the switch is verified.

<a id="drain"></a>
## Finishing requests on the old system

Your old system must stay reachable, and its circulation usable, until this is done.

1. **Stop lending.** Turn off supplying for your agency in DCB Admin. Your items leave live
   availability, so no new request is placed on them. Search results may still show your titles
   until the old records are removed.
2. **Start the Alma harvest.** The OpenRS team turns `ingest` on for the Alma Host LMS. A large
   catalogue takes days. Your Alma records join the same catalogue entries as your old ones;
   with supplying off, neither is offered.
3. **Stop borrowing.** Turn off borrowing for your agency. Your patrons can no longer place new
   requests.
4. **Let requests finish.** Loans from you to other members come home, and your patrons return
   what they borrowed. Staff keep working these in your old system as usual. Any request DCB
   Admin still lists for your agency when your old system closes is cancelled or completed by
   hand, with the other library.

## The switch

1. **Re-point your agency** to the Alma Host LMS, with the authentication profile
   `BASIC/BARCODE+PASSWORD`, supplying and borrowing still off. DCB Admin has no screen for this
   today. The OpenRS team does it, then restores your agency's loan and hold limits, which the
   same call clears.
2. **Re-import your pickup locations** under the Alma Host LMS, with the same location codes and
   each local ID set to its Alma library code.
3. **Stop the old harvest** by setting `ingest: false` on the old Host LMS.
4. **Remove the old Location mappings,** so that nothing still in the catalogue from the old
   system is attributed to your library.
5. **Run both reports** in [Checking the configuration](alma-setup.md#checking).
6. **Test in production:** borrow one item, lend one, and have one of your patrons collect at
   another member. The lent item must go into transit when scanned in.
7. **Turn supplying and borrowing back on.**

<a id="old-records"></a>
## After the switch

- **The old catalogue records stay** until the OpenRS team removes them. DCB has no supported
  way today to remove one Host LMS's records while keeping the Host LMS, and deleting a Host LMS
  that patrons are attached to is not safe, so this is done as a separate task. Until then, with
  the Location mappings gone, they show in the catalogue but offer no requestable copies.
- **The old Host LMS stays too,** and DCB keeps checking whether it answers. Expect connection
  alarms for it once your old system is switched off, until the OpenRS team retires it.
- **Staff lists.** The library staff view lists borrowing requests by your agency's current
  Host LMS, so requests made on the old system no longer appear there. DCB Admin still shows
  them.
- **Shared systems.** If your old system also hosts other members, it cannot be retired, and
  your old records can only be removed selectively. Talk to the OpenRS team before planning the
  move.

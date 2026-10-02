# Koha notes

## Setting up OAI-PMH ingest

DCB harvests a Koha catalogue with `KohaOaiPmhIngestSource`, which is assigned
automatically when the Host LMS is created against `KohaHostLmsClient`. It reads
`<base-url>/cgi-bin/koha/oai.pl` and follows resumption tokens until the harvest
is exhausted.

### 1. What the library has to set in Koha

Administration → System preferences → Web services:

| Preference | Set to | Why |
|---|---|---|
| `OAI-PMH` | **Enable** | Off by default. While it is off, `oai.pl` answers every verb with an OAI error rather than a 404, so the harvest fails rather than returning nothing. |
| `OAI-PMH:archiveID` | anything site-specific, e.g. `catalogue.example.org` | It is the prefix in the record identifier `<archiveID>:<biblionumber>`. Leaving it at the shipped `KOHA-OAI-TEST` works but makes two Kohas indistinguishable in a log. |
| `OAI-PMH:MaxCount` | 50 (default) is fine | Page size, not harvest size — DCB follows resumption tokens. |

Nothing else is required. In particular:

- **No OAI set is needed to harvest everything.** Koha only joins
  `oai_sets_biblios` when the request carries a `set` parameter, so a
  `ListRecords` without one returns every biblio. Leave `oai-set` unset in DCB.
- **`OAI-PMH:ConfFile` is not needed.** Without one, Koha serves `marcxml` and
  `oai_dc`. `marcxml` is what DCB ingests; `oai_dc` carries no MARC and is
  useless to it. `marc21` exists **only** where a ConfFile defines it, so do not
  assume the prefix that works for Alma or FOLIO works here.
- **`include_items` is not needed.** Availability comes live from
  `/api/v1/biblios/{biblio_id}/items` at resolution time, so item data embedded
  in a harvested bib would only age.

Verify before configuring anything in DCB — these need no credentials:

```
https://catalogue.example.org/cgi-bin/koha/oai.pl?verb=Identify
https://catalogue.example.org/cgi-bin/koha/oai.pl?verb=ListMetadataFormats
https://catalogue.example.org/cgi-bin/koha/oai.pl?verb=ListRecords&metadataPrefix=marcxml
```

`Identify` failing means the system preference is still off. `ListRecords`
returning `noRecordsMatch` on a populated catalogue means something is filtering
it — usually a `set` that was never built.

### 2. If the library genuinely wants a subset

An OAI set restricts what DCB harvests. Membership is **materialised**, not
evaluated per request, which is the usual reason a set harvests nothing.

1. Administration → OAI sets configuration → New set. Give it a `setSpec` and a
   name.
2. Define mappings on it: `marcfield`, `marcsubfield`, operator `equal` or
   `notequal`, value, joined with `and` / `or`. A biblio with at least one
   matching subfield belongs to the set.
3. Populate it, by **either**:
   - setting `OAI-PMH:AutoUpdateSets` to Enable, which recomputes membership when
     a record is created or modified — this does **not** backfill existing
     records; or
   - running `misc/migration_tools/build_oai_sets.pl` — `-r` to rebuild from
     scratch, `-i` **mandatory** if any mapping names an item field (952).

   Enable `AutoUpdateSets` *and* run the script once: the script covers the
   existing catalogue, the preference keeps it current. Otherwise the set is
   correct on the day it was built and drifts thereafter.

A set that matches the whole catalogue is possible — map `999$c notequal` some
value that never occurs, since `999$c` holds the biblionumber on every MARC21
record (`090$a` under UNIMARC) —
but it is strictly worse than omitting the set: it has to be rebuilt as the
catalogue grows, and `oai_sets_biblios` becomes a second copy of the biblio
table for no gain.

### 3. What to configure in DCB

Beyond the circulation keys `KohaClientConfig` requires (`api-url`,
`client_id`, `client_secret`, `sharing-library-code`,
`virtual-item-library-code`):

| Key | Required | Value |
|---|---|---|
| `base-url` | yes, for ingest | The **OPAC** origin, no path, e.g. `https://catalogue.example.org` |
| `metadata-prefix` | yes, for ingest | `marcxml` on a stock Koha |
| `oai-set` | no | setSpec, only to harvest a subset |
| `oai-path` | no | Defaults to `/cgi-bin/koha/oai.pl`; override only where the site rewrites URLs |

**`base-url` is not a duplicate of `api-url`.** `oai.pl` is served by the OPAC;
the REST API is commonly reached through the staff interface. Neither key can
stand in for the other, and Koha is the only ILS in DCB that needs both.

Both OAI keys are warnings rather than hard requirements at creation, because a
member that only borrows contributes nothing to the shared index and is
legitimately created with `"ingest": false`. Without them
`KohaOaiPmhIngestSource` throws on construction, which surfaces once as
`Ingest Check Failed` on the create response and thereafter as a harvest that
never runs.

### 4. Why the identifier matters

Koha's OAI identifier is `<archiveID>:<biblionumber>`; DCB splits on `:` and
keeps the last segment. That segment is the id `KohaHostLmsClient.getItems`
calls `/api/v1/biblios/{biblio_id}/items` with — so an identifier scheme whose
trailing segment is not the biblionumber ingests bibs whose items can never be
found, and the records resolve to nothing.

Because DCB keeps only the last segment, the *shape* of the prefix is free: a
bare token (`KOHA-OAI-TEST:1`) and the conformant `oai:<domain>` form
(`oai:catalogue.example.org:1`) both yield `1`. `KohaIngestTests` covers both.

### 5. Set OAI-PMH:archiveID before the first harvest, not after

`OAI-PMH:archiveID` ships as `KOHA-OAI-TEST` and should be changed to something
site-specific — but do it **before DCB harvests the system for the first time**.

DCB mints each record's UUID from the *whole* OAI identifier, archiveID
included:

```java
// OaiPmhIngestSource.uuid5ForOAIResult
uuid5Prefix + ":" + lms.getCode() + ":" + result.header().identifier()
```

So changing archiveID re-mints every `IngestRecord` and `RawSource` UUID and
changes every `SourceRecord.remoteId`. On the next harvest DCB sees the whole
catalogue as new records and the previously ingested ones are left behind — not
corrupt, but duplicated and orphaned, with no automatic cleanup. The
`sourceRecordId` (the biblionumber) is unaffected, which is why the damage is
duplication rather than broken resolution.

There is no REST endpoint for system preferences — the Koha API spec has no
syspref path — so this is Administration → System preferences → Web services in
the staff interface.

If it has to change after a harvest has already run, treat it as a reingest:
clear the Host LMS's ingest process state so the next run bootstraps from zero,
and plan to remove the records harvested under the old prefix.

## Bib suppression

Two different decisions a library makes, and they are not the same decision:

1. **"Nobody should see this."** Koha has a flag for it, and it hides the record
   in the library's own OPAC as well as in DCB.
2. **"We will lend this at home but not to the consortium."** Koha has **no**
   flag for this. It has no concept of a consortium, so there is nothing for it
   to have a flag about.

DCB handles both through one ruleset, `koha-default`, shipped in
`application.yml`. A Koha Host LMS with no `suppressionRulesetName` of its own
falls back to it — see `KohaOaiPmhIngestSource.getSuppressionRulesetName()`.
Suppressing a bib in DCB deletes it: `BibRecordService.process` treats
`suppressFromDiscovery` exactly like `deleted`, so flagging a record that was
already harvested removes it on the next pass. Nothing has to be reingested and
there is no separate cleanup.

### 1. "Not to be shown to anyone" — Koha's own flag, 942$n

This is Koha's `OpacSuppression` system preference. The per-record flag is the
subfield mapped to the `suppress` index, which the manual gives as **942$n** in
MARC21 and "no official field in UNIMARC". `1` means suppressed. A UNIMARC site
therefore has to check what its own Koha-to-MARC mapping points the `suppress`
index at, and needs its own ruleset — `koha-default` reads 942$n and nothing
else.

What the library has to do:

| Where | What |
|---|---|
| Administration → System preferences → Cataloging → Display | `OpacSuppression` = **Hide** |
| Administration → Authorized values | A category for the subfield — `YES_NO` works, or a `SUPPRESS` category with `0` = don't suppress, `1` = suppress |
| Administration → MARC bibliographic frameworks | Point 942$n at that authorized value, in every framework in use |
| Cataloguing | Set 942$n to `1` on the records to hide |

Two Koha-side facts worth knowing before you trust this:

- **Do not enable `OpacSuppression` until at least one record has a 942$n.**
  Koha's own system preference documentation warns that turning it on with no
  populated 942$n makes OPAC searches fail, because Zebra errors on an index
  with no values. This is a Koha trap, not a DCB one, but a library that hits it
  will turn the preference back off and the suppression will stop.
- **The value is coerced to a boolean.** Koha stores it via
  `TransformMarcToKoha`, which does `$value = $value ? 1 : 0` — Perl
  truthiness. So `2`, `no` and `maybe` in 942$n all hide the record in the
  OPAC. `0` and empty do not. This is why 942$n cannot be overloaded to carry
  the local-only distinction: any value you invent to mean "local only" also
  hides the record locally, which is the opposite of what was asked for.

Since Koha 25.11 the value is also mirrored into a real column,
`biblio.opac_suppressed` (bug 38330), which makes reporting easier. The MARC
subfield remains the cataloguer's interface and remains in the record Koha
serves over OAI-PMH, so DCB reads the subfield and is unaffected by the change.

DCB's side of it is the first condition of `koha-default`. Nothing else to
configure.

#### The OAI-PMH wrinkle

Koha's OAI-PMH server did not always honour `OpacSuppression`: suppressed
records were served in full, which is exactly why DCB filters on the flag
itself rather than trusting the harvest. Bug 37713, released in 26.05.00,
changed that — Koha now withholds the metadata and emits a
`<header status="deleted">` instead
(`Koha::OAI::Server::Repository::get_biblio_marcxml` returns early when
`is_opac_suppressed && $biblio->opac_suppressed`).

So which of the two mechanisms fires depends on the Koha version and on whether
`OpacSuppression` is on:

| Koha | `OpacSuppression` | What DCB receives | What suppresses the bib |
|---|---|---|---|
| any | off | full record, 942$n present | the `koha-default` ruleset |
| before 26.05 | on | full record, 942$n present | the `koha-default` ruleset |
| 26.05 onwards | on | header only, `status="deleted"` | the OAI deletion |

The ruleset route is the one to rely on, because it is the only one that works
in all three rows. The deletion route is pre-existing behaviour and is honoured
on the source-record import path — `OaiPmhIngestSource.initIngestRecordBuilder`
sets `deleted` from the header status, and `MarcIngestSource` tolerates the
absent MARC. The older `IngestService` path drops header-only records in
`getResources` before they reach that, so do not treat the deletion as the
primary mechanism.

Note also that the same early return covers `OpacHiddenItems` /
`OpacHiddenItemsHidesRecord` — a biblio all of whose items are hidden in the
OPAC is withheld from OAI-PMH too, with no 942$n involved. There is no MARC
flag for DCB to read in that case, so it is the deletion route or nothing.

### 2. "Local only" — 942$x, DCB's convention

Koha has no native flag, and researching this does not turn one up: no system
preference, no framework subfield, no ILL-side setting distinguishes "lendable
here" from "lendable to the consortium". Whatever carries the distinction has
to be something the consortium agrees on and DCB is told to read.

DCB's convention is **942$x**, and `koha-default`'s second condition reads it.
`1` means local only. The reasoning:

- It sits next to 942$n, the subfield cataloguers already use for the other
  kind of suppression, so both decisions are made in one place in the editor.
- Koha's default MARC21 framework defines 942 subfields `0 2 6 a c e h i k m n
  s` and nothing else, so `x` is free, and MARC21 942 is a local-use field so
  there is no standard to collide with either.
- It is unmapped, so Koha ignores it. Unlike a second value in 942$n it cannot
  accidentally hide the record locally.

What the library has to do:

| Where | What |
|---|---|
| Administration → Authorized values | `YES_NO`, or a `DCB_LOCAL` category with `0`/`1` |
| Administration → MARC bibliographic frameworks | Add subfield `x` to tag 942 in every framework in use. Label it something a cataloguer will understand — "Local only, not shared with DCB". Make it visible in the editor and point it at the authorized value |
| Cataloguing | Set 942$x to `1` on the records to withhold |

The framework step is not optional. Koha's cataloguing editors offer the
subfields the framework defines — as the manual puts it, "once in the
cataloging module you will not be able to add or remove fields and subfields" —
so until 942$x exists in the framework there is no way for a cataloguer to set
it.

#### Why not an OAI set instead

The Koha-native way to withhold records from a harvester is an OAI set that
excludes them (a mapping with operator `notequal` on whatever flag the site
uses), pointed at by `oai-set` — see "If the library genuinely wants a subset"
above. It works for a record that is flagged before DCB ever sees it, and it is
strictly worse than the ruleset for the case that actually matters:

- **A record leaving a set is not a deletion.** OAI-PMH has no way to say "this
  is no longer in your set"; it simply stops appearing in `ListRecords`. So a
  bib DCB already harvested that is then marked local only stays in DCB
  indefinitely. The ruleset, by contrast, sees the record, suppresses it, and
  `BibRecordService.process` deletes the bib it had.
- **Membership is materialised**, so it needs `OAI-PMH:AutoUpdateSets` plus a
  `build_oai_sets.pl` run, and drifts whenever either is missed.

Use a set to scope *what a library contributes at all*. Use the ruleset for
per-record suppression.

### 3. Using a different field

A consortium that has already standardised on something else does not have to
adopt 942$x. Write a ruleset and point the Host LMS at it by name:

```bash
curl -X POST https://dcb.example.org/object-rules \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "koha-consortium-x",
    "type": "CONJUNCTIVE",
    "conditions": [
      { "operation": "propertyValueAnyOf",
        "property": "metadata.record.fields.942.subfields.n",
        "values": ["1", "true", "Y", "y", "yes", "Yes", "YES"],
        "negated": true,
        "documentation": "Koha OpacSuppression" },
      { "operation": "propertyValueAnyOf",
        "property": "metadata.record.fields.598.subfields.a",
        "values": ["LOCAL"],
        "negated": true,
        "documentation": "Local only, site convention" }
    ]
  }'
```

then set `suppressionRulesetName` to `koha-consortium-x` on the Host LMS. Four
things about the shape:

- **Rulesets evaluate true for inclusion.** `inferSuppression` negates the
  result. A condition describes the records you want to keep.
- **`CONJUNCTIVE` is what makes the flags independent.** Every condition has to
  pass for the bib to survive, so either flag on its own suppresses it. A
  `DISJUNCTIVE` ruleset here would mean a record needs *both* flags to be
  dropped, which is not what anyone wants.
- **An absent property is an inclusion.** `propertyValueAnyOf` is false when the
  property does not resolve, and the negation turns that into "keep". So a
  catalogue that has never used the subfield is unaffected, and adding a
  condition for a field nobody populates is harmless.
- **Values are matched exactly, and case matters.** The engine builds
  `^(\Qa\E|\Qb\E)$` from the list, so `Yes` and `YES` are separate entries.
  This is why `koha-default` lists the case variants of `1`/`true`/`yes`.

Note the asymmetry with Koha in the value handling: Koha hides anything
Perl-truthy, DCB suppresses only what the ruleset lists. A site that puts `2` in
942$n gets a record hidden in its OPAC and still shared with DCB — unless it is
on 26.05 or later with `OpacSuppression` on, where Koha withholds the record and
the deletion covers for the ruleset. Use an authorized value so the question
never arises, and if a site insists on an exotic value, add it to the ruleset.

### 4. Checking it worked

There is no "suppressed bibs" view, because a suppressed bib is a deleted bib.
Confirm from the source side instead:

- Koha: `Reports` on `biblio_metadata.metadata` for the subfield, or on
  `biblio.opac_suppressed` from 25.11.
- What DCB actually received:
  `select source_record_data from source_record where remote_id = '<archiveID>:<biblionumber>'`
  (`select json from raw_source where remote_id = ...` on the older harvest
  path). This is the step that settles whether the flag is reaching DCB at all.
- DCB: no row in `bib_record` for that `source_record_id`, and the pass records
  a `DroppedTitle` stat against the Host LMS code.

The failure mode to look for is the flag never arriving — a framework that does
not define the subfield, a cataloguer editing the record under a different
framework, or an `OAI-PMH:ConfFile` that changes what Koha serves. The stored
source record distinguishes that from a ruleset that is not matching, and it is
worth checking before touching the ruleset.

## Item suppression

Separate from bib suppression, and it works differently in every respect worth
knowing about.

A suppressed **bib** is deleted from DCB. A suppressed **item** is not: it is
mapped as normal, flagged `suppressed`, and then dropped by
`LiveAvailabilityService`'s `notSuppressed` filter at availability time. The bib
stays, the other copies stay, and the suppression takes effect the next time
somebody checks availability rather than at the next harvest. There is no
re-harvest to wait for and nothing to undo if you get it wrong.

The ruleset is named by the Host LMS's `itemSuppressionRulesetName`. With none
set, `KohaHostLmsClient` falls back to **`koha-item-default`**, shipped in
`application.yml`.

### 1. What the default ruleset does

It suppresses an item whose `not_for_loan_status` is **42** — DCB's convention
for "this copy is local only, do not lend it through the consortium". That value
was hardcoded in `KohaHostLmsClient` until it moved into the ruleset; the
behaviour is unchanged, it is just editable now.

| Where | What to set |
|---|---|
| Administration → Authorized values → `NOT_LOAN` | Add `42`, described as something like "Local only — not shared with consortium" |
| Cataloguing → item editor | Set *Not for loan* to that value on the copies to withhold |

Any other `not_for_loan` value is left alone. A value of `1` means the item is
genuinely not lendable, but that is still the consortium's business to know
about — it shows as unavailable rather than vanishing.

`not_for_loan` authorised values are site-defined, so a library already using
`42` for something else should be pointed at a ruleset of its own rather than
editing `koha-item-default`, which every other Koha shares.

### 2. Withdrawn items are not part of this

`withdrawn > 0` suppresses unconditionally, in code, and is deliberately not
expressible in the ruleset. An item Koha says is gone must not be contributable
by any configuration.

### 3. Writing your own ruleset

The subject is a **`KohaItem` bean**, not a MARC record, which makes the
property names different from every other ruleset in this file. Conditions must
name the *Java* property:

```yaml
- operation: propertyValueAnyOf
  property: "notForLoanStatus"      # NOT "not_for_loan_status"
  values: [ "42" ]
  negated: true
```

Rule resolution goes through `BeanMap`, which keys on the introspected bean, so
the Koha API's snake_case does not resolve. **This fails silently**: an
unresolvable property makes `propertyValueAnyOf` false, the negation turns that
into "include", and every item is contributed with nothing logged to say why.
`RulesetTests.kohaItemDefaultSuppressesTheLocalOnlyNotForLoanValue` exists to
catch exactly that typo — extend it if you add conditions.

The same inertness is what makes the ruleset safe on a catalogue that has never
used the field: an absent property reads as "include".

### 4. Checking it worked

Unlike bibs, there is something to look at directly. Fetch live availability for
the cluster record and compare against Koha:

- Koha: `Reports` on `items.notforloan` for the biblionumber.
- DCB: the availability response for the cluster record — a suppressed item is
  absent from it while its siblings remain.
- Turn on `DEBUG` for `org.olf.dcb.core.interaction.koha.KohaHostLmsClient` and
  the decision log for each suppressed item is logged with the rules that fired.

If an item you expected to disappear is still listed, check the property name in
the ruleset before anything else — that is the failure that produces no error.

## Mappings

A Koha library needs three kinds of mapping before it can lend or borrow. All three
are reference value mappings, all three go in through DCB Admin, and none of them
is Koha-specific machinery — what is Koha-specific is *which Koha value* goes in
each one, and getting that wrong produces a mapping nothing ever consults.

| What | Category | Koha value | Direction |
|---|---|---|---|
| Item types | `ItemType` | itemtype code, e.g. `BK` | both ways |
| Patron types | `patronType` | patron category code, e.g. `AD` | both ways |
| Locations | `Location` | **branch** code, e.g. `BRANCH-N` | Koha → agency only |

### 1. The shape every mapping has to have

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

### 2. Item types

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

### 3. Patron types

Also both ways: inbound to decide whether a Koha borrower may request at all,
outbound to pick the category DCB's virtual patron is created under.

**Koha patron categories are short codes, not numbers.** Sierra's patron types
are numeric and are mapped with *numeric range* mappings; a Koha library
following that guidance ends up with numeric range mappings the Koha client never
reads — `findCanonicalPatronType` and `findLocalPatronType` both go to reference
value mappings only. Use `patronType` reference value mappings.

Get the codes from Administration → Patron categories, or
`GET /api/v1/patron_categories`.

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

### 4. Locations

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

## Pickup locations

A pickup location is a `Location` row, not a mapping, and it is what a patron
picks in discovery. Add them in DCB Admin under the library, or upload them as a
`Locations` file.

### What Koha needs in one

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

### Uploading them in bulk

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

### Verifying it end to end

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

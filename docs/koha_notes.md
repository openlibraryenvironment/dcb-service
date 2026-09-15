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

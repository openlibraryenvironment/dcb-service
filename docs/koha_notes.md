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

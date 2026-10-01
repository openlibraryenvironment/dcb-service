# Polaris notes

## Harvesting bibs over OAI-PMH

By default DCB harvests a Polaris catalogue through the PAPI `Synch_Bibs*`
endpoints on `PolarisLmsClient`. Polaris 7.7 and later can also publish an
OAI-PMH data provider, but it is optional at install time, so DCB only uses it
for a Host LMS that has been explicitly switched to it.

Circulation is unaffected either way: it always goes through `PolarisLmsClient`.

The choice is per Host LMS, so each Polaris server is harvested one way or the
other and both kinds run side by side. Libraries that share one Polaris server
share its Host LMS, its catalogue and therefore its harvest.

What differs for a Host LMS on OAI:

| | PAPI | OAI |
|---|---|---|
| Bib removed when deleted in Polaris | No | Yes, if the provider reports deletions |
| Bib removed when Display in PAC is unchecked | Yes | Only if the provider reports it as deleted (see below) |
| Item suppression ruleset | Applies | Applies, unchanged: items always come from PAPI |
| Reconciliation sweep (`/admin/sourceImport/<code>/reconcile`) | Recovers missing bibs | Recovers missing bibs |
| Vanished-bib sweep (`/admin/sourceImport/<code>/vanished`) | Not available | Finds, and on request removes, bibs the provider no longer lists |
| Stalled-checkpoint repair | Automatic | None |

### 1. Confirm the provider before switching

Run these against the library's server and keep the responses:

```
<base-url><oai-path>?verb=Identify
<base-url><oai-path>?verb=ListMetadataFormats
<base-url><oai-path>?verb=ListRecords&metadataPrefix=<prefix>
```

Check these:

| Check | Why |
|---|---|
| `Identify` answers at all | The provider is optional. A Polaris without it fails every harvest. |
| `ListMetadataFormats` lists a MARCXML format | Use that prefix exactly. `oai_dc` carries no MARC and is useless to DCB. |
| Each record identifier **ends in the Polaris `BibliographicRecordID`** after the last `:` | That segment becomes the bib's source record id, which is the id DCB passes to PAPI to fetch items. Any other identifier scheme ingests bibs whose items can never be found. |
| A bib with **Display in PAC** unchecked does **not** appear in `ListRecords` | The PAPI harvest reads that flag and removes the bib from DCB. The OAI harvest has no equivalent: a bib the provider lists is shown. |
| Unchecking **Display in PAC** on a test bib makes the next `ListRecords&from=<before the change>` return its identifier with `status="deleted"` | That is how a bib leaves DCB when it is hidden later. If the provider simply stops listing it, the bib stays in DCB indefinitely. |

If the first of those two fails, keep that Host LMS on PAPI. If only the second
fails, OAI is still usable, but a bib hidden later leaves DCB only when someone
runs the vanished-bib sweep (section 4).

### 2. Client config

| Key | Required | Notes |
|---|---|---|
| `base-url` | yes | The same value PAPI already uses. |
| `metadata-prefix` | yes | From `ListMetadataFormats`. |
| `oai-path` | no | Defaults to `/polaris.oaipmh.dataprovider/polaris/bibliographic`. Override it if the site serves the provider elsewhere. |
| `oai-set` | no | Only to harvest a subset. |
| `ingest` | no | `false` disables harvesting exactly as it does for PAPI. |

Add `metadata-prefix` (and `oai-path` if needed) to the Host LMS client config
**before** switching. A Host LMS switched without it is skipped by every
harvest run, with an error naming the missing key.

### 3. Switch the Host LMS

The admin UI does not edit `ingestSourceClass`, so send the mutation directly.
Omit `clientConfig`: when present it replaces the whole config.

```graphql
mutation {
  updateHostLms(input: {
    id: "<host-lms-id>"
    ingestSourceClass: "org.olf.dcb.core.interaction.polaris.PolarisOaiPmhIngestSource"
    reason: "Harvest over OAI-PMH"
  }) {
    hostLms { code }
    ingestStatus
  }
}
```

`ingestStatus` fetches the first page from the provider. Expect
`Success: Retrieved chunk with N records.`; anything else means the switch has
been saved but the host will not harvest. Fix the config, or switch back.

Then clear the import checkpoint so that the first OAI run is a full harvest
instead of resuming from the PAPI cursor:

```
POST /admin/sourceImport/<host-lms-code>/resetCheckpoint?reason=switch-to-oai
```

### What happens to records already harvested over PAPI

Bib records keep their ids: the OAI source keys each bib by host code and
`BibliographicRecordID`, exactly as the PAPI harvest does. The full harvest
updates them in place, so clustering and the shared index see updates, not new
records.

Source records do not keep their ids, because each mechanism stores its own
remote id: the bare bib id for PAPI and the full OAI identifier for OAI. The
old PAPI rows are not harvested again, but housekeeping can still queue them
for reprocessing, and the OAI source cannot read them.

Once the first full OAI harvest has finished:

1. Run the vanished-bib sweep (section 4), first as a report and then with
   `apply=true`. A bib PAPI harvested but the provider does not list has only
   its PAPI row, so this is the one point at which it can still be removed.
2. Delete the remaining PAPI rows. PAPI remote ids are bare integers, and an OAI
   identifier DCB can ingest always contains `:`, so this removes nothing the
   OAI harvest ingested. Rows still waiting to be processed are kept, so a
   deletion from step 1 is not lost; run it again later to remove them.

```sql
DELETE FROM source_record
WHERE host_lms_id = '<host-lms-id>'
  AND remote_id ~ '^[0-9]+$'
  AND processing_state <> 'PROCESSING_REQUIRED';
```

### 4. Recovery sweeps

Both run only when an administrator calls them; nothing schedules them. Only one
sweep runs at a time on a DCB instance, whichever Host LMS it is for, and each
sends one request at a time to the library's server. Both return 202 at once;
read progress and results from `GET /admin/sourceImport/status` on the same
instance.

**Missing bibs** — `POST /admin/sourceImport/<code>/reconcile?reason=…`

Lists every identifier the provider has (`ListIdentifiers`, no MARC) and
fetches each live one DCB holds no record for (`GetRecord`, one request per
missing bib). The listing can cost as many requests as a full harvest, but it
carries far less data and only the missing records are processed. When most of
a catalogue is missing, resetting the checkpoint is cheaper.

**Vanished bibs** — `POST /admin/sourceImport/<code>/vanished?reason=…`

Lists every identifier the provider has and compares it with the bibs DCB
holds for the host. A bib vanishes when the provider no longer lists it as
live: either it is missing from the listing, or it is listed as deleted. The
status report gives `held`, `vanished` and a `sample` of up to 20 bib ids to
check by hand in Polaris. It deletes nothing.

With `apply=true` it also replaces every stored record of each vanished bib
(OAI or PAPI) with a deletion, which the ingest job then processes to remove
the bib (`deletionsQueued` in the report). It refuses, reporting `refused`, when
the vanished bibs are more than 10% of the host's bibs: losing that much at once
is likelier to be a wrong set or a broken provider than real withdrawals.

If the listing fails part way, the sweep fails and changes nothing (`lastError`):
a partial listing would make every unlisted bib look vanished.

### Switching back to PAPI

Set `ingestSourceClass` to
`org.olf.dcb.core.interaction.polaris.PolarisLmsClient`, reset the checkpoint,
and once the full PAPI harvest has finished, delete the OAI rows instead:

```sql
DELETE FROM source_record
WHERE host_lms_id = '<host-lms-id>'
  AND remote_id !~ '^[0-9]+$'
  AND processing_state <> 'PROCESSING_REQUIRED';
```

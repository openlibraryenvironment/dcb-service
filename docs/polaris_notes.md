# Polaris notes

## Harvesting bibs over OAI-PMH

By default DCB harvests a Polaris catalogue through the PAPI `Synch_Bibs*`
endpoints on `PolarisLmsClient`. Polaris 7.7 and later can also publish an
OAI-PMH data provider, but it is optional at install time, so DCB only uses it
for a Host LMS that has been explicitly switched to it.

Circulation is unaffected either way: it always goes through `PolarisLmsClient`.

### 1. Confirm the provider before switching

Run these against the library's server and keep the responses:

```
<base-url><oai-path>?verb=Identify
<base-url><oai-path>?verb=ListMetadataFormats
<base-url><oai-path>?verb=ListRecords&metadataPrefix=<prefix>
```

Check four things:

| Check | Why |
|---|---|
| `Identify` answers at all | The provider is optional. A Polaris without it fails every harvest. |
| `ListMetadataFormats` lists a MARCXML format | Use that prefix exactly. `oai_dc` carries no MARC and is useless to DCB. |
| Each record identifier **ends in the Polaris `BibliographicRecordID`** after the last `:` | That segment becomes the bib's source record id, which is the id DCB passes to PAPI to fetch items. Any other identifier scheme ingests bibs whose items can never be found. |
| Whether a bib with **Display in PAC** unchecked appears in `ListRecords` | The PAPI harvest suppresses those bibs from discovery. OAI carries no such flag, so if the provider lists them, DCB shows them unless a suppression ruleset hides them. |

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
for reprocessing, and the OAI source cannot read them. Once the first full OAI
harvest has finished, delete them. PAPI remote ids are bare integers, and an OAI
identifier DCB can ingest always contains `:`, so this removes nothing the OAI
harvest ingested:

```sql
DELETE FROM source_record
WHERE host_lms_id = '<host-lms-id>'
  AND remote_id ~ '^[0-9]+$';
```

### Switching back to PAPI

Set `ingestSourceClass` to
`org.olf.dcb.core.interaction.polaris.PolarisLmsClient`, reset the checkpoint,
and once the full PAPI harvest has finished, delete the OAI rows instead:

```sql
DELETE FROM source_record
WHERE host_lms_id = '<host-lms-id>'
  AND remote_id !~ '^[0-9]+$';
```

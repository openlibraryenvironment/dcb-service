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
| Bib removed when Display in PAC is unchecked | Yes | Only if the provider reports it as deleted (see the checks below) |
| Item suppression ruleset | Applies | Applies, unchanged: items always come from PAPI |
| Missing-bib sweep (`/admin/sourceImport/<code>/reconcile`) | Recovers missing bibs | Recovers missing bibs |
| Vanished-bib sweep (`/admin/sourceImport/<code>/vanished`) | Not available | Finds, and on request removes, bibs the provider no longer lists |
| Stalled-checkpoint repair | Automatic | None |

### Provider checks

Run these against the library's server and keep the responses:

```
<base-url><oai-path>?verb=Identify
<base-url><oai-path>?verb=ListMetadataFormats
<base-url><oai-path>?verb=ListRecords&metadataPrefix=<prefix>
```

| Check | Why |
|---|---|
| `Identify` answers at all | The provider is optional. A Polaris without it fails every harvest. |
| `ListMetadataFormats` lists a MARCXML format | Use that prefix exactly. `oai_dc` carries no MARC and is useless to DCB. |
| Each record identifier **ends in the Polaris `BibliographicRecordID`** after the final `/` | That segment becomes the bib's source record id, which is the id DCB passes to PAPI to fetch items. DCB skips, and logs, any record whose final segment is not an integer. |
| A bib with **Display in PAC** unchecked does **not** appear in `ListRecords` | The PAPI harvest reads that flag and removes the bib from DCB. The OAI harvest has no equivalent: a bib the provider lists is shown. |
| Unchecking **Display in PAC** on a test bib makes the next `ListRecords&from=<before the change>` return its identifier with `status="deleted"` | That is how a bib leaves DCB when it is hidden later. If the provider simply stops listing it, the bib stays in DCB until a vanished-bib sweep removes it. |

If any of the first four fails, keep that Host LMS on PAPI. If only the last
fails, OAI is still usable, but plan to run the vanished-bib sweep regularly.

What one Polaris 7.7 tenant returned, on 2026-09-07, so you know what to expect.
Each server still needs its own checks, because the provider is optional.

| Item | Value |
|---|---|
| Identifier | `oai:stlouis-training.polarislibrary.com:polaris:bibliographic/2`. The bib id follows the final `/`, after three colons. |
| `baseURL` | `…/polaris.oaipmh.dataprovider/polaris/bibliographic`, the default `oai-path` |
| `granularity` | `YYYY-MM-DDThh:mm:ssZ` (to the second) |
| `deletedRecord` | `transient`: deletions are reported on a best-effort basis, so some will be missed. The vanished-bib sweep is how those are caught. |
| Bib ids | Sparse: the first page ran 2, 4, 5, 7, 8 |

### Client config

| Key | Required | Notes |
|---|---|---|
| `base-url` | yes | The same value PAPI already uses. |
| `metadata-prefix` | yes | From `ListMetadataFormats`. |
| `oai-path` | no | Defaults to `/polaris.oaipmh.dataprovider/polaris/bibliographic`. Override it if the site serves the provider elsewhere. |
| `oai-set` | no | Only to harvest a subset. |
| `ingest` | no | `false` pauses harvesting, exactly as it does for PAPI. Circulation is unaffected. |

The PAPI harvest ignores the OAI keys, so they can be added while the Host LMS
is still on PAPI. A Host LMS switched to OAI without `metadata-prefix` is
skipped by every harvest run, with an error naming the missing key.

## Migrating a Host LMS from PAPI to OAI

Bib records keep their ids across the switch: the OAI source keys each bib by
host code and `BibliographicRecordID`, exactly as the PAPI harvest does, so the
first OAI harvest updates bibs in place and clustering and the shared index see
updates, not new records. Source records do not keep their ids, because each
harvest stores its own remote id (the bare bib id for PAPI, the full OAI
identifier for OAI), so the old PAPI rows are cleared up at the end.

Every step can be undone with [Rolling back to PAPI](#rolling-back-to-papi).
`GET /hostlmss/importIngestDetails/<host-lms-id>` is the measuring instrument
throughout: it returns `bibRecordCount`, `sourceRecordCount`, `processStates`,
`ingestEnabled` and the current `checkPoint`.

1. **Check the provider.** Run the [provider checks](#provider-checks) and stop
   here unless the first four pass.

2. **Record a baseline.** Save the output of `importIngestDetails`, and note
   five or so bib ids from this host whose items currently show in DCB.

3. **Add the OAI config and pause harvesting.** In one edit of the Host LMS
   client config, add `metadata-prefix` (and `oai-path` if needed) and set
   `"ingest": "false"`.

4. **Wait for the running import to stop.** An import already in progress
   finishes its run even when paused. Read `importIngestDetails` twice, at least
   five minutes apart; when `checkPoint` has not changed, nothing is writing it.

5. **Switch the ingest source.** The admin UI does not edit
   `ingestSourceClass`, so send the mutation directly. Omit `clientConfig`: when
   present it replaces the whole config.

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

   `ingestStatus` fetches the first page from the provider, even while paused.
   Expect `Success: Retrieved chunk with N records.` Anything else means the
   switch is saved but cannot harvest: fix the config, or roll back.

6. **Clear the checkpoint**, so the first OAI run is a full harvest instead of
   resuming from the PAPI cursor:

   ```
   POST /admin/sourceImport/<host-lms-code>/resetCheckpoint?reason=switch-to-oai
   ```

7. **Resume harvesting.** Set `"ingest": "true"` (or remove the key).

8. **Wait for the full harvest and its processing.** While the harvest walks
   the catalogue, `checkPoint` carries a `resumptionToken`. When it has no
   `resumptionToken` and has a `from`, the full pass is done. Then wait until
   `processStates` shows no `PROCESSING_REQUIRED` left for the host.

9. **Verify.** Compare with the baseline:

   | Observation | Meaning |
   |---|---|
   | `bibRecordCount` close to the baseline | Bibs were updated in place, as intended. |
   | `bibRecordCount` close to double | Identifiers did not key to the PAPI bibs. Roll back. |
   | Many `FAILURE` in `processStates` | Identifiers or MARC are not being read. Roll back and recheck the provider. |
   | The noted bibs still show, with their items | Source record ids still match what PAPI uses for items. |

10. **Recover gaps** (optional). Run the [missing-bib sweep](#recovery-sweeps)
    and check `recordsRecovered`.

11. **Remove what the provider no longer lists.** Run the
    [vanished-bib sweep](#recovery-sweeps) without `apply` and check its
    `sample` in Polaris: those bibs should be withdrawn or hidden. Then run it
    with `apply=true`, and wait until `processStates` shows no
    `PROCESSING_REQUIRED` again. Do this before step 12: a bib PAPI harvested
    that the provider does not list has only its PAPI row, and this is the
    last point at which it can be removed.

12. **Delete the old PAPI rows.** PAPI remote ids are bare integers, and an OAI
    identifier DCB can ingest always contains `/`, so this removes nothing the
    OAI harvest ingested. Rows still waiting to be processed are kept.

    ```sql
    DELETE FROM source_record
    WHERE host_lms_id = '<host-lms-id>'
      AND remote_id ~ '^[0-9]+$'
      AND processing_state <> 'PROCESSING_REQUIRED';
    ```

    The PAPI rows are not harvested again, but housekeeping can still queue
    them for reprocessing, and the OAI source cannot read them.

13. **Final check.** `bibRecordCount` should be the baseline plus what the
    missing-bib sweep recovered, minus what the vanished-bib sweep removed.
    Place a test request against one of the noted bibs.

### Rolling back to PAPI

The same steps in reverse, and as safe, because bib ids are the same either way:

1. Set `"ingest": "false"` and wait for the checkpoint to stop changing, as in
   steps 3 and 4.
2. Set `ingestSourceClass` to
   `org.olf.dcb.core.interaction.polaris.PolarisLmsClient`.
3. Clear the checkpoint and set `"ingest": "true"`.
4. When the full PAPI harvest has finished and `processStates` shows no
   `PROCESSING_REQUIRED`, delete the OAI rows:

   ```sql
   DELETE FROM source_record
   WHERE host_lms_id = '<host-lms-id>'
     AND remote_id !~ '^[0-9]+$'
     AND processing_state <> 'PROCESSING_REQUIRED';
   ```

A bib removed by a vanished-bib sweep returns only if PAPI still lists it as
displayable.

## Recovery sweeps

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
a catalogue is missing, clearing the checkpoint is cheaper.

**Vanished bibs** — `POST /admin/sourceImport/<code>/vanished?reason=…`

Lists every identifier the provider has and compares it with the bibs DCB
holds for the host. A bib vanishes when the provider no longer lists it as
live: either it is missing from the listing, or it is listed as deleted. The
status report gives `held`, `vanished` and a `sample` of up to 20 bib ids to
check by hand in Polaris. It deletes nothing.

With `apply=true` it also replaces every stored record of each vanished bib
(OAI or PAPI) with a deletion, which the ingest job then processes to remove
the bib (`deletionsQueued` in the report). It refuses, reporting `refused`, when
the vanished bibs are more than a set share of the host's bibs: losing that
much at once is likelier to be a wrong set or a broken provider than real
withdrawals.

That share is `dcb.source-import.vanished-max-share`
(`DCB_SOURCE_IMPORT_VANISHED_MAX_SHARE`), a fraction from 0 to 1 that defaults
to `0.1`. It applies to every Host LMS and is read when DCB starts. To let
through a large withdrawal that the `sample` confirms is real, raise it,
restart, run the sweep with `apply=true`, then put it back. A value outside 0
to 1 is rejected with an error naming the setting.

If the listing fails part way, the sweep fails and changes nothing (`lastError`):
a partial listing would make every unlisted bib look vanished.

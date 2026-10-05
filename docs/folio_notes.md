# FOLIO notes

## Harvest settings

DCB harvests a FOLIO catalogue from edge-oai-pmh at `<base-url>/oai`, authenticated with the
same `apikey` as circulation.

| Setting | Required | Default | What it is |
|---|---|---|---|
| `record-syntax` | Yes, unless `ingest` is `false` | — | Sent as `recordSyntax` on every harvest request. |
| `metadata-prefix` | No | `marc21_withholdings` | The OAI-PMH metadata format: MARC with holdings. |
| `ingest` | No | `true` | `false` stops harvesting without removing the Host LMS. |

Earlier releases accepted a FOLIO Host LMS without `record-syntax` or `metadata-prefix`, and
its harvest then never ran: the ingest source could not be built, and DCB logged a warning
naming the Host LMS and carried on. A Host LMS without `metadata-prefix` now harvests with
the default. One without `record-syntax` still does not harvest, and its next change in DCB
Admin is refused until the setting is added.

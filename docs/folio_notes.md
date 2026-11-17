# FOLIO notes

## Harvest settings

DCB harvests a FOLIO catalogue from edge-oai-pmh at `<base-url>/oai`, authenticated with the
same `apikey` as circulation.

| Setting | Required | Default | What it is |
|---|---|---|---|
| `record-syntax` | No | `oai_dc` | Sent as `recordSyntax` on every harvest request. Every FOLIO on production uses `oai_dc`; set it only for one that differs. |
| `metadata-prefix` | No | `marc21_withholdings` | The OAI-PMH metadata format: MARC with holdings. |
| `ingest` | No | `true` | `false` stops harvesting without removing the Host LMS. |

Earlier releases accepted a FOLIO Host LMS without `record-syntax` or `metadata-prefix`, and
its harvest then never ran: the ingest source could not be built, and DCB logged a warning
naming the Host LMS and carried on. A Host LMS without either now harvests with the defaults.

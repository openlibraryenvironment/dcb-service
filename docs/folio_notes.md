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

## Request policies for DCB's patron groups

DCB places a supplier request in FOLIO for its virtual patron, in the patron group mapped
from the borrower's patron type. FOLIO pages an available copy, and asks for a Hold once the
copy is already paged or on loan, for example when a second consortium patron requests a
single copy. If the circulation rules' request policy for that patron group allows Page but
not Hold, FOLIO refuses the second request with `REQUEST_NOT_ALLOWED_FOR_PATRON_ITEM_COMBINATION`.

DCB records it with the remediation `FOLIO_REQUEST_POLICY_REFUSED`: the request's error message
names the Host LMS, the request type and the patron group, and its audit entry lists the next
steps (`docs/operational/remediation-catalogue.md`). The fix is to allow Hold (as well as Page)
in the request policy that applies to each patron group DCB uses.

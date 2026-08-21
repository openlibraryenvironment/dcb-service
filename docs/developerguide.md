# Developer Guide

DCB Profile NCIP2.02+ registration lives under
`org.olf.dcb.request.lifecycle.ncip.profile`. Keep controllers thin; application orchestration owns
validation, transactions, synchronization, and revocation. Directory/JWKS network pulls must finish
before redemption starts its database transaction.

Run:

```bash
GRADLE_USER_HOME="$PWD/.gradle-codex" timeout 30m ./gradlew test --no-daemon --no-build-cache --rerun-tasks
```

Cross-system acceptance is in `dcb-ops/docker-local/qa-local.sh
smoke-fallback-host-dcb-request`.

Electronic availability is transient. Add fields only to `AvailabilityResponseViewV2`; never extend the fragile legacy view.

## OAI-PMH checkpoints

`OaiPmhIngestSource` defaults to `HIGHEST_TIMESTAMP`: after the final resumption-token page, the next
inclusive `from` is the greatest record datestamp observed across the harvest. Boundary records replay
by design. `FolioOaiPmhIngestSource2` overrides this with `INTERNAL_CLOCK`, preserving established FOLIO
behaviour for feeds where many records can share one second-resolution datestamp. New OAI adapters should
use the default unless their provider has a documented equivalent constraint.

## Discovery metadata rebuild

New MARC mappings require reharvest, reprocess and shared-index rebuild; deployment alone does not enrich
existing clusters.

1. Record deployed revisions, source/index counts, current index/alias and sample cluster JSON. Confirm the
   previous index remains recoverable.
2. For each approved test Host LMS, reset its checkpoint with
   `POST /admin/sourceImport/{hostLmsCode}/resetCheckpoint?reason=discovery-metadata-rebuild`. Do not run this
   against production without a separate operational approval.
3. Let the scheduled import finish; monitor `GET /admin/sourceImport/status` and compare source counts.
4. Run `POST /admin/reprocess?criteria=ALL`; wait for housekeeping status to become inactive.
5. Run `POST /admin/reindex/START`; verify completion, document count, non-empty rich metadata and member
   objects, then validate the legacy availability endpoint.
6. On failure, keep/switch back to the recorded prior index alias. Do not delete the prior index until samples,
   counts and Discovery checks pass.

# Developer Guide

DCB Profile NCIP2.02+ registration lives under
`org.olf.dcb.request.lifecycle.ncip.profile`. Keep controllers thin; application orchestration owns
validation, transactions, synchronization, and revocation. Directory/JWKS network pulls must finish
before redemption starts its database transaction.

Profile directory pulls request the bounded `self=true` view. Preserve the canonical directory URL
for proof claims and membership state; apply the criterion only to the HTTP fetch URI.

`InvitationPolicy.authProfile` is the default. Optional `allowedAuthProfiles` constrains explicit
`RegistrationRequest.authProfile` selections. Keep explicit selections in the canonical descriptor;
legacy omitted selections must retain their historical descriptor shape and use the server-owned default.

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

1. Record deployed revisions, source/index counts, the fixed physical index and sample cluster JSON. Copy
   the current index to a uniquely named backup with Elasticsearch `_reindex`; verify its source document
   count and retain it until acceptance passes. DCB currently has no shared-index alias rollback.
2. For each approved test Host LMS, reset its checkpoint with
   `POST /admin/sourceImport/{hostLmsCode}/resetCheckpoint?reason=discovery-metadata-rebuild`. Do not run this
   against production without a separate operational approval.
3. Let the scheduled import finish; monitor `GET /admin/sourceImport/status` and compare source counts.
4. Run `POST /admin/reprocess?criteria=ALL`; wait for housekeeping status to become inactive.
5. Run `POST /admin/reindex/START`; verify completion, document count, non-empty rich metadata and member
   objects, then validate the legacy availability endpoint.
6. On failure, stop the rebuild and retain both the database and backup index. Restoring the fixed primary
   index requires a separately approved destructive replacement: recreate its normal mapping, reindex the
   backup `_source`, verify counts, then restart consumers. Never delete the backup before acceptance.

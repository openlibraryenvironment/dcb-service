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

## OAI-PMH checkpoints

`OaiPmhIngestSource` defaults to `HIGHEST_TIMESTAMP`: after the final resumption-token page, the next
inclusive `from` is the greatest record datestamp observed across the harvest. Boundary records replay
by design. `FolioOaiPmhIngestSource2` overrides this with `INTERNAL_CLOCK`, preserving established FOLIO
behaviour for feeds where many records can share one second-resolution datestamp. New OAI adapters should
use the default unless their provider has a documented equivalent constraint.

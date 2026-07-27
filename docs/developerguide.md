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

# Agent Operating Notes

## ADR First

Before making dependency or CI/test-run changes, review ADRs in `docs/ADRs/`.

Current required reference:

- `docs/ADRs/0001-full-suite-test-timeout-policy.md`

## Full Suite Test Policy

For full-suite test validation, use the command defined in the ADR:

```bash
GRADLE_USER_HOME="$PWD/.gradle-codex" timeout 30m ./gradlew test --no-daemon --no-build-cache --rerun-tasks
```

Do not terminate full-suite runs early unless there is a confirmed infrastructure/process issue.

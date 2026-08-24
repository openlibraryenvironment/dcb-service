# Agent Operating Notes

## Architecture First

Read `ARCHITECTURE.md` before substantial analysis or changes.

Keep `ARCHITECTURE.md` current when changing module ownership, dependency rules,
extension points, persistence ownership, external integration boundaries, or
known architectural rules.

Classify every new or changed HTTP entry point against the execution-boundary
rule in `ARCHITECTURE.md`; synchronous waits require explicit blocking dispatch
because their completion may need the Netty event loop occupied by the request.

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

## Definition Of Done

- Relevant tests or a clear reason they were not run.
- Documentation updated when architecture, module boundaries, extension points,
  persistence ownership, or external integration boundaries change.
- No database schema changes without explicit approval.

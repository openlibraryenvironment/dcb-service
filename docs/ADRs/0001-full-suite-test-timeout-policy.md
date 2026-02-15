# ADR 0001: Full Suite Test Timeout Policy

- Status: Accepted
- Date: 2026-02-15

## Context

The DCB full test suite is large and can run for a significant amount of time.
Ad-hoc interruption of long-running builds has caused confusion about whether the
suite was actually failing or simply still executing.

## Decision

For full-suite runs (`./gradlew test` or broader), use an explicit timeout of
at least 30 minutes and do not terminate early unless there is a confirmed
infrastructure issue.

Standard command:

```bash
GRADLE_USER_HOME="$PWD/.gradle-codex" timeout 30m ./gradlew test --no-daemon --no-build-cache --rerun-tasks
```

Policy:

1. Use `timeout 30m` minimum for full-suite runs.
2. Do not manually kill test runs before timeout unless the process is clearly orphaned or stuck outside normal test execution.
3. Treat a lack of frequent console output as normal unless there is corroborating evidence of deadlock/hang.
4. Record the final outcome (`BUILD SUCCESSFUL`, `BUILD FAILED`, or timeout exit) before concluding status.

## Consequences

- Reduces false negatives from prematurely terminated runs.
- Improves repeatability between local checks and CI expectations.
- May increase local feedback time, but provides reliable state assessment.

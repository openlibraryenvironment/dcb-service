# Architecture Documentation Review

Date: 2026-07-04

## Scope

Review the new documentation approach for dcb-service architecture guidance.

Covered documents:

- `ARCHITECTURE.md`
- `AGENTS.md`
- `docs/backlog/current/inbound-lifecycle-convergence.md`

## Findings

1. `ARCHITECTURE.md` now gives a useful starting model.

   It identifies main modules, ownership, dependency rules, extension points,
   persistence ownership, external integration isolation, and boundary rules.

2. `AGENTS.md` now points agents at `ARCHITECTURE.md`.

   It also adds a definition-of-done expectation that architecture docs stay
   current.

3. The no-schema-change guardrail is explicit.

   `ARCHITECTURE.md` and `AGENTS.md` both state that database schema changes
   need explicit approval.

4. Module-level docs are not yet present.

   This is acceptable for the first step. `MODULE.md` files should be added only
   where local rules are useful.

## Recommended MODULE.md Candidates

- `request.workflow`
- `request.lifecycle`
- `request.lifecycle.ncip`
- `tracking`
- `core.interaction`
- `storage`

Each `MODULE.md` should stay short and answer:

- what the module owns
- what it must not depend on
- extension points
- local testing expectations
- important boundary rules

## Guardrails

- Keep `ARCHITECTURE.md` high level.
- Do not turn architecture docs into an implementation guide.
- Link detailed docs from `ARCHITECTURE.md` instead of expanding it endlessly.
- Update docs when ownership, boundaries, extension points, persistence, or
  integration rules change.

## Review Outcome

Documentation approach is suitable as a starting model. Next step is to add
targeted `MODULE.md` files as architecture-sensitive modules are changed.


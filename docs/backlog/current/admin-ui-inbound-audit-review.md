# Admin UI Inbound Audit Review

## Status

Current follow-up.

## Context

Phase 1 preserved legacy polling audit wording and keys where possible. NCIP
inbound evidence adds protocol/correlation metadata and uses
`Inbound lifecycle message projected.`

## Goal

Review how existing admin applications show lifecycle evidence and explain DCB
request state when evidence comes from polling or inbound protocol messages.

## Guardrails

- Do not rewrite historical audit meaning casually.
- Protocol details should be additive unless an admin UI change is reviewed.
- Keep transaction history coherent for humans diagnosing request state.

## Acceptance Criteria

- Admin UI renders polling and inbound protocol audit rows coherently.
- Any proposed audit vocabulary change has reviewed examples.
- Documentation explains the stable audit concepts: source, role, resource,
  from state, to state, raw status, correlation, and protocol reference.

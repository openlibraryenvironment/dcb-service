# NCIP Acknowledgement Profile Hardening

## Status

Current follow-up.

## Context

Phase 1 accepts this NCIP ACK contract: received, accepted, projected, and
audited transactionally. ACK does not promise downstream workflow cascade
completion.

Current code may still complete workflow synchronously before responding where
that naturally happens.

## Goal

Define per-message NCIP response semantics and make the implementation match
the documented contract.

## Guardrails

- Do not couple ACK semantics to downstream LMS availability unless explicitly
  reviewed for a specific service.
- Keep protocol parsing/auth/mapping separate from workflow reaction logic.
- Preserve existing NCIP response shapes unless an external contract change is
  reviewed.

## Acceptance Criteria

- ACK semantics are documented per supported inbound NCIP message.
- Tests distinguish projection success from downstream workflow cascade success.
- Failure responses are explicit for parse, validation, auth, mapping, and
  projection failures.

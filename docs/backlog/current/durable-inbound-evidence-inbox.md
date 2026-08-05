# Durable Inbound Evidence Inbox

## Status

Current follow-up.

## Context

Phase 1 deliberately avoided schema changes. Inbound lifecycle evidence is
projected immediately and protected only by the current in-memory idempotency
guard.

## Goal

Design durable inbound evidence storage, replay, retry, and idempotency for NCIP
and future inbound protocols.

## Guardrails

- Requires explicit schema/design approval before implementation.
- Must not introduce hidden queues outside the lifecycle evidence boundary.
- Must define replay, duplicate detection, failure visibility, and admin audit
  behaviour.
- Must explain interaction with the existing polling loop.

## Acceptance Criteria

- Approved schema/design note exists.
- Architecture tests ensure all inbound lifecycle traffic passes through the
  approved inbox or documented equivalent.
- Retry semantics are explicit and testable.

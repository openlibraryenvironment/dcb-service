# TrackingServiceV4 Default Rollout

## Status

Current follow-up.

## Context

Phase 1 introduced `TrackingServiceV4` as an opt-in polling implementation using
lifecycle evidence projection. V3 remains the default.

## Goal

Decide whether and when V4 becomes the default tracking implementation.

## Guardrails

- No concrete Host LMS adapter changes.
- No external contract test changes without review.
- Keep `TrackingScheduler` as the only scheduled tracking entry point.
- Preserve existing polling audit wording and legacy audit keys.

## Acceptance Criteria

- CI and local smoke evidence are recorded.
- Operational backout plan is documented.
- V4 is enabled by configuration in a controlled environment before becoming
  default.
- Full suite passes before default switch.

# tracking

Owns scheduled polling and conversion of observed Host LMS state into lifecycle
evidence.

## Owns

- Scheduled tracking task registration through `TrackingScheduler`.
- Polling orchestration and manual `forceUpdate` through `TrackingService`.
- Poll counters and too-long handling.
- `StateChange` as the internal polling-detection vocabulary.
- Mapping polling `StateChange` records into lifecycle evidence.

## Must Not

- Update DCB request state directly.
- Call concrete Host LMS adapter implementations directly.
- Introduce new inbound projection paths outside lifecycle evidence projection.

## Extension Points

- `TrackingScheduler`: the single `@AppTask` / `@Scheduled` entry point for
  automatic tracking.
- `TrackingServiceV3`: default imperative tracking path using
  `HostLmsReactions`.
- `TrackingServiceV4`: opt-in tracking path selected with
  `dcb.tracking.service=v4`; routes state changes through lifecycle evidence.
- `TrackingEventSink`: local seam for polling event projection.

## Boundary Rules

- `StateChange` belongs to polling internals.
- Shared projection/audit belongs to `LifecycleEvidenceProjector`.
- `TrackingScheduler` owns scheduling. `TrackingServiceV3` and
  `TrackingServiceV4` must remain unscheduled implementations selected through
  Micronaut injection.
- V4 is not default until parity, full-suite, smoke, and review are complete.

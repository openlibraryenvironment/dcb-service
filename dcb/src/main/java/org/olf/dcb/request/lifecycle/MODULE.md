# request.lifecycle

Owns lifecycle strategy selection and protocol-neutral lifecycle evidence.

## Owns

- Imperative/declarative placement strategy ports.
- `LifecycleEvidence` as the canonical inbound evidence model.
- `LifecycleEvidenceProjector` for request evidence projection and audit.
- `LifecycleEvidenceIngestor` for reactive inbound evidence plus workflow
  progression.

## Must Not

- Depend on concrete Host LMS adapter packages.
- Decide `PatronRequest.status` directly.
- Store durable inbound evidence without explicit schema/design approval.

## Extension Points

- Add protocol adapters below `request.lifecycle.<protocol>`.
- Map protocol messages to lifecycle evidence.
- Keep protocol acknowledgement semantics explicit per protocol/message type.

## Boundary Rules

- Projection updates peer evidence fields on `PatronRequest`/`SupplierRequest`.
- Projection dispatches on canonical lifecycle fields: role, resource,
  operation, and status.
- Polling resource names such as `SupplierItem` and `PickupRequest` are legacy
  compatibility metadata. They must not define lifecycle projection decisions.
- DCB request state changes remain in `request.workflow` `Handle...`
  transitions.
- `Handle...` transitions are the application reaction layer that turns
  projected peer state into DCB workflow progression.
- Polling may call the projector directly; reactive inbound callers should use
  the ingestor so workflow progression is triggered.

# DCB Service Backlog

## High priority

- [DCB NCIP onboarding](current/dcb-ncip-onboarding.md): add an admin-only hub
  readiness and ORS Appliance invitation workflow under Service Information.
- [NCIP acknowledgement profile hardening](current/ncip-acknowledgement-profile-hardening.md):
  define response semantics and failure tests.
- [Durable inbound evidence inbox](current/durable-inbound-evidence-inbox.md):
  obtain schema/design approval for retry, replay, and idempotency.

## General follow-up

- [TrackingServiceV4 default rollout](current/tracking-service-v4-default-rollout.md):
  controlled rollout evidence and backout plan.
- [Admin UI inbound audit review](current/admin-ui-inbound-audit-review.md):
  review operator presentation and vocabulary.
- Object storage for brand assets: reinstate an S3-API `BrandAssetStore` as a third value
  for `dcb.branding.assets.store`, for estates that already run a bucket. Removed in
  favour of Postgres so uploads need no infrastructure; see `docs/branding.md`. Bring back
  the Caffeine cache as a decorator at the same time, since there will be two
  implementations wanting it.

Completed spikes and phases live in `done/`.

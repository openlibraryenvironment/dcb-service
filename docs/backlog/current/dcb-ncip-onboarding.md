# DCB NCIP Onboarding

## Status

In progress. Application and test deployment configuration complete; merge,
deployment and live verification pending.

## Goal

Add a discrete administrator workflow in `dcb-admin-ui` for preparing DCB Profile
NCIP2.02+ onboarding and issuing an ORS Appliance membership invitation.

The entry point is **DCB NCIP Onboarding** under `/serviceInfo`. It is visible
only to `ADMIN` and `CONSORTIUM_ADMIN` users. Other users must not see the link
and must not be able to open the route directly.

## Context

DCB already owns invitation policy and issuance at
`POST /api/v1/dcb-profile-ncip2/membership-invitations`. Invitations are
single-use, expire after 30 minutes, and are returned only at issuance. There is
currently no UI and no authoritative preflight proving that the DCB node has the
public URL, NCIP identity, and peer-auth configuration needed to complete
redemption.

ORS already owns tenant-side readiness and redemption. Do not duplicate its
directory, symbol, location, address, OAI-PMH, NCIP, or signing checks in DCB
Admin.

## User Journey

1. An administrator opens **Service information -> DCB NCIP Onboarding**.
2. The page loads DCB-owned readiness checks and explains how to resolve each
   failure. Secret values are never returned.
3. Invitation creation remains disabled until readiness passes.
4. The administrator enters and reviews the fixed invitation policy:
   - Host LMS code;
   - Agency code;
   - expected ORS symbol;
   - borrowing, supplying, and ingest permissions;
   - default and allowed authentication profiles;
   - optional loan limit and suppression rulesets.
5. The UI validates policy dependencies before submission: borrowing or
   supplying is required, and ingest requires supplying.
6. Explicit confirmation creates the invitation.
7. The result shows the DCB base URL, one-time invitation token, expiry time,
   copy actions, and instructions to continue in the ORS Appliance
   **Integrations -> Connect to DCB** workflow.
8. The UI clears the token when leaving the result and offers issuance of a new
   invitation if it expires or is lost.

## Implementation Plan

### 1. DCB readiness contract (`dcb-service`)

- Add an admin-secured
  `GET /api/v1/dcb-profile-ncip2/readiness` endpoint.
- Return an overall `ready` flag, profile/version, public DCB base URL, and
  stable checks with code, status, safe explanation, and remediation text.
- Check at least:
  - profile-registration node name and absolute HTTPS public base URL (HTTP is
    accepted only by the existing explicit local-development override);
  - peer authentication and NCIP peer authentication enabled;
  - local peer ID, issuer, subject, audience, JWKS URL, and key ID;
  - usable matching signing/public keys without returning key material;
  - DCB NCIP system and agency IDs;
  - advertised JWKS and NCIP URLs can be formed from public configuration.
- Put checks in one application service used by both the readiness endpoint and
  invitation issuance. Direct API calls must not bypass readiness.
- Reject issuance while unready with the normal problem envelope, stable code
  `PROFILE_REGISTRATION_NOT_READY`, and a retryable service-unavailable status.
- Keep `ADMIN` and `CONSORTIUM_ADMIN` authorization on both endpoints.

### 2. Admin-only workflow (`dcb-admin-ui`)

- Add `/serviceInfo/dcbNcipOnboarding` and a **DCB NCIP Onboarding** item to the
  Service Information page.
- Gate both navigation visibility and direct-route loading to `ADMIN` and
  `CONSORTIUM_ADMIN`. Backend authorization remains authoritative.
- Implement four clear states: readiness, policy entry, review/confirmation,
  and one-time invitation result.
- Use the existing bearer-authenticated REST client and backend problem
  envelope; do not add a parallel transport or GraphQL wrapper.
- Keep the invitation only in component memory. Never put it in URLs, logs,
  analytics, browser storage, query caches, or error reporting.
- Show an expiry countdown and clearly state that the token cannot be recovered.
- Keep advanced policy fields collapsed so the normal ORS onboarding path is
  short and comprehensible.

### 3. Deployment completeness (`park-hill-server-cluster`)

- Configure the DCB test deployment through GitOps and Vault with its public
  profile-registration URL, NCIP identity, and peer-auth signing/JWKS identity.
- Publish the readiness and JWKS routes only when the corresponding features are
  enabled.
- Prove the externally advertised DCB base, JWKS, and NCIP routes before live
  handoff. Do not treat successful invitation issuance alone as readiness.

### 4. Verification

- Backend integration tests cover authenticated roles, forbidden roles, every
  readiness check, issuance blocking, successful issuance, and redaction.
- API boundary tests distinguish missing, explicit-null, empty, malformed, and
  valid values. A tolerant optional field must still reject malformed present
  values.
- Frontend tests cover hidden navigation, direct-route protection, failed and
  successful readiness, policy dependencies, confirmation, token copying,
  expiry, and token clearing on navigation.
- Browser smoke proves the complete admin path against the deployed test DCB,
  followed by ORS non-consuming validation and explicit redemption.
- Record the human walkthrough in the applicable manual test plan before live
  testing.

## Acceptance Criteria

- Non-admin users cannot discover or open the onboarding workflow.
- Administrators receive actionable, non-secret readiness results.
- Neither the UI nor a direct API call can mint an invitation while DCB is not
  capable of completing registration.
- Policy is reviewed explicitly before creation.
- A valid invitation is displayed once with the correct DCB base URL and expiry.
- The invitation is not persisted client-side or exposed through telemetry.
- The ORS wizard can validate without consuming the invitation, then redeem it
  successfully and install reciprocal NCIP/JWT trust.
- DCB creates the HostLMS, Agency, Library, and selected Locations atomically.
- Deployment configuration and external routes are verified before the feature
  is offered to administrators.

## Out of Scope

- Reimplementing ORS tenant readiness in DCB Admin.
- Automatic redemption or passing the token directly between systems.
- General membership synchronization, sensitive-change review, rejection,
  revocation, or history UI; retain that as a separate roadmap item.
- Making NCIP onboarding visible to library or read-only users.

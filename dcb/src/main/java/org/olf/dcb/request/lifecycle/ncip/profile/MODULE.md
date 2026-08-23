# DCB Profile NCIP2.02+ Membership

Owns DCB-authorized invitation, validation, redemption, synchronization, review and revocation.

- `api`: public profile DTOs/controllers; never exposes HostLMS implementation classes.
- `application`: orchestration and policy. Network validation completes before mutation.
- `domain`: membership authorization/binding state.
- `persistence`: membership repository only.
- `support`: directory/JWKS pull, canonicalization and proof verification.

ORS directory data is descriptive authority. DCB owns membership, DCB codes and participation policy.
Existing HostLMS, Agency, Library and Location repositories remain authoritative for runtime objects.
Directory pulls add `self=true` to the fetch URI so ORS applies the self-entry criterion before
pagination. The canonical signed and stored directory URL is unchanged.

Invitation policy owns the default and allowed Agency authentication profiles. An explicit ORS
selection must be allowed and is bound into the signed descriptor. Legacy requests omit the selection
and receive the invitation default without changing their descriptor hash.

`DcbProfileReadinessService` is the single authority for DCB-owned invitation prerequisites.
Both the admin readiness endpoint and invitation issuance use it; results expose status and
remediation only, never configured key material.

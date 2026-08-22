# Features

- Catalogue ingest, clustering, availability, and request orchestration.
- Versioned electronic-item availability with an unchanged legacy contract.
- Source-aware OAI-PMH checkpoint policies with observed-timestamp resumption by default and preserved
  FOLIO clock semantics.
- Rich, structured MARC discovery metadata and serialized cluster-member holding summaries.
- NCIP v2.02 host integration with reciprocal JWT/JWKS authentication.
- DCB Profile NCIP2.02+ invitation issuance, constrained authentication-profile selection,
  non-consuming preflight, and atomic redemption.
- 15-minute directory synchronization with sensitive-change review.
- Membership revocation that disables participation while preserving history.

The registration REST contract is generated from
`/api/v1/dcb-profile-ncip2`; see the deployed OpenAPI document.

# DCB Resource-Sharing Glossary

- **System**: an ILS/host platform. One System may host many Agencies/Libraries.
- **Agency**: DCB's participating Library. Internal invariant: `agencyCode == libraryCode`.
- **Library**: the consortium-facing institution representation of an Agency; not a System.
- **Location ID**: DCB's globally unique Location UUID.
- **Local location code**: a code meaningful only within a System. Values such as `MAIN` may repeat.
- **Patron home location**: the patron's local home-library/location code at their System.
- **Pickup location**: the selected DCB Location. Legacy DCB placement accepts its Location UUID in
  `pickupLocation.code`; new facades should resolve a clear public identifier to that UUID.

External ILS, NCIP, and library practitioners may use agency, library, branch, site, institution, and
system differently. Translate that vocabulary in adapters and facade documentation; do not conflate
the internal concepts.

Example: Systems `system-a` and `system-b` may both expose local code `MAIN`; the System qualifies that
code. A shared System may host `library-a` and `library-b`, but its local location codes must still
resolve uniquely within that System. Facades must reject ambiguous imported data before calling DCB.

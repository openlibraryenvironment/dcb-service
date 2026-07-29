# Example configuration bundle

A minimal, self-consistent bundle showing the structure `dcb_setup.sh` expects.
Copy it to start your own:

```bash
cp -r scripts/config/example scripts/config/private-mine
./scripts/dcb_setup.sh --profile local --bundle private-mine --config all
```

`scripts/config/private-*/` is gitignored, which is the escape hatch for
configuration you cannot genericise. Everything else here is committed, so it
must not contain real API keys, real hostnames or real institution data — use
`${VARIABLES}` and put the values in your profile.

## Two levels: groups, then steps

```
example/
  profiles.conf          config profile -> group mapping
  folio/                 a group: one vendor's entire configuration
    10-hostlms/
    20-agencies/
    30-graphql/
  sierra/
    10-hostlms/
    ...
  foundation/            NCIP primitives + vendor override (profiles A/B)
  ors/                   declarative ORS Appliance (profile D)
```

`foundation/` is the Evergreen example: `FoundationClient` composing an NCIP
base protocol with a per-operation override. It is still **imperative**, so it
carries a `capabilities.imperative` block but no role strategies. See
docs/local-development.md, "Setting up a Foundation host".

`ors/` is worth reading if you are adding a **declarative** host: its
`clientConfig` carries a `capabilities` block selecting declarative placement
and event-driven tracking, which is what makes DCB stop polling it. Everything
else in this bundle is imperative and needs no such block. See
docs/local-development.md, "Setting up an ORS Appliance host".

A **group** is selected with `--config`. `--config folio` applies `folio/` only;
`--config all` applies every group. Groups named `zz-*` are shared: applied for
every config profile, and last — that is where a consortium belongs, since it
has to come after the libraries it groups.

`profiles.conf` maps names to comma-separated groups, so a config profile can
span vendors:

```
folio=folio
sierra=sierra
mobius=polaris,folio
```

`all` is built in and needs no entry.

### Adding a vendor

1. Create the group directory with its `NN-<kind>` step directories.
2. Add a line to `profiles.conf`.

No script changes. `--config all` picks it up automatically, and an empty group
directory is valid.

## Ordering within a group

Step directories are applied in lexical order. The `NN-` prefix exists purely to
make dependencies explicit, because DCB rejects an agency whose host LMS does
not yet exist:

| Kind | Endpoint | Files |
|---|---|---|
| `hostlms` | `POST /hostlmss` | one `.json` per host LMS |
| `agencies` | `POST /agencies` | one `.json` per agency |
| `locations` | `POST /locations` | one `.json` per location |
| `graphql` | `POST /graphql` | `.graphql` (preferred) or `.json` |
| `object-rulesets` | `POST /object-rulesets` | one `.json` per ruleset |
| `locations-upload` | `POST /locations/upload` | `<HOST_LMS_CODE>.csv` / `.tsv` |
| `mappings-upload` | `POST /uploadedMappings/upload` | `<HOST_LMS_CODE>.csv` / `.tsv` |
| `numeric-mappings-upload` | `POST /uploadedMappings/upload` | `<HOST_LMS_CODE>.csv` / `.tsv` |
| `group-membership` | `POST /graphql` | `.json` — see below |

The directory name is `NN-<kind>`; the number is only there to force ordering,
so renumber freely and reuse a kind as many times as you need (`30-graphql` for
libraries, `70-graphql` for the consortium). To add a library you add a file;
you never edit the script.

A group only needs the steps it actually uses — the `folio` group here has no
`40-object-rulesets`, and that is fine.

## Upload steps

For the three upload kinds the **filename is the host LMS code**:
`EXAMPLE_SIERRA.tsv` uploads against `code=EXAMPLE_SIERRA`. Both `.csv` and
`.tsv` are accepted. No data files are committed in this example bundle, since
real location and mapping data is site-specific — drop yours in and they will be
picked up.

`mappings-upload` sends `type=Reference value mappings`;
`numeric-mappings-upload` sends `type=Numeric range mappings`. That is the only
difference between them.

## Group membership

Joining libraries to a consortium group is the one step that cannot be a static
payload, because the group ID is only known at runtime. Describe it instead:

```json
{ "groupCode": "MY_CONSORTIUM", "libraryQuery": "agencyCode:*" }
```

The script resolves the group **by code**, queries libraries matching
`libraryQuery`, and adds each one. Resolving by code rather than capturing an ID
from a create response is what makes re-running a bundle safe.

## GraphQL

Write `.graphql` files as ordinary multi-line documents; the script wraps them
into `{"query": ...}` with `jq`, so there is no manual escaping. Use `.json`
only when you need to send `variables` alongside the query.

Note that GraphQL returns HTTP 200 even when a mutation fails. `dcb_setup.sh`
inspects the response body for `errors` and reports those as failures.

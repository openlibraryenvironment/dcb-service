# The remediation catalogue

When DCB can name why a request failed, the request says what to do about it, not only that it
failed. The words come from one maintained list, `dcb/src/main/resources/remediation/catalogue.json`,
so a remedy is written once, reviewed as text, and the same everywhere it applies.

## What staff see

A request that ends in ERROR for a reason in the catalogue:

- has the entry's **summary** as its error message, for example "COLLEGE's request policy does
  not allow a Hold request for patron group "DCB patrons" on this item";
- has a `remediation` block in the audit entry for that error:

| Field | What it is |
|---|---|
| `code` | the entry's code, for example `FOLIO_REQUEST_POLICY_REFUSED`, the same for every request it applies to |
| `audience` | whose staff can act on it: `SUPPLYING_LIBRARY`, `BORROWING_LIBRARY`, `PICKUP_LIBRARY`, `CONSORTIUM` or `OPERATOR` |
| `summary` | what went wrong, in a sentence |
| `nextSteps` | what to do about the cause, in order |
| `retrySteps` | how to retry the request safely once the cause is fixed, or place a new one |
| `docs` | where the longer explanation is |

Any other error keeps its message as before, with no `remediation` block. The detail and
parameters of the failure stay in the audit entry either way.

A supplier that refuses the request (`SupplierRefusedRequestProblem`) does not end the request
in ERROR: DCB treats that supplier as unable to supply, and the entry's summary and next steps
are the reason and `howToFix` in its "Supplier cannot supply" audit entry
(`supplier-preflight.md`). Only a copy someone chose still ends in ERROR, with the whole entry,
retry steps included.

## The entries

| Code | Audience | When |
|---|---|---|
| `FOLIO_REQUEST_POLICY_REFUSED` | Supplying library | A FOLIO supplier's request policy does not allow the request type for DCB's patron group. See `docs/folio_notes.md` |

## Adding an entry

An entry is a code that some part of DCB raises, and the words for it:

1. Add a constant to `RemediationCode`.
2. Add an entry with the same name to `catalogue.json`:
   - `audience`: one of the values above;
   - `placeholders`: the names the text may use, as `{name}`;
   - `summary`: one sentence, what went wrong;
   - `nextSteps`: at least one, each a sentence saying what to do about the cause;
   - `retrySteps`: at least one, saying how to retry the failed request safely once the cause is
     fixed: who can do it, what it does again, and anything that must happen first (for example,
     cleaning the request up before placing a new one, so its copy is free);
   - `docs`: where to read more.
3. Raise it: a problem that implements `RemediableProblem` returns the code and a value for each
   placeholder. Values are shown to staff and kept in the audit, so they never carry a patron's
   name, barcode or other personal data: use codes, types and group names.

The application does not start if an entry and `RemediationCode` disagree: a code with no entry,
an entry with no code, an entry with no audience, summary, next step or retry step, or text that
uses a placeholder the entry does not declare. `RemediationCatalogueTests` checks the shipped catalogue
the same way, and renders every entry.

A placeholder with no value reads "unknown".

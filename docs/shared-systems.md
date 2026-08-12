# Shared Systems in DCB

**How several libraries share one library management system, what you have to configure,
and why each piece exists.**

Audience: consortium administrators, implementers and support staff configuring DCB. This
describes what DCB does and why it does it, not how it is coded.

---

## 1. The three things DCB models

DCB keeps three concepts separate. Almost every shared-system problem is really a confusion
between two of them.

| Concept | What it is | Example |
|---|---|---|
| **Host LMS** | One *system* DCB talks to. A URL plus credentials. | One Koha installation. One Sierra server. One Alma tenant. |
| **Agency** | One *participating library*. This is what borrows and lends. | "Springfield Public Library" |
| **Location** | One *branch or site* inside a system, identified by that system's own local code. | Koha library `SPRING`, Sierra location `sp` |

An Agency belongs to exactly one Host LMS. A Host LMS may carry **many** Agencies. That
many-to-one relationship is what "shared system" means.

```
                    ┌──────────────────────────┐
                    │  Host LMS  "SHARED-KOHA" │   one Koha installation
                    │  api-url, credentials    │
                    └───────────┬──────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
   ┌────┴─────┐           ┌─────┴────┐            ┌─────┴────┐
   │ Agency   │           │ Agency   │            │ Agency   │
   │ SPRING   │           │ SHELBY   │            │ OGDEN    │
   └────┬─────┘           └─────┬────┘            └─────┬────┘
        │                       │                       │
  Location: SPRING        Location: SHELBY        Location: OGDEN
  (Koha library_id)       (Koha library_id)       (Koha library_id)
```

### Why the separation matters

An ILS does not have a concept of "which consortium member owns this". It has branches. DCB
has to lend between *libraries*, and on a shared system the branch code is the only thing in
the data that distinguishes one member from another.

**The bridge between a Location and an Agency is a reference value mapping.** Nothing else
connects them. Without a mapping for a branch, an item or a patron from that branch is
unattributable: DCB knows where it came from, but not who it belongs to, and therefore not
whether that library is in the consortium, lends, or borrows.

---

## 2. Two different shapes of "sharing"

These look similar and behave very differently. Get the shape right before configuring
anything.

### Shape A — one Host LMS, many libraries

One Koha with sixty member libraries. One Host LMS record, sixty Agency records, sixty
location-to-agency mappings.

This is the **supported and intended** shape. Use it whenever the libraries share one
database and one set of credentials.

When one library borrows from another inside this shape, DCB routes the request as
**RET-LOCAL**: it places a real hold on the real item for the real patron. No virtual bib,
no virtual item, no virtual patron.

*Why:* DCB's normal interlending machinery works by creating a placeholder copy of the
supplier's record in the borrower's system, so the borrower's staff can circulate something.
When both libraries are already in the same database, the record is right there — creating a
placeholder would mean duplicating a record next to itself, with a second barcode competing
with the real one. Placing a real hold instead is both simpler and closer to what the
libraries already do between branches.

> **A decision to take up front:** because DCB is not creating its own records, intra-system
> lending is governed by the ILS's own circulation rules, not by DCB's loan policy or
> canonical item type mappings. If your consortium expects DCB to enforce lending policy
> between co-tenant libraries, it will not. That is a consequence of the design, not a
> defect, but it needs to be a decision rather than a surprise.

### Shape B — many Host LMS records, one physical server

Two Sierra logins, different scopes, one Sierra server. Two Host LMS records in DCB.

DCB detects this and **excludes** items on the other record from resolution.

*Why:* this is the same duplication problem as above, but DCB cannot solve it the same way.
The two Host LMS records have separate credentials and scopes, so DCB treats them as separate
libraries and would create a virtual bib and item — into the very database the original
already lives in. Two Host LMS records over one server describes separately-administered
libraries; it is not a licence to lend an item to the database it already lives in.

If the two really are distinct circulation systems that happen to share a URL — an appliance
or gateway fronting several systems — set **`base-url-qualifier`** to a different value on
each record. DCB then treats them as separate systems and allows lending between them.

---

## 3. How DCB decides two Host LMS records are the same system

Each connector derives a system identity from the URL you configured:

| Connector | Identity derived from |
|---|---|
| Sierra | `base-url` |
| FOLIO | `base-url` |
| Polaris | `base-url` (plus the application-services override) |
| Koha | `api-url` |
| Alma | `alma-url` |
| OpenRS appliance | `ncip-endpoint-url`, qualified by `ncip-system-id` |

Two Host LMS records whose identities are equal are one system. Add `base-url-qualifier` to
either record to force them apart.

*Why the URL:* it is the one thing that is necessarily true of a system and independent of
how DCB happens to be configured. Anything DCB assigns itself — the Host LMS code, the
agency — would be equal only when someone remembered to make it so, and the failure would be
silent.

There is nothing to configure here in the ordinary case. Configure the URL correctly and the
rest follows.

---

## 4. The `shared-system` flag

Set on the Host LMS client config:

```json
{
  "api-url": "https://koha.example.org/api/v1",
  "client_id": "...",
  "client_secret": "...",
  "shared-system": true,
  "sharing-library-code": "DCB",
  "virtual-item-library-code": "DCB"
}
```

### What it does

DCB has two convenience shortcuts for systems that serve a single library. Both answer the
question "which library is this?" with "the only one there is". On a shared system that
answer is wrong, and wrong silently. `shared-system: true` disables both.

| Shortcut | On a dedicated system | On a shared system |
|---|---|---|
| **`default-agency-code`** — the agency to assume when a patron's home branch has no mapping | Sensible convenience | **Disabled.** Would attribute every co-tenant's patrons to one library |
| **`Location: *` wildcard mapping** — one mapping matching every location | Saves configuring each location | **Disabled.** Would make every co-tenant's entire catalogue suppliable as one library |

### Why this is a flag rather than advice

Both shortcuts fail *quietly*. A patron of a library that is not in the consortium at all,
arriving from a shared Sierra with `default-agency-code` set, resolves to the participating
agency — and then passes the borrowing-participation check, because the agency they were
wrongly attributed to genuinely does participate. Nothing errors. Nothing warns. The request
proceeds and an interlending transaction is placed on behalf of a library that never joined.

The same applies to supply: a `Location: *` mapping pointed at a participating agency makes
the non-participant's entire catalogue lendable.

Making this a configuration flag turns "remember not to use the shortcuts on a shared system"
into something the software enforces.

The cost is that on a shared system **every branch must be mapped explicitly**. That is the
point: an unmapped branch becomes a visible failure with an alarm, instead of a patron
quietly attributed to the wrong library.

### Rules DCB enforces

- `shared-system: true` together with `default-agency-code` is **rejected** by the admin API
  and by DCB Admin's Host LMS form. They contradict each other.
- The same combination arriving from application configuration at startup does not stop DCB
  booting, but raises a `CONFIG.<code>.SHARED_SYSTEM_DEFAULT_AGENCY` alarm. The default
  agency is ignored either way. *Why not refuse to boot: the runtime already declines to use
  the default agency, so the combination is harmless — what was dangerous about it was going
  unmentioned. Refusing to start over a configuration typo would be the worse trade.*
- The OpenRS appliance connector is exempt. It reads `default-agency-code` as the agency it
  names in every NCIP message, not as a fallback for an unmapped location, so a shared
  appliance needs it exactly as much as a dedicated one.

---

## 5. Which workflow a request runs under

DCB picks a workflow from three roles: the **patron's** library, the **lending** library and
the **pickup** library.

| Situation | Workflow | Virtual records created? |
|---|---|---|
| Patron, lender and pickup are all on one system | `RET-LOCAL` | No — a real hold on the real item |
| Lender is also the pickup, patron elsewhere | `RET-EXP` (expedited) | Yes |
| Patron collects at their own library | `RET-STD` (standard) | Yes |
| Patron collects somewhere that is neither their library nor the lender's | `RET-PUA` (pickup anywhere) | Yes |

**All three roles** must be on one system for `RET-LOCAL`. Two out of three is not enough.

*Why all three:* the borrower's system is where the virtual records have to go, and DCB
resolves which system that is from the patron. If the lender and the pickup share a system
but the patron does not, treating the request as local would hand the patron's system the
*other* system's bibliographic and item identifiers. They mean nothing there.

This three-way rule is what makes the shared-Koha case work. Lending between branch A and
branch B of one Koha, collected by a patron of that Koha, is `RET-LOCAL`, and the
virtual-record machinery — which assumes one library per system — never runs.

---

## 6. Excluding a library that is not in the consortium

This is the second common shared-system scenario: one physical Sierra, one library in
OpenRS, one library not.

**Model the non-participating library as an explicit Agency with both participation flags
off.** Do not simply leave its locations unmapped.

| Setting | Effect |
|---|---|
| `isSupplyingAgency: false` | Its items are dropped from availability. It never lends. |
| `isBorrowingAgency: false` | Its patrons are refused. It never borrows. |

*Why explicit rather than absent:* leaving the locations unmapped does exclude the library,
but by accident. Nobody can see the decision, it raises unmapped-location alarms that will
never be actioned, and one well-meant mapping added later silently includes a library that
never agreed to participate. An explicit agency with both flags false is an assertion: it
appears in the admin UI, it is quiet, and turning it on is a deliberate act.

Both flags are checked in more than one place — availability filtering on the supply side,
preflight *and* patron validation on the borrow side — so neither depends on a single switch
being left on.

**The real hazard here is the two shortcuts in §4.** Setting `shared-system: true` closes
both routes.

---

## 7. Setting up a shared system

1. **Create one Host LMS record** for the system, with `shared-system: true` and **no**
   `default-agency-code`.
2. **Create one Agency per participating library.** Set `isSupplyingAgency` and
   `isBorrowingAgency` to reflect what that library has actually agreed to do.
3. **Create one Agency per non-participating library**, with both flags `false` (§6).
4. **Let DCB find the branches.** Run an availability check against a title held across the
   system. Every branch DCB sees is recorded as a Location, and any that does not resolve to
   an agency is flagged **needs attention** with a `ReviewDynamicLocation` workflow. That
   list is your work queue. You can also enumerate branches from the ILS if you prefer —
   Koha's `GET /api/v1/libraries`, Sierra's location table — but you no longer have to.
5. **Create one location-to-agency mapping per branch**, from the Host LMS context, category
   `Location`, the branch's local code, to the agency code. Do **not** create a `*` mapping —
   it is ignored on a shared system.
6. **Check the branch codes are the ones DCB will see.** DCB reads the *owning branch* of an
   item, never its shelving location. Every library on a system draws shelving locations from
   the same vocabulary, so `STACKS` cannot identify anybody.

   | ILS | Branch DCB uses | Shelving location DCB ignores for mapping |
   |---|---|---|
   | Koha | `home_library_id`, falling back to `holding_library_id` | `location` |
   | Alma | `library` | `location` |
   | Sierra | the item's location code | — |

   Koha patrons are identified by their `library_id`. An item with no branch at all is
   dropped from availability rather than guessed at.
7. **Re-run availability** and confirm items now resolve to the right agencies, and that the
   needs-attention list has emptied.

### Optional: layered mappings with `contextHierarchy`

If a shared system needs per-library overrides on top of consortial defaults, set
`contextHierarchy` on the Host LMS to an ordered list of contexts:

```json
{ "contextHierarchy": ["SHARED-KOHA", "MOBIUS", "GLOBAL"] }
```

DCB tries each context in order and takes the first mapping it finds, so a consortium-wide
default can be stated once and overridden per library. This applies to location-to-agency
mappings and to patron type mappings alike.

---

## 8. Diagnosing an unmapped branch

Two things tell you, and they answer different questions.

### The location review queue — "which branches exist that I have not mapped?"

Every location DCB sees on an item is recorded. Ones that resolved to an agency are recorded
quietly; ones that did not are flagged `needsAttention` and carry a `ReviewDynamicLocation`
workflow. Filtering the admin UI's location list by needs-attention gives you the outstanding
mappings for a system.

*Why only the unmapped ones are flagged:* on an established consortium most locations already
resolve. Flagging all of them would bury the handful that need work under everything that
does not.

### The alarm — "something is being dropped right now"

```
ILS.<host-lms-code>.LOCATION_TO_AGENCY_FAILURE.Location
```

One alarm per Host LMS, whose details carry an `unmappedLocationCodes` list of every distinct
code seen. Onboarding a sixty-branch system produces **one** alarm listing the codes to fix,
not sixty notifications, and only the first sighting posts to your Slack or Teams webhook.

*Why one alarm rather than one per code:* the condition an operator cares about is "this
system has unmapped branches", which is one condition. Sixty notifications for it is not
sixty times as useful.

### Symptoms

| Symptom | Likely cause |
|---|---|
| Items from a branch never appear in availability | No location-to-agency mapping for that branch, or the items carry no branch at all |
| A patron is refused with "unable to resolve agency" | No mapping for their home branch, and no default is permitted |
| A patron resolves to the *wrong* library | `shared-system` not set, and `default-agency-code` or a `*` mapping is absorbing them |
| Items appear from a library that is not in the consortium | As above — a `*` mapping is attributing them to a participating agency |

---

## 9. Known limitations

**DCB will not lend a patron their own library's copy when the pickup is elsewhere.** A
patron at branch A of a sixty-library Koha, collecting at branch B, is supplied from branch C
rather than from the copy on their own shelf. DCB excludes the borrowing library's own items
whenever the pickup is somewhere else. On a consortium of separate systems that rule is
sensible; on one shared system it is a visible routing cost. Whether to support the direct
case is a product decision.

**Resolution does not record why an item was excluded.** The audit shows which items were
considered and which survived filtering, but not the reason for each exclusion.

**Two Host LMS records on one server both ingest.** Bibliographic records are harvested per
Host LMS record, so a server fronted by two records produces two copies of each record. This
is by design; be aware of it when sizing.

**Discovered locations are recorded, not classified.** DCB records the code and the name it
was given, and types the location `UNKNOWN` — it has seen a code on an item and cannot tell
whether that is a branch, a campus or a service point. Editing a discovered location is part
of actioning it.

**Location codes must be unique within an agency, not across the consortium.** Two libraries
can each have a `MAIN`; one library cannot have two. This matches how DCB derives a location's
internal identity, from the agency code and the location code together.

---

## 10. Configuration reference

Keys relevant to shared systems, on the Host LMS client config:

| Key | Type | Meaning |
|---|---|---|
| `shared-system` | boolean | This system hosts more than one participating library. Disables the default agency and the `*` wildcard. |
| `default-agency-code` | string | The agency to assume when a patron's home branch has no mapping. **Incompatible with `shared-system`** (except on the OpenRS appliance, where it means something different). |
| `base-url-qualifier` | string | Distinguishes logical systems that share one URL. Only needed for appliances and gateways. |
| `contextHierarchy` | string list | Ordered contexts to search for reference value mappings, most specific first. |
| `sharing-library-code` | string | Koha, Alma: the library that stands for "a borrower outside this system". Correctly one value per system, shared or not. |
| `virtual-item-library-code` | string | Koha, Alma: the branch virtual items are created at, used only when the borrowing patron's own branch is unknown. On a shared system the patron's home branch is preferred. |

Agency-level settings:

| Setting | Meaning |
|---|---|
| `isSupplyingAgency` | This library lends through DCB. `false` drops its items from availability. |
| `isBorrowingAgency` | This library borrows through DCB. `false` refuses its patrons. |

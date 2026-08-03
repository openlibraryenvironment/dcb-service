# Cancellations in DCB

How DCB detects and handles a cancelled request, on both the borrowing and the supplying side, what it
does to the virtual records each library holds, and where the sharp edges are.

Audience: anyone changing cancellation behaviour, or working out why a live request ended up where it did.

---

## 1. DCB almost always infers cancellation from polling

There is **no cancel endpoint on the DCB API**. `PatronRequestController` exposes `place`, `update`,
`rollback` and `transition/cleanup` — nothing that cancels. Patrons and staff cancel in their *own* ILS
(LEAP, Sierra, FOLIO), and DCB finds out later by polling.

Everything downstream follows from that. DCB is not reacting to an instruction; it is **inferring intent
from a hold that has stopped existing**, which is why so much of this document is about ambiguity.

The SupplierRequestController does provide some options for managing supplier requests, but these are typically used 
for re-resolution and troubleshooting.

### The polling loop

`TrackingServiceV3` runs
every `dcb.tracking.interval` (5m) over requests whose `next_scheduled_poll` is due, and for each one:

```
trackBorrowingSystem   -> checkPatronRequest   (the borrower's hold)
                          checkVirtualItem     (the borrower's virtual item)
trackPickupSystem      -> checkPickupRequest / checkPickupItem   (PUA only)
trackSupplyingSystem   -> checkSupplierRequest / checkSupplierItem
                       -> patronRequestWorkflowService.progressUsing(context)
```

Three properties of this loop matter constantly:

1. **The request is polled before the item.** Within the borrowing system, the hold is checked first.
2. **Nothing progresses mid-cycle.** `HostLmsReactions.onTrackingEvent` only persists the new field
   values and writes an audit entry. The workflow runs **once, at the end**, so a transition sees every
   change from that cycle at the same time.
3. **Which roles are polled depends on status.** `DefaultRequestTrackingPolicy.rolesTrackedAutomaticallyFor`
   maps status → roles; unknown statuses fall through to "poll everything" legacy behaviour. A host
   configured as `TrackingMode.EVENT_DRIVEN` is not polled at all and must supply evidence inbound.

### Transition selection is alphabetical

```java
allTransitions.stream()
  .sorted(Comparator.comparing(PatronRequestStateTransition::getName).reversed())
```

`PatronRequestWorkflowService.getPossibleStateTransitionsFor` takes the **first applicable** transition in
**reverse alphabetical order of `getName()`**. That is the entire priority mechanism.

> **This is a live issue that we will fix separately** Two transitions whose guards overlap are ordered by class
> name. `HandleCancelledRequestItemOut` outranks `HandleBorrowerItemLoaned` purely because `C` > `B`, and
> that is why the cancellation guard must exclude a loaned item explicitly (§4.3) rather than relying on
> something else winning. When adding a transition, check what else can be applicable in the same states
> and make the guards **disjoint**. Do not fix a collision by renaming — the next author will undo it.

---

## 2. What "cancelled" looks like on the wire

DCB normalises every ILS's hold status into `HostLmsRequest` constants. Two mean the hold is gone:

| Constant | Meaning |
|---|---|
| `HOLD_CANCELLED` (`"CANCELLED"`) | the ILS explicitly reports the hold as cancelled |
| `HOLD_MISSING` (`"MISSING"`) | the hold cannot be found at all |

**`MISSING` is deeply ambiguous and this is the root of most cancellation bugs.** A hold goes missing when:

- the patron cancelled it, **or**
- it was deleted by staff, **or**
- **it was consumed by a successful checkout** — Sierra and Polaris delete the hold record when the
  patron collects the item.

So "the hold is gone" on its own does **not** mean "the patron cancelled". Deciding which it was requires
looking at the item as well (§4.3).

Per-ILS notes:

- **Sierra / Polaris**: hold record disappears on checkout → `MISSING`. A genuine cancellation usually
  also surfaces as `MISSING`.
- **FOLIO**: `getRequest` reads the mod-dcb transaction; `CANCELLED` → `HOLD_CANCELLED`, a transaction
  that cannot be found → `MISSING`. A patron cancelling in FOLIO raises a Kafka `CANCEL` event, which
  `CirculationEventListener.processRequestEvent` turns into `cancelTransactionEntity` for **any** role.
- Once `localRequestStatus` is `MISSING`, `checkPatronRequest` stops polling that hold — there is nothing
  left to ask about.

---

## 3. The two kinds of cancellation

|  | Patron cancellation | Supplier cancellation |
|---|---|---|
| Who | patron or staff at the **borrowing** library | the **supplying** library |
| Observed on | `patronRequest.localRequestStatus` (and `pickupRequestStatus` for PUA) | `supplierRequest.localStatus` |
| Question DCB asks | is this request still wanted? | can this item still be supplied? |
| Answer | no → wind the whole request down | maybe → try another supplier |

They are handled by entirely separate transitions and never interact.

---

## 4. Patron cancellation

The patron no longer wants the item. The request must end — the only question is what to do about the
physical item and the virtual records, and that depends on where the item has got to.

### 4.1 Before the item ships — `CancelledPatronRequestTransition`

**Source states:** `REQUEST_PLACED_AT_BORROWING_AGENCY`, `REQUEST_PLACED_AT_PICKUP_AGENCY`
**Guard:** borrower hold is `MISSING` or `CANCELLED`

The item is still on the supplier's shelf. Nothing has moved, so there is nothing to orphan:

1. cancel the supplier hold (`SupplyingAgencyService.cancelHold`) — skipped with an audit entry for a
   declarative supplier, which has no imperative cancel;
2. cancel the pickup hold if this is PUA;
3. set `Status.CANCELLED` and `Outcome.CANCELLED`;
4. re-fetch the supplier hold and audit whether the cancellation actually took.

`FinaliseRequestTransition` then cleans up and the request reaches `FINALISED`.

### 4.2 After the item ships — `HandleCancelledRequestItemOut`

**Source states:** `PICKUP_TRANSIT`, `RECEIVED_AT_PICKUP`, `READY_FOR_PICKUP`
**Guard:** borrower hold (or, for PUA, borrower **or** pickup hold) is `MISSING`/`CANCELLED`,
**and the item is not `LOANED`**

The item has left the supplier. Finalising now would delete the borrowing library's virtual records while
the physical item is still out — see §6 for why that is so damaging. Instead:

1. **Terminate the supplier hold** and verify it actually went (§4.5);
2. **Park** in `AWAITING_RETURN_TO_SUPPLIER` — always, for every supplier;
3. release to `CANCELLED` + `Outcome.CANCELLED` only once the supplier reports the real item back
   (`HandleCancelledRequestItemReturned`, guard: supplier item `AVAILABLE`/`RECEIVED`).

The design reasoning is in §7.

### 4.3 The one that is not a cancellation: collection

At `READY_FOR_PICKUP`, a patron collecting the item produces **exactly the same hold signal as a
cancellation** — Sierra and Polaris consume the hold on checkout. In the same tracking cycle the item
becomes `LOANED`, and because the workflow only runs at the end of the cycle, both facts are visible
together.

Two transitions are therefore applicable at once, and alphabetical ordering picks the cancellation one.
`HandleCancelledRequestItemOut` excludes itself when the borrower **or** pickup item reports `LOANED`, so
`HandleBorrowerItemLoaned` wins and the request proceeds to `LOANED`.

> Covered by `patronCollectingTheItemMustNotBeTreatedAsACancellation` and its PUA twin. Without that
> guard, **every successful pickup is parked as a cancellation.**

### 4.4 Pickup Anywhere: which hold is the patron's?

Under PUA there are two holds at the borrowing end:

| Hold | Whose | Cancelled by |
|---|---|---|
| borrower (`localRequestId`) | the **real patron**, at their home library | the patron — this is the one they see |
| pickup (`pickupRequestId`) | a **virtual patron** DCB created at the pickup library | DCB, as a consequence |

`CancelledPatronRequestTransition` has always keyed on the **borrower** hold and torn the pickup hold down
afterwards. `HandleCancelledRequestItemOut` watches the borrower hold always, plus the pickup hold for PUA
— losing that also means the item is out with nothing holding it.

> Watching only the pickup hold silently drops every PUA patron cancellation: once the item is out,
> `CancelledPatronRequestTransition` no longer claims those states either, so nothing moves the request
> and it never finalises. Regression test:
> `shouldBeApplicableForPickupAnywhereWhenThePatronCancelsAtTheirHomeLibrary`.

### 4.5 Why the supplier hold must be terminated, and verified

The supplier hold is normally consumed by the **supplier-side checkout**, which only happens once the
patron actually loans the item. A patron who cancels before loaning leaves it live. Left in place, when
the item is checked back in at the supplier it is routed straight back out to the borrower again —
Polaris reports this as *"transfer for hold"*; FOLIO re-fulfils the still-open transaction.

So DCB deletes it via `SupplyingAgencyService.cleanUp` (`HoldOperation.DELETE`), each client implementing
the right terminal operation for its ILS.

`cleanUp` swallows its own failures — `checkHoldExists` converts errors to empty, `switchIfEmpty` yields
`"OK"`, and the result is discarded. So `HandleCancelledRequestItemOut` **re-fetches the hold afterwards**
and audits whether it is really gone. Parking a request whose hold survived means waiting for an
`AVAILABLE` that the surviving hold will prevent ever happening.

---

## 5. Supplier cancellation

The supplying library cancels or loses the hold. The patron still wants the item, so DCB tries to get it
somewhere else.

### 5.1 `HandleSupplierRequestCancelled`

**Source states:** `REQUEST_PLACED_AT_SUPPLYING_AGENCY`, `CONFIRMED`, `REQUEST_PLACED_AT_BORROWING_AGENCY`,
`REQUEST_PLACED_AT_PICKUP_AGENCY`
**Guard:** supplier hold `localStatus` is `MISSING`/`CANCELLED` **and** the supplier request `isActive`

It marks the supplier request `CANCELLED`, sets the patron request to `NOT_SUPPLIED_CURRENT_SUPPLIER`, and
audits. It does not touch the borrower's records.

> **All four source states are pre-shipment.** There is no supplier-cancellation handling once the item is
> in transit or on the pickup shelf — by then the supplier hold has legitimately been consumed, so a
> vanished supplier hold is normal rather than a signal.

### 5.2 `ResolveNextSupplierTransition`

**Source state:** `NOT_SUPPLIED_CURRENT_SUPPLIER`
**Guard:** always applicable — the branch is inside `attempt`

Re-resolution runs only when **both**:

- the `RE_RESOLUTION` consortium functional setting is enabled (defaults to **false** if unset), and
- the item was **not** manually selected (`isManuallySelectedItem`) — DCB will not silently substitute an
  item a human chose.

**If re-resolution applies:** find another agency with no outstanding supplier request, create a new
`SupplierRequest`, and re-enter at the place-request-at-supplying-agency step. The patron's own hold is
left alone throughout — from their point of view nothing happened.

**If it does not:** cancel the local borrowing hold, cancel the pickup hold, and set
`NO_ITEMS_SELECTABLE_AT_ANY_AGENCY` — a `FinaliseRequestTransition` source, so the request cleans up and
finalises. Note that here DCB **cancels the patron's hold for them**; this is the one path where a
cancellation travels borrower-ward.

---

## 6. What cancellation actually destroys

Cancelling is only half the story. `FinaliseRequestTransition` fires on `CANCELLED`, `COMPLETED` or
`NO_ITEMS_SELECTABLE_AT_ANY_AGENCY` and runs `CleanupService.cleanup` **before** setting `FINALISED`:

```
supplyingAgencyService.cleanUp   -> delete the supplier hold
borrowingAgencyService.cleanUp   -> delete the borrower's hold, then item, then bib
pickupAgencyService.cleanUp      -> delete the pickup item, bib and hold (PUA only)
```

### The adapter asymmetry

Whether that last step destroys anything **depends entirely on the borrower's ILS**:

| Borrower | `deleteItem` / `deleteBib` |
|---|---|
| Polaris | real — `ApplicationServices.deleteItemRecord` / `deleteBibliographicRecord` |
| Sierra | real — `client.deleteItem` / `deleteBib` |
| FOLIO | **no-ops** — *"Delete virtual item is not currently implemented for FOLIO"*, returns `OK` |

A FOLIO borrower's virtual item is a mod-dcb **circulation item** whose lifecycle mod-dcb owns; DCB never
deletes it. A Polaris or Sierra borrower's virtual bib and item are DCB's own creations and really are
deleted.

### The practical consequence

Deleting those records **while the physical item is still out** leaves the borrowing library with no
record to check the item back in against. The supplier eventually writes the item off and **bills the
borrowing library for a lost item**. This is the DCB-2193 defect.

> **Only Polaris libraries reported it, and that is a measurement artefact, not evidence.** FOLIO
> borrowers were unaffected because DCB had nothing to delete there — the absence of a *symptom*, not of
> the bug. **Judge any change to cleanup timing on the borrower.**

---

## 7. DCB-2193: cancelling while the item is out

### 7.1 The defect

A patron cancels while the item is in transit to them or sitting on the pickup shelf. Pre-DCB-2193, that
was treated as an ordinary cancellation: `CANCELLED` → auto-finalise → virtual records deleted, with the
physical item still out in the world. Polaris libraries lost their record of items their patrons had not
yet returned and were billed for them.

### 7.2 The decisions

**Track the request until the real item is back, then clean up.** Cleanup is not skipped or weakened; it
is *deferred* to the point where it is safe.

**A dedicated status, `AWAITING_RETURN_TO_SUPPLIER`.** Not `CANCELLED`, which auto-finalises. Not
`RETURN_TRANSIT` either: when the patron cancels while the item is on the pickup shelf, nothing is in
transit anywhere — the item is **stranded and needs a human to send it back**. A distinct status makes
that visible, filterable and alertable, and that operational signal is the justification for adding one.

**The park releases to `CANCELLED`, not back into the return leg.** Nothing was supplied. Rejoining
`RETURN_TRANSIT` would end in `HandleSupplierItemAvailable` and stamp `Outcome.SUPPLIED` on a request
nobody ever received.

**`Outcome.CANCELLED` is set explicitly**, because both paths bypass `CancelledPatronRequestTransition`,
which is otherwise the only thing that sets it.

**Keep the pre-shipment fast path.** `CancelledPatronRequestTransition` was narrowed to the two
pre-shipment states rather than deleted — cancelling before the item moves is safe and terminal, and
routing those through the park state would fill it with requests that need no attention.

### 7.3 The state graph

```
                 patron hold gone, item OUT, item NOT with the patron
 PICKUP_TRANSIT     ─┐        terminate + verify the supplier hold
 RECEIVED_AT_PICKUP ─┼─(HandleCancelledRequestItemOut)─► AWAITING_RETURN_TO_SUPPLIER
 READY_FOR_PICKUP   ─┘         (always - never CANCELLED)   │
                                                           ├─(supplier item AVAILABLE/RECEIVED)─►
                                                           │    CANCELLED ─► FINALISED
                                                           │      (records deleted here, safely)
                                                           │
                                                           └─ supplier that cannot report a return at
                                                                all: stays parked, flagged + alarmed,
                                                                released by hand (cleanup?force=true).
                                                                No ILS is in this state today.

 item still AT supplier (REQUEST_PLACED_*) ─► CANCELLED ─► FINALISED   (§4.1: nothing to orphan)
 item WITH the patron (item LOANED)        ─► LOANED                   (§4.3: a collection)
```

### 7.4 Rejected: cancelling when the supplier cannot report a return

An earlier revision asked whether the **supplier** could report the item's return, and cancelled
immediately when it could not, reasoning that waiting for a signal that cannot arrive strands the request
forever. A FOLIO supplier answers "cannot report", so every FOLIO-supplied request took that path.

Shipped against a Polaris borrower, it deleted the Polaris virtual item while the real item was out and
the library was billed — **the exact bug DCB-2193 exists to fix, reintroduced through the back door.**

The error was one of role and of question. What the *supplier* can observe says nothing about whether it
is safe to delete the *borrower's* records. **A supplier's tracking limitations are not the borrowing
library's to pay for.** The transition now parks unconditionally;
`HostLmsClient.canReportItemReturnedAfterHoldTerminated()` survives only to decide whether a parked
request needs **flagging**, and is documented as barred from gating cleanup or finalisation.

### 7.5 FOLIO suppliers: reading Inventory instead of the transaction

`ConsortialFolioHostLmsClient.getItem` normally derives item status from the mod-dcb transaction. DCB
itself makes that transaction terminal when it cancels the lender hold, and mod-dcb cannot help
afterwards — `findTransactionByItemIdAndStatusNotInClosed` excludes `CANCELLED`, so the physical check-in
event can never reach it.

FOLIO's **Inventory** is a separate channel that survives the cancellation: the item stays `In transit`
(reason *"DCB cancelled"*) until it is physically checked in at the owning library, then becomes
`Available`. So for a `CANCELLED` transaction **only**, `getItem` falls back to Inventory
(`itemFromInventory`) and a FOLIO supplier reports the return like any other.

**No workflow code knows or cares** — the park releases through the ordinary guard. Deliberately scoped:

- `CANCELLED` only; every other transaction status is still authoritative, and `CLOSED` already maps to
  `AVAILABLE`;
- empty when Inventory does not know the item, so a **borrower's** record — which lives in
  mod-circulation-item, not Inventory — keeps the transaction's answer, with no role detection needed;
- Inventory errors fall back to the transaction, because tracking must not break when Inventory is down.

> **Containment:** this rests on FOLIO leaving the item `In transit` on cancellation. If a tenant is ever
> seen flipping it straight to `Available`, the fallback would release the park instantly and reintroduce
> the bug. The rollback is to override `canReportItemReturnedAfterHoldTerminated()` to `false` on the
> FOLIO client, restoring parked-and-flagged behaviour in one line.

### 7.6 Transitions removed

- **`HandleCancelledRequestReturnTransit`** — its guard was identical to `HandleSupplierItemAvailable`'s,
  so it existed only to hop into `RETURN_TRANSIT` so that transition could run on a *later* poll.
- **`HandleBorrowerSkippedLoanTransit`** — its guard was a strict subset of
  `HandleCancelledRequestItemOut`'s, so which of the two ran was decided by class name, and it jumped to
  `RETURN_TRANSIT` **without terminating the supplier hold**.

  **Behaviour change:** a genuinely missed loan is indistinguishable from a cancellation — in both cases
  the hold is gone, the item is out, and DCB never saw a loan. Those requests now park and record
  `Outcome.CANCELLED` rather than completing as supplied. The physical handling is identical and correct
  either way; only the label differs.

---

## 8. Manual cleanup

`POST /patrons/requests/{id}/transition/cleanup` runs `CleanupService` by hand.

It **rejects with 409 Conflict** (an RFC-7807 `ThrowableProblem`, rendered by `micronaut-problem-json`) in
every status meaning "the item is not back at the supplier yet": `PICKUP_TRANSIT`, `RECEIVED_AT_PICKUP`,
`READY_FOR_PICKUP`, `LOANED`, `RETURN_TRANSIT`, `AWAITING_RETURN_TO_SUPPLIER` — plus `CANCELLED`, which
finalises on its own.

`?force=true` overrides the check for support staff who have confirmed the item's whereabouts by other
means. It is logged and audited.

`dcb-admin-ui`'s `cleanupStatuses` hides the button for the same set and must be kept in step.

### `ERROR` is allowed, but not blindly

Clearing errored requests is what this endpoint is mainly for, so `ERROR` is permitted — but it is the one
permitted status whose stored state can be **arbitrarily stale**. `application.yml` sets
`ERROR: null` in `dcb.polling.durations`, so `next_scheduled_poll` is never set and an errored request is
**never polled again**. Its status is frozen at the moment it failed, however long ago that was.

`ERROR` itself says nothing about where the item is. `previousStatus` does — it is recorded on every
status change by `PatronRequest.decidePreviousStatus`. So cleanup is refused when a request errored *out
of* an item-out status, with the same `force=true` override.

Without that, a request that errored in `PICKUP_TRANSIT` reads `ERROR`, is waved through, and cleanup
deletes the borrower's virtual records with the item still out — the DCB-2193 bug, by hand, on exactly the
requests most likely to be cleaned up by hand.

### Why the guard does not poll first

The obvious objection is that the guard trusts stored state, so it should call
`TrackingService.forceUpdate` first to be sure. It deliberately does not:

- **It would not help where staleness actually bites.** The guard reads `status`, and tracking only
  changes `status` as a side effect of workflow progression. The genuinely stale case is `ERROR`, and
  nothing progresses out of `ERROR` — polling refreshes item and hold fields the guard never reads.
  `previousStatus` closes that hole for free.
- **It mutates before refusing.** `forceUpdate` runs `progressUsing`, so a request in `PICKUP_TRANSIT`
  with a vanished hold parks itself and the caller then gets an error about `AWAITING_RETURN_TO_SUPPLIER`
  — a status they never asked about, on a request DCB has already changed.
- **It is slow and can fail silently.** Up to three ILSs are polled synchronously inside an admin API
  call, and `forceUpdate` swallows its own errors (`onErrorResume(error -> Mono.just(pr_id))`), so a
  downed ILS yields stale data anyway, just later.
- **Freshness is already a separate operation.** `POST /{id}/update` *is* `forceUpdate`. An operator who
  wants current data can ask for it explicitly, and then decide. Coupling it to the destructive call
  removes that choice.

The remaining exposure is a status that is fresh-ish but not current: pre-shipment statuses poll hourly
(`REQUEST_PLACED_AT_BORROWING_AGENCY: 1h`), so there is a window in which the item has shipped and DCB has
not noticed. That window is bounded, it self-heals on the next poll, and `force=true` is not needed to
work around it — which is not true of the `ERROR` case.

> Implementation note: `status` is a **reserved** Problem property — passing it to `.with()` throws at
> runtime. The offending status is reported as `patronRequestStatus`.

---

## 9. Working with mod-dcb

DCB does not own mod-dcb; the FOLIO team does. Current limitations DCB works around, and what would let
us stop:

1. **A lender transaction cannot be closed by DCB.** `LendingLibraryServiceImpl.updateTransactionStatus`
   has no `→ CLOSED` case and throws *"status update from X to Y is not implemented"*. For a lender,
   `CLOSED` is only reached by a physical check-in event.
2. **Cancelling is the only way to release the hold**, and it makes the transaction terminal — which is
   what costs DCB its tracking channel and forced the Inventory fallback in §7.5.
3. **A cancelled lender transaction can never be closed later.**
   `findTransactionByItemIdAndStatusNotInClosed` is `status NOT IN ('CLOSED','CANCELLED','ERROR','CREATED','OPEN')`,
   and `CirculationEventListener.handleDcbLoanEvent` uses exactly that query.

**Ranked asks:**

1. *Preferred* — let a cancelled lender transaction still receive its check-in, so `CHECK_IN + LENDER →
   CLOSED` still fires. DCB could then use one channel instead of two.
2. *Alternative* — a distinct lender state (e.g. `CANCELLED_AWAITING_RETURN`) that cancels the circulation
   request so it cannot re-capture, but stays eligible for check-in → `CLOSED`.
3. *Not recommended* — let DCB `PUT → CLOSED` on a lender transaction. It would unblock us, but DCB would
   be asserting a physical fact it does not know.

**Also worth reporting as a bug:** `cancelRequest` silently no-ops when
`getCancellationRequestIfOpenOrNull` returns null, and the lender `CANCELLED` branch never calls
`updateTransactionEntity` — so `PUT /status {CANCELLED}` can return success having changed nothing.
Callers cannot distinguish "cancelled" from "did nothing".

---

## 10. Reading a cancelled request

Audit messages worth searching for:

| Message | Means |
|---|---|
| `CancelledPatronRequest : LOCAL_HOLD_MISSING` / `_CANCELLED` | pre-shipment patron cancellation (§4.1) |
| `CancelledRequestItemOut : patron hold gone while item is out…` | parked (§4.2) |
| `CancelledRequestItemOut : supplier hold termination verification` | whether the supplier hold really went (§4.5) |
| `CancelledRequestItemOut : declarative supplier hold termination is not implemented…` | declarative supplier; hold **not** terminated |
| `CancelledRequestItemOut : supplier cannot report this item's return…` | parked and needs manual release |
| `CancelledRequestItemReturned : item is back at the supplier…` | park released (§4.2) |
| `Supplier Request Cancelled (ID: …)` | supplier-side cancellation (§5.1) |
| `Clean up result` | finalisation ran; lists the state of each virtual record afterwards |

Common questions:

- **Stuck in `AWAITING_RETURN_TO_SUPPLIER`?** The supplier has not reported the item back. Check the
  hold-termination verification audit — a surviving supplier hold re-captures the item and prevents it
  ever going `AVAILABLE`. Release with `cleanup?force=true` once the item is confirmed home.
- **A collection was treated as a cancellation?** The item status was not `LOANED` when the workflow ran.
  Check whether the borrower reports loans on the item DCB is tracking.
- **A cancellation was missed entirely?** Check which hold went missing. For PUA the borrower hold is the
  patron's (§4.4). For a declarative supplier, imperative termination is skipped by design.
- **Records deleted too early?** That should now be impossible via the automatic path; check for a
  `force=true` cleanup, and see §6 for what each ILS actually deletes.

---

## 11. Invariants

Changing cancellation behaviour? These are the things that must stay true.

1. **Never finalise a request while the physical item is out.** Finalisation deletes the borrower's
   virtual records at Polaris and Sierra (§6).
2. **A missing hold is not a cancellation on its own.** It also means the patron collected the item
   (§4.3).
3. **A supplier's tracking limitations never license deleting the borrower's records** (§7.4).
4. **Judge cleanup-timing changes on the borrower**, and specifically on a Polaris or Sierra borrower —
   FOLIO cannot show you the symptom (§6).
5. **Guards must be disjoint**, because ties are broken by class name (§1).
6. **A transition that is applicable but changes nothing loops forever** — `applyTransition` recurses into
   `progressAll` and reselects it. Release conditions belong in `isApplicableFor`, which is synchronous.
7. **A new status must be registered in four places**: `dcb.polling.durations`,
   `DCBStartupEventListener`, `DefaultRequestTrackingPolicy.rolesTrackedAutomaticallyFor`, and
   `dcb-admin-ui` (`DCBStatuses`, `cleanupStatuses`, `useChartPalette`, en-GB + es locales).

### Regression tests that must not be deleted

| Test | Guards against |
|---|---|
| `patronCollectingTheItemMustNotBeTreatedAsACancellation` (+ PUA twin) | every pickup parked as a cancellation |
| `shouldBeApplicableForPickupAnywhereWhenThePatronCancelsAtTheirHomeLibrary` | PUA cancellations silently dropped |
| `folioSupplierMustStillParkSoTheBorrowerKeepsItsVirtualItem` | the §7.4 regression |
| `shouldDeleteVirtualRecordsEvenWhenTheBorrowerItemIsStillInTransit` | virtual records orphaned on every normal loan |
| `ConsortialFolioHostLmsClientGetItemTests` (Inventory fallback cases) | FOLIO parks never releasing, or releasing instantly |
| `PatronRequestCleanupGuardTests` | cleanup API deleting records while the item is out, including via a stale `ERROR` |
| `engineMustPickTheReleaseTransitionThatCancels` | another transition claiming `AWAITING_RETURN_TO_SUPPLIER` and stamping `Outcome.SUPPLIED` |

> Two of these assert the **engine's** choice rather than a transition in isolation
> (`patronCollectingTheItemMustNotBeTreatedAsACancellation`,
> `engineMustPickTheReleaseTransitionThatCancels`). That is deliberate: guards tested in isolation cannot
> see a collision, and both bugs those tests cover were collisions. A merge that resurrected a superseded
> transition was invisible to every other test in this list.

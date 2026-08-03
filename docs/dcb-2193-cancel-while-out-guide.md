# DCB-2193 — "Patron cancelled while the item is out": how the solution works

When a patron's hold disappears while the borrowed item is physically **out** of the supplying library,
DCB used to auto-cancel and finalise the request — deleting the borrowing library's virtual records and
orphaning the physical item, which the supplier eventually writes off as lost and bills for. This
document describes the implemented solution on `dcb-2193-new-transition`.

## 0. The two mechanics you must internalise

**(a) The supplier hold MUST be terminated, or the returning item is re-captured.** The supplier-side
checkout that normally consumes the hold only happens once the patron actually **loans** the item. Here
nothing consumed it, so the hold is still live. Left in place, when the item is checked back in at the
supplier it is routed straight back out to the borrower:

- **Polaris / Sierra / Alma** re-fill the native hold (Polaris reports **"transfer for hold"**).
- **FOLIO** re-fulfils the still-open mod-dcb transaction / circulation request.

So DCB terminates the hold via `SupplyingAgencyService.cleanUp` (`HoldOperation.DELETE`), and then
**verifies it actually went**. `cleanUp` swallows its own failures, and parking a request whose hold is
still live means waiting for an `AVAILABLE` that can never arrive.

**(b) After termination, only *some* ILSs can still tell us the item came home.** This is the crux:

- **Sierra / Polaris / Alma:** the supplier's **real inventory item** is tracked independently of the
  hold. It stays out until physically checked in, then reports `AVAILABLE`. That is a reliable
  "physically back" signal, so we **park** the request and release it only when the item is genuinely
  home.
- **FOLIO:** `getItem` normally derives the item status from the **mod-dcb transaction**
  (`mapToItemStatus`), and terminating the hold makes that transaction terminal — it reports `CANCELLED`
  forever and can never report `AVAILABLE`. But mod-dcb is not the only channel. FOLIO's **Inventory**
  survives the cancellation: the item stays `In transit` (reason "DCB cancelled") until it is physically
  checked in at the owning library, then becomes `Available`. So for a `CANCELLED` transaction only,
  `getItem` falls back to Inventory (`itemFromInventory`) and FOLIO reports the return like everyone
  else. **No workflow code knows or cares** — the park releases through the ordinary guard.

Expressed as a client capability, not `if (ils == …)`:
`HostLmsClient.canReportItemReturnedAfterHoldTerminated()` — default `true`. **Nothing overrides it to
`false` today**; FOLIO did until the Inventory fallback made it able to report. It is kept as the
extension point for any ILS that genuinely cannot, and drives flagging only.

> **This capability must never gate cleanup or finalisation, and does not.** It describes what the
> *supplier* can observe. It says nothing about whether it is safe to delete the *borrower's* records.
>
> An earlier revision of this branch used it to cancel a FOLIO-supplied request at entry, reasoning that
> waiting for a signal that cannot arrive is a permanent stall. Shipped against a Polaris borrower, that
> deleted the Polaris virtual item while the real item was still out and the library was billed for a
> lost item — the exact bug DCB-2193 exists to fix, reintroduced through the back door. **A supplier's
> tracking limitations are not the borrowing library's to pay for.** Its only remaining use is deciding
> whether a parked request needs flagging for a human to release.

### mod-dcb evidence (folio-org/mod-dcb, lender role)

- **`→ CLOSED` cannot be forced by DCB.** `LendingLibraryServiceImpl.updateTransactionStatus` has no
  `→ CLOSED` case; it falls through to `IllegalArgumentException("status update from %s to %s is not
  implemented")`. For a lender, `CLOSED` is only reached by a physical check-in **event**.
- **Cancel releases the item.** `PUT CANCELLED` → `BaseLibraryService.cancelTransactionRequest` →
  `CirculationServiceImpl.cancelRequest` cancels the underlying page request, returning the item to
  `Available`.
- **No path back to `CLOSED` after cancel.** `TransactionRepository.findTransactionByItemIdAndStatusNotInClosed`
  is `status NOT IN ('CLOSED','CANCELLED','ERROR','CREATED','OPEN')`, and
  `CirculationEventListener.handleDcbLoanEvent` uses exactly that query — so a later physical check-in can
  never find a `CANCELLED` transaction and can never close it.

**Also worth reporting to the mod-dcb team:** `cancelRequest` silently no-ops when
`getCancellationRequestIfOpenOrNull` returns null, and the lender `CANCELLED` branch never calls
`updateTransactionEntity` — so `PUT /status {CANCELLED}` can return success having changed nothing.
Callers cannot distinguish "cancelled" from "did nothing".

### What we want from mod-dcb (ranked)

1. **Preferred — let a cancelled lender transaction still receive its check-in.** Include `CANCELLED` in
   the lookup used by the DCB loan-event handler so `CHECK_IN + LENDER → CLOSED` still fires. FOLIO then
   joins the same park-and-release path as every other ILS and the override below is deleted.
2. **Alternative — a distinct lender state** (e.g. `CANCELLED_AWAITING_RETURN`) that cancels the
   circulation request so it cannot re-capture, but stays eligible for check-in→`CLOSED` and does not
   return the item to Available while it is physically elsewhere.
3. **Not recommended — allow DCB to PUT `→ CLOSED` on a lender transaction.** It would unblock us, but
   DCB would be asserting a physical fact it does not know.

## 1. Decisions

- **Dedicated status `AWAITING_RETURN_TO_SUPPLIER`.** Not `CANCELLED` (which auto-finalises, deleting the
  records while the item is out — the bug). Not `RETURN_TRANSIT` either: when the patron cancels while the
  item sits on the pickup shelf, nothing is in transit anywhere — the item is **stranded and needs a human
  to send it back**. A distinct status makes that visible, filterable and alertable. That operational
  signal is the justification for the new state.
- **The park releases to `CANCELLED`, not back into the return leg.** Nothing was supplied. Rejoining
  `RETURN_TRANSIT` would end in `HandleSupplierItemAvailable` and stamp `Outcome.SUPPLIED` on a request
  nobody ever received. `FinaliseRequestTransition` then does the record cleanup it was always going to
  do — just at the point where deleting the virtual records no longer orphans a physical item.
- **`Outcome.CANCELLED` is set on both terminal paths.** `Outcome` is what reporting keys off, and both
  paths bypass `CancelledPatronRequestTransition`, which is otherwise the only thing that sets it.

## 2. State graph

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

 item still AT supplier (REQUEST_PLACED_*) ─► CANCELLED ─► FINALISED   (unchanged: nothing to orphan)
 item WITH the patron (item LOANED)        ─► LOANED               (a collection, not a cancellation)
```

## 3. The changes

1. **`PatronRequest.Status`** — new `AWAITING_RETURN_TO_SUPPLIER` value.

2. **`HostLmsClient.canReportItemReturnedAfterHoldTerminated()`** — new default-`true` capability;
   `ConsortialFolioHostLmsClient` overrides to `false`. Drives **flagging only** — see the warning in §0.

3. **`HandleCancelledRequestItemOut`** — the entry transition. Source = the 3 "out" states; guard =
   borrower (or pickup, for PUA) hold is `MISSING`/`CANCELLED` **and the item is not `LOANED`**. It
   terminates the supplier hold, verifies it, and **always parks**. No borrower LMS call (the hold is
   already gone).

   **There is no cancel-at-entry path, for any supplier.** Cancelling finalises; finalisation deletes the
   borrowing library's virtual item and bib; doing that while the item is out is the bug. A supplier that
   cannot report the return is parked and flagged (audit + a bounded-cardinality alarm keyed on the host
   LMS code), never finalised.

   **The item-status gate is load-bearing.** Sierra and Polaris consume the local hold on checkout, so
   "hold gone" also describes a completely normal collection. Tracking polls the request before the item
   and progresses the workflow once at the end of the cycle, so the engine sees the missing hold *and*
   the `LOANED` item in one context — and breaks the tie by **reverse-alphabetical transition name**
   (`PatronRequestWorkflowService.getPossibleStateTransitionsFor`), where `HandleCancelledRequestItemOut`
   outranks `HandleBorrowerItemLoaned`. Without the gate, every successful pickup is parked as a
   cancellation. Do not "fix" a future collision here by renaming.

4. **`HandleCancelledRequestItemReturned`** — releases a parked request to `CANCELLED` +
   `Outcome.CANCELLED` once the **supplier** item is `AVAILABLE`/`RECEIVED`. The borrower side cannot be
   used as a trigger: outbound and return transit share the same `TRANSIT` status, so a borrower-item
   gate fires at park time.

   Its guard is deliberately synchronous and narrow. A transition that is applicable but leaves the
   status untouched **loops forever** — `applyTransition` recurses into `progressAll`, which selects it
   again. So "can this supplier report a return?" is resolved once, at park time, not re-asked here.

5. **`ConsortialFolioHostLmsClient.deleteHold`** — while the item is out, `OPEN → CLOSED` is rejected, so
   it falls back to `CANCELLED`. Idempotent on a terminal transaction so finalisation cleanup does not
   error.

6. **`DefaultRequestTrackingPolicy`** — `AWAITING_RETURN_TO_SUPPLIER` is tracked `SUPPLIER`-only. Without
   an entry it fell through to `default -> Set.of()` and was polled on all three roles by the legacy
   fallback. An event-driven supplier is not polled here at all and must release via inbound lifecycle
   evidence — currently unimplemented, see §6.

7. **`application.yml`** — `dcb.polling.durations.AWAITING_RETURN_TO_SUPPLIER: 1h`, and
   **`DCBStartupEventListener`** seeds the status code as tracked/non-terminal.

8. **`CancelledPatronRequestTransition`** — narrowed to `{REQUEST_PLACED_AT_BORROWING_AGENCY,
   REQUEST_PLACED_AT_PICKUP_AGENCY}` (item still at supplier → auto-finalise is safe).

9. **`PatronRequestController.cleanupPatronRequest`** — rejects manual cleanup in every status meaning
   "the item is not back at the supplier yet", with **409 Conflict** (a `ThrowableProblem`; `micronaut-problem-json`
   renders it as RFC-7807) rather than the 500 an unhandled `IllegalStateException` produced. Support can
   override with **`?force=true`**, which is logged and audited — they do occasionally need to clear a
   genuinely stuck request, so this is a speed bump, not a wall.

   Guards on **stored** state: polling first would let automatic progression move the request underneath
   the guard, so the caller would get an error about a state they never asked about, having already
   mutated the request.

   N.B. `status` is a reserved Problem property — passing it to `.with()` throws.

## 3a. Which hold does the patron actually cancel?

The patron's own hold is the one at their **home (borrowing)** library — including under Pickup Anywhere.
The pickup hold is one DCB places against a *virtual* patron so the item can sit on the pickup shelf;
`CancelledPatronRequestTransition` treats the borrower hold as the trigger and then tears the pickup hold
down as a consequence.

So `HandleCancelledRequestItemOut` watches the **borrower** hold always, plus the **pickup** hold for PUA
(losing that also means the item is out with nothing holding it). Watching only the pickup hold for PUA —
as an earlier revision of this branch did — silently dropped every PUA patron cancellation: once the item
is out, `CancelledPatronRequestTransition` no longer claims those states either, so nothing moved the
request and it never reached `FINALISED`. Covered by
`shouldBeApplicableForPickupAnywhereWhenThePatronCancelsAtTheirHomeLibrary`.

## 4. Deleted

- **`HandleCancelledRequestReturnTransit`** — its guard was identical to `HandleSupplierItemAvailable`'s,
  so it existed only to hop into `RETURN_TRANSIT` so that transition could run on a *later* poll. A no-op
  hop costing an hour.
- **`HandleBorrowerSkippedLoanTransit`** — subsumed. Its guard (hold `MISSING`, item
  `TRANSIT`/`MISSING`/`AVAILABLE`, source `PICKUP_TRANSIT`/`READY_FOR_PICKUP`) was a strict subset of the
  new transition's, so which of the two ran was decided by class name. It also jumped to `RETURN_TRANSIT`
  **without terminating the supplier hold** — the bug being fixed.

  **Behaviour change worth flagging:** a genuinely missed loan is indistinguishable from a cancellation —
  in both cases the hold is gone, the item is out, and DCB never saw a loan. Those requests now park and
  record `Outcome.CANCELLED` rather than completing as supplied. The physical handling is identical and
  correct either way; only the label differs.

## 5. Reverted (collateral damage from earlier commits on this branch)

- **`BorrowingAgencyService.cleanUp`'s `TRANSIT`/`LOANED` guard.** `TRANSIT` is the *normal* value of the
  borrower's `localItemStatus` at cleanup time — `HandleBorrowerRequestReturnTransit` fires *because* it
  is `TRANSIT`, nothing moves it off, and polling stops at `COMPLETED`. Gating deletion on it orphaned a
  virtual item and bib at the borrowing library for **every completed loan**, with nothing ever coming
  back to clean them up. Covered by
  `FinaliseRequestTransitionTests.shouldDeleteVirtualRecordsEvenWhenTheBorrowerItemIsStillInTransit`.
  Unnecessary now anyway: the request only finalises once the item is home.
- **`PickupAgencyService`'s status guard** — unreachable. `FinaliseRequestTransition` runs cleanup while
  the status is still `COMPLETED`/`CANCELLED`, and the manual API now rejects those states.
- **`HandleSupplierItemAvailable`'s `onErrorResume`** — only needed because the park path used to route
  through `RETURN_TRANSIT` and poke an already-terminal borrower. It also swallowed genuine failures on
  the normal return leg.

## 5a. A FOLIO patron cancelling: does it reach FINALISED?

Traced end to end. **Yes**, in every combination — but only because of the PUA fix in §3a; before it, the
PUA case stalled indefinitely.

Detection: the patron cancels their FOLIO request → Kafka `CANCEL` event →
`CirculationEventListener.processRequestEvent` → `cancelTransactionEntity` for **any** role, so the
borrower transaction goes `CANCELLED`. DCB's `getRequest` maps mod-dcb `CANCELLED` → `HOLD_CANCELLED`
(`mapToHostLmsRequest`), so `localRequestStatus` is a recognised cancellation. The borrower's *item*
status maps through `mapToItemStatus`'s `default` branch to a passthrough `"CANCELLED"` — importantly not
`LOANED`, so the collection gate does not exclude it.

| Item is | Supplier | Path | Ends |
|---|---|---|---|
| still at supplier | any | `CancelledPatronRequestTransition` | `CANCELLED` → `FINALISED` |
| out | Sierra/Polaris/Alma | park → supplier item `AVAILABLE` → release | `CANCELLED` → `FINALISED` |
| out | FOLIO | park → Inventory reports `Available` → release | `CANCELLED` → `FINALISED` |
| out, PUA | any | borrower hold watched (§3a) | as above |

Every combination reaches `FINALISED` on its own, and only once the real item is confirmed home. Nothing
in the workflow layer distinguishes the supplier.

## 5b. Why only Polaris libraries reported this

The premature-deletion complaint tracks the **borrower's** ILS exactly, and the reason is in the adapters:

| Borrower | `deleteItem` / `deleteBib` |
|---|---|
| Polaris | real — `ApplicationServices.deleteItemRecord` / `deleteBibliographicRecord` |
| Sierra | real — `client.deleteItem` / `deleteBib` |
| FOLIO | **no-ops** — *"Delete virtual item is not currently implemented for FOLIO"*, returns `OK` |

A FOLIO borrower's "virtual item" is a mod-dcb **circulation item**, whose lifecycle mod-dcb owns. DCB
never deletes it, so finalising early at a FOLIO borrower destroyed nothing and nobody noticed. At a
Polaris or Sierra borrower the records really are DCB's to delete, and deleting them while the item was
still out left the library with no record to check the item back in against — so the supplier billed them
for a lost item.

Two consequences worth keeping in mind:

- **FOLIO borrowers were never evidence that the timing was correct** — only that DCB had nothing to
  break there. Their silence is the absence of a symptom, not the absence of the bug. That is exactly the
  trap the reverted early-cancel fell into: it was reasoned about from the FOLIO side and shipped against
  a Polaris one.
- **Anything that fixes the timing must be judged on the borrower**, which is why the park is now
  unconditional and no FOLIO-as-borrower carve-out was added — it would have bought nothing.

Finalisation against a FOLIO **borrower** completes because:

- `deleteRequestIfPresent` → `deleteHold` sees a `CANCELLED` transaction and short-circuits to
  `RESULT_OK` without mutating it (§3.5). Without that terminal check it would attempt `CLOSED`, be
  rejected, and cancel an already-cancelled transaction.
- `deleteItem` and `deleteBib` are deliberate no-ops for FOLIO — mod-dcb owns those records — so there is
  nothing left to fail.
- While parked, only the SUPPLIER role is polled (§3.6), so DCB stops interrogating a terminal borrower
  transaction it can learn nothing more from.

`FinaliseRequestTransition` runs in the same recursive progression as the release, so `FINALISED` is
reached immediately rather than on the next poll.

## 6. Known follow-ups (separate issues)

- **Watch the Inventory fallback in production.** It rests on FOLIO leaving the item `In transit`
  (reason "DCB cancelled") until it is checked in at the owning library — confirmed against a real
  tenant, and covered by `ConsortialFolioHostLmsClientGetItemTests`. If a tenant is ever seen flipping
  the item straight to `Available` on cancellation, the fallback would release the park instantly and
  reintroduce the bug; the containment is to override
  `canReportItemReturnedAfterHoldTerminated()` to `false` on the FOLIO client again, which restores the
  parked-and-flagged behaviour in one line.
- **The mod-dcb asks in §0 are still worth making.** The Inventory fallback works around a limitation
  rather than removing it: a lender transaction that could still receive its check-in would let DCB use
  one channel instead of two, and would benefit anyone else integrating with mod-dcb.

- **Declarative suppliers cannot have their hold terminated.** `HandleCancelledRequestItemOut` skips and
  audits, matching `CancelledPatronRequestTransition`. Until a declarative cancel exists, such a request
  parks with a live supplier hold and will not release itself.
- **Event-driven suppliers have no release path.** The release keys off polled supplier item status;
  under `TrackingMode.EVENT_DRIVEN` that is never populated. Needs `InboundLifecycleMessageHandler`
  support.
- **A parked request whose hold survived termination never releases.** The verification audits the
  outcome but does not alarm. Wants an `AlarmsService` hook.
- **Borrower-side routing:** when the patron cancels mid-transit, the borrower (e.g. Polaris) shelves the
  returned item locally as `Available` rather than routing it back to the supplier. DCB can't automate
  this; the park state is what makes it visible.
- **The admin UI has no affordance for `?force=true`.** The API supports it; the UI still just hides the
  cleanup button for out-item statuses. A "clean up anyway" confirmation behind it would save support a
  curl.
- **`dcb-hub-admin-ui/src/helpers/statuses.ts`** duplicates `constants/statuses/*` and is imported by
  nothing. Delete it before the stale copy misleads someone.

## 7. Cross-stack

`dcb-admin-ui` updated: `DCBStatuses.ts` (enum → filter dropdown), `cleanupStatuses.ts` (kept in step with
the API guard), `useChartPalette.ts` (`STATUS_ORDER`), and the `en-GB` + `es` locale files.

## 8. Verify

- `./gradlew :dcb:compileJava :dcb:compileTestJava` — clean.
- Full suite: `./gradlew :dcb:test` — green (12m53s).
- Regression tests that must never be deleted:
  - `HandleCancelledRequestItemOutTests.patronCollectingTheItemMustNotBeTreatedAsACancellation`
  - `HandleCancelledRequestItemOutTests.pickupAnywhereCollectionMustNotBeTreatedAsACancellation`
  - `HandleCancelledRequestItemOutTests.shouldBeApplicableForPickupAnywhereWhenThePatronCancelsAtTheirHomeLibrary`
  - `FinaliseRequestTransitionTests.shouldDeleteVirtualRecordsEvenWhenTheBorrowerItemIsStillInTransit`
  - `PatronRequestCleanupGuardTests` (409 + force override)

## Definition of done

A patron hold vanishing while the item is out terminates the supplier hold (verified) so the returning
item is not re-captured, and the request holds its records until the item is genuinely back at the
supplier — then cancels and finalises, recording `Outcome.CANCELLED`. A **FOLIO** supplier cancels at
entry because it provably cannot report the return. A patron **collecting** the item is unaffected.
Supplier cancellation / re-resolution is unchanged.

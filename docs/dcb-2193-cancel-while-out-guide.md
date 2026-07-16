# DCB-2193 — "Patron cancelled while the item is out": how the solution works

When a patron cancels their local hold while the borrowed item is physically **out** of the supplying
library, DCB used to auto-cancel and finalise the request — deleting the borrowing library's virtual
records and orphaning the physical item (the supplier writes it off as lost). This document describes
the implemented solution on `dcb-2193-new-transition`. The handling **diverges by supplier ILS**,
because what "terminate the hold" does — and whether the item's physical return can be tracked at all —
differs fundamentally between FOLIO and the others.

## 0. The two mechanics you must internalise

**(a) The supplier hold MUST be terminated, or the returning item is re-captured.** The supplier-side
checkout that normally consumes the hold only happens once the patron actually **loans** the item. Here
they cancelled before loaning, so the hold is still live. Left in place, when the item is checked back
in at the supplier it is re-routed straight back out to the borrower:
- **Polaris / Sierra / Alma** re-fill the native hold (Polaris reports **"transfer for hold"**).
- **FOLIO** re-fulfils the still-open mod-dcb transaction / circulation request.

So DCB terminates the hold via `SupplyingAgencyService.cleanUp` (`HoldOperation.DELETE`); each client
implements the correct terminal operation. For Sierra/Polaris/Alma that's a native hold delete. For
FOLIO, `deleteHold` tries to CLOSE the transaction and — because mod-dcb rejects `→ CLOSED` while the
item is out — falls back to CANCELLING it (see §mod-dcb).

**(b) After termination, only *some* ILSs can still tell us the item came home.** This is the crux, and
it splits the flow:
- **Sierra / Polaris / Alma:** the supplier's **real inventory item** is tracked independently of the
  hold. It stays "in transit / checked out" until physically checked in, then reports `AVAILABLE`. That
  is a reliable "physically back" signal — so we **park** the request in `AWAITING_RETURN_TO_SUPPLIER`
  and release it only when the real item is genuinely back.
- **FOLIO:** the mod-dcb transaction is *both* the hold and the only tracking channel. Cancelling it
  (step a) returns the item to `AVAILABLE` in inventory **immediately** (mod-dcb releases it), regardless
  of where the item physically is, and permanently severs any `CLOSED` signal. So there is **no** signal
  DCB can wait on — and nothing is orphaned at the supplier (the item is back in available inventory as
  far as FOLIO is concerned). So we **finalise immediately** (`CANCELLED → FINALISED`).

This is expressed as a client capability, not `if (ils == …)`:
`HostLmsClient.cancellingSupplierHoldReleasesItem()` — FOLIO returns `true`, everyone else `false`.

### mod-dcb evidence (folio-org/mod-dcb, lender role)

- **`→ CLOSED` cannot be forced by DCB.** `LendingLibraryServiceImpl.updateTransactionStatus` has no
  `→ CLOSED` case — a PUT to `CLOSED` throws "not implemented" (this is the "Unexpected response from Host
  LMS" DCB logs). For a lender, `CLOSED` is only reached by a physical check-in **event**
  (`CirculationEventListener.processDcbTransactionEntity`: `CHECK_IN + LENDER → CLOSED`).
- **Cancel releases the item.** PUT `CANCELLED` → `cancelTransactionRequest` → `CirculationServiceImpl.cancelRequest`
  cancels the underlying FOLIO circulation (page) request, which returns the previously-paged item to
  `Available`. So RTAC `AVAILABLE` after our cancel means "we cancelled", not "it came home".
- **No path back to `CLOSED` after cancel.** The loan-check-in→`CLOSED` handler uses
  `findTransactionByItemIdAndStatusNotInClosed`, whose SQL is `status NOT IN ('CLOSED','CANCELLED','ERROR','CREATED','OPEN')`
  — `CANCELLED` (and `OPEN`) are excluded, so a later physical check-in can never move the transaction to
  `CLOSED`. The check-in-topic handler only closes `EXPIRED` transactions.

That trio is why a FOLIO supplier has no trackable physical return once the hold is terminated, and why
Option A (finalise) is the right call for FOLIO specifically.

## 1. Decisions

- **Dedicated status `AWAITING_RETURN_TO_SUPPLIER`, not `CANCELLED`, for the park case.** `CANCELLED`
  auto-finalises (`FinaliseRequestTransition`), which would delete the virtual records on the next tick —
  the exact orphan bug. So Sierra/Polaris/Alma park in the dedicated status. (FOLIO deliberately *does*
  use `CANCELLED` → finalise, because for FOLIO there is nothing to orphan.)
- **Preserve `RETURN_TRANSIT`.** A parked request rejoins the existing return leg; completion stays in
  `HandleSupplierItemAvailable` (source `RETURN_TRANSIT`).

## 2. State graph

```
                 patron hold gone, item OUT; cleanUp() the supplier hold
 PICKUP_TRANSIT ─┐
 RECEIVED_AT_PICKUP ─┼─(HandleCancelledRequestItemOut)─┬─ supplier releases item on cancel (FOLIO)
 READY_FOR_PICKUP ─┘                                   │      └─► CANCELLED ─► FINALISED
                                                       │
                                                       └─ supplier does NOT (Sierra/Polaris/Alma)
                                                              └─► AWAITING_RETURN_TO_SUPPLIER
                                                                    └─(real item AVAILABLE at supplier)─►
                                                                       RETURN_TRANSIT ─► COMPLETED ─► FINALISED

 item still AT supplier (REQUEST_PLACED_*) ─► CANCELLED ─► FINALISED   (unchanged: nothing to orphan)
```

## 3. The changes

1. **`PatronRequest.Status`** — new `AWAITING_RETURN_TO_SUPPLIER` value (the park state).

2. **`HostLmsClient.cancellingSupplierHoldReleasesItem()`** — new default-`false` capability.
   `ConsortialFolioHostLmsClient` overrides it to `true`. This is the single point that drives the
   park-vs-finalise divergence; no ILS branching in workflow code.

3. **`HandleCancelledRequestItemOut`** — the entry transition. Source = the 3 "out" states; guard =
   borrower hold (or pickup hold for PUA) is `MISSING`/`CANCELLED`. Action: `cleanUp` the supplier hold,
   then branch on the capability — `CANCELLED` (finalise) if the supplier releases the item on cancel,
   else `AWAITING_RETURN_TO_SUPPLIER` (park). No borrower LMS call (the borrower hold is already gone).

4. **`ConsortialFolioHostLmsClient.deleteHold`** — while the item is out, `OPEN → CLOSED` is rejected, so
   it falls back to `CANCELLED` (returns `RESULT_OK_CANCELLED`). It is also **idempotent on a terminal
   transaction** (`CLOSED`/`CANCELLED`/`ERROR` → `RESULT_OK`, no mutation) so the finalisation cleanup,
   which re-runs `deleteHold` after we already cancelled at park time, does not error.

5. **`HandleCancelledRequestReturnTransit`** — releases a *parked* request (Sierra/Polaris/Alma). Gated
   **solely** on the supplier having the item back: supplier item `AVAILABLE`/`RECEIVED`. The borrower
   side is **not** a trigger — outbound and return transit share the same `TRANSIT` status, so a
   borrower-item gate misfires at park time. FOLIO never reaches this transition (it finalised at entry),
   so there is no FOLIO transaction-`CLOSED` release path here.

6. **`HandleSupplierItemAvailable`** — source `RETURN_TRANSIT`. The borrower "received back" poke is
   best-effort (`onErrorResume`): a terminal (`CANCELLED`) FOLIO/Alma borrower rejects the poke, which must
   not wedge the request in `RETURN_TRANSIT`.

7. **`PatronRequestWorkflowPath`** — `AWAITING_RETURN_TO_SUPPLIER → RETURN_TRANSIT` next-expected hint.

8. **`DCBStartupEventListener`** — seeds `saveOrUpdateStatusCode("DCBRequest","AWAITING_RETURN_TO_SUPPLIER",TRUE)`.
   Keeps the parked request classified as tracked/non-terminal. It does **not**, on its own, schedule
   polling — see item 9.

9. **`application.yml` — `dcb.polling.durations.AWAITING_RETURN_TO_SUPPLIER: 1h`.** The periodic tracker
   (`TrackingServiceV3.run` → `findScheduledChecks`) is driven by `next_scheduled_poll`, which
   `scheduleNextCheck` sets from `dcb.polling.durations`. With no entry, `next_scheduled_poll` is `null`
   and the parked request is never re-polled. (Only relevant to the park path; FOLIO finalises immediately.)

10. **`CancelledPatronRequestTransition`** — narrowed to `{REQUEST_PLACED_AT_BORROWING_AGENCY,
    REQUEST_PLACED_AT_PICKUP_AGENCY}` (item still at supplier → auto-finalise is safe).

## 4. What is NOT touched

- **Supplier cancellation / re-resolution** (`HandleSupplierRequestCancelled` → re-resolution) keys off
  the supplier hold `localStatus` and is terminal-vs-non-terminal separate from this patron-cancellation
  path. The new status is not added to any supplier-side transition.
- **`FinaliseRequestTransition`** — `CANCELLED`/`COMPLETED` auto-finalise unchanged; the FOLIO path and
  the released park path both reach `FINALISED` through it.

## 5. Tests

- **`HandleCancelledRequestItemOutTests`**
  - Sierra supplier (capability false) → parks in `AWAITING_RETURN_TO_SUPPLIER` and deletes the hold
    (`verifyDeleteHoldRequestMade`).
  - FOLIO supplier (capability true) → sets `CANCELLED` (finalise path), with the CLOSE→CANCEL deleteHold.
  - Release only on supplier item back (`AVAILABLE`/`RECEIVED`); regression that a borrower item merely
    in `TRANSIT` does **not** release.
- **`ConsortialFolioHostLmsClientDeleteHoldTests`** — CLOSE rejected → falls back to CANCEL
  (`RESULT_OK_CANCELLED`); already-`CLOSED`/`CANCELLED`/`ERROR` → `RESULT_OK`, no mutation.
- **`DummyScenarioTests`** — end-to-end park→release (Dummy supplier, capability false).

## 6. Known follow-ups (separate issues)

- **Borrower-side routing:** when the patron cancels mid-transit, the borrower (e.g. Polaris) shelves the
  returned item locally as `Available` rather than routing it back to the supplier — the physical item can
  end up stranded at the borrower. DCB can't automate this return; tracked separately.
- Pre-existing branch scope-creep to split out: FOLIO `getItemByBarcode` `JsonNode` rewrite; `ping()`
  returning `Mono.empty()` on error instead of `PING_STATUS_ERROR`; deleted docs/mock JSON;
  `BorrowingAgencyService` magic strings; `PatronRequestController.cleanupPatronRequest` dropping a publisher.

## 7. Verify

- `./gradlew :dcb:compileJava :dcb:compileTestJava` — clean.
- Targeted:
  `./gradlew :dcb:test --tests "*HandleCancelledRequestItemOutTests" --tests "*ConsortialFolioHostLmsClient*Tests" --tests "*CancelledPatronRequestTransitionTests" --tests "*DummyScenarioTests" --tests "*DCBStartupEventListenerTests"`
- Full gate before merge: `timeout 30m ./gradlew test --no-daemon --no-build-cache --rerun-tasks`.

## Definition of done

Cancel-while-out terminates the supplier hold so the returning item is not re-captured. A **FOLIO**
supplier finalises immediately (`CANCELLED → FINALISED`) — cancelling cleanly releases the item to
available inventory and there is no trackable physical return. A **Sierra/Polaris/Alma** supplier parks
in `AWAITING_RETURN_TO_SUPPLIER` (records intact, still tracked) and finalises only once the real item is
physically back. Supplier cancellation / re-resolution is unchanged.

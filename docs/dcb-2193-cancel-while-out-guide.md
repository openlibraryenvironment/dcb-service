# DCB-2193 — "Patron cancelled while the item is out": path to a working solution

You are close. The state-model *shape* on `dcb-2193-new-transition` is right; two things sink it. This guide takes you from the current branch to a mergeable solution without touching mod-dcb and without going near supplier cancellation.

## 0. The two things you must internalise

**(a) For a FOLIO supplier, the mod-dcb transaction IS the tracking channel.** DCB observes the item coming home only because the transaction reaches `CLOSED` (lender check-in → `CLOSED` → DCB maps it to item `AVAILABLE`). If you **cancel** that transaction, mod-dcb's check-in listener stops seeing it (`findTransactionByItemIdAndStatusNotInClosed` excludes `CANCELLED`/`ERROR`), so it **never** reaches `CLOSED`, DCB **never** sees `AVAILABLE`, and the request is orphaned forever.

**(b) But the supplier hold MUST still be removed — because in this flow nothing ever consumed it.** The supplier-side checkout that normally consumes the hold (`checkOutItemToPatron`) is only called from `HandleBorrowerItemLoaned` — i.e. only when the patron actually **loans** the item. Here the patron cancelled *before* loaning, so the hold is still live. Left in place it re-captures the item when it is checked back in at the supplier — Polaris reports **"transfer for hold"** and routes it straight back out — so the item never becomes `AVAILABLE` and the request can never complete. *This is why the normal return leg works and this one does not.*

**Conclusion — the operation matters more than the intent. Use `SupplyingAgencyService.cleanUp` (`HoldOperation.DELETE`), never `cancelHold` (`HoldOperation.CANCEL`):**

| | `cancelHold()` → `cancelHoldRequest` | `cleanUp()` → `deleteHold` |
|---|---|---|
| **FOLIO supplier** | Sets transaction `CANCELLED` → **kills tracking, orphans request** | Attempts `CLOSED`; mod-dcb rejects it (lender close processor is `manual`, and `process()` validates the whole chain **before** mutating, so nothing changes); client swallows it as `RESULT_OK_NOT_RESOLVED` → **transaction intact**, closes naturally on check-in ✓ |
| **Polaris / Sierra / Alma** | cancels hold | **deletes the hold** → no re-capture → item goes `AVAILABLE` ✓ (Polaris's delete/fallback-cancel quirk is already handled inside `deleteHold`) |

`deleteHold` is the existing, ILS-aware abstraction — no `if (ils == POLARIS)` needed. The original branch's instinct (cancel the supplier hold) was **right**; only the operation was wrong.

## 1. Decisions (already made — don't relitigate on the branch)

- **Use a dedicated status `AWAITING_RETURN_TO_SUPPLIER`, not `CANCELLED`.** `CANCELLED` is auto-finalised by `FinaliseRequestTransition` (`attemptAutomatically()==true`, source includes `CANCELLED`). Parking a still-out request in `CANCELLED` deletes the virtual records on the next tick — the exact bug. Making `CANCELLED` durable means guarding the most safety-critical terminal transition for every cancellation path. Not worth it. (Show "Cancelled (awaiting return)" in the UI by mapping the new status — a display concern.)
- **Preserve `RETURN_TRANSIT`.** The parked request rejoins the *existing* return leg; completion stays in `HandleSupplierItemAvailable`. Do not extend `HandleSupplierItemAvailable` to complete straight from the new status — that bypasses `RETURN_TRANSIT`.

## 2. Target state graph

```
                 patron hold gone, item OUT
 PICKUP_TRANSIT ─┐
 RECEIVED_AT_PICKUP ─┼─► AWAITING_RETURN_TO_SUPPLIER ─► RETURN_TRANSIT ─► COMPLETED ─► FINALISED
 READY_FOR_PICKUP ─┘   (HandleCancelledRequestItemOut) │  (HandleCancelled  (HandleSupplier (Finalise
                        DELETEs the supplier hold;      │   RequestReturn     ItemAvailable)  Request)
                        no borrower LMS calls           │   Transit, once the
                                                        │   supplier has it back)
 item still AT supplier (REQUEST_PLACED_*) ─► CANCELLED ─► FINALISED   (unchanged: auto-finalise is fine, nothing to orphan)
```

## 3. Concrete changes

1. **`PatronRequest.Status`** — keep the new `AWAITING_RETURN_TO_SUPPLIER` enum value. ✔ (already on branch)

2. **`HandleCancelledRequestItemOut`** — the entry transition. Source = the 3 "out" states; guard = borrower hold (or pickup hold for PUA) is `MISSING`/`CANCELLED`; action = **`supplyingAgencyService.cleanUp(ctx)` (DELETE the supplier hold), then audit + set `AWAITING_RETURN_TO_SUPPLIER`.**
   - Replace `cancelSupplierHoldIfPresent` (which called `cancelHold`/CANCEL — FOLIO-fatal) with `cleanUp` (DELETE — FOLIO-safe). Keep the `SupplyingAgencyService` dependency. See §0(b): the hold must go, or Polaris re-captures the returning item.
   - `cleanUp` is already defensive (it audits and swallows its own failures via `logAndReturnErrorString` rather than erroring the transition), so no extra error handling is needed here.
   - No borrower LMS call — the borrower hold is already gone; that's the trigger.
   - Fix the docstring: it currently says "routes to RETURN_TRANSIT" — it parks in `AWAITING_RETURN_TO_SUPPLIER`.

3. **`HandleCancelledRequestReturnTransit`** — new release transition. Source = `AWAITING_RETURN_TO_SUPPLIER`; action = set `RETURN_TRANSIT`. No LMS calls.
   - **Guard must be driven by the SUPPLIER item being back** (`AVAILABLE`/`RECEIVED`, or FOLIO-supplier transaction `CLOSED`) — the only signal reliable across every ILS. Keep the borrower/pickup item `TRANSIT`/`MISSING`/`AVAILABLE` check as a *secondary* early trigger, but never as the sole gate.
   - **Why (the FOLIO-as-borrower trap):** when the borrower is FOLIO, its virtual item status is a projection of the mod-dcb transaction. Once the patron cancels, that transaction is terminal `CANCELLED`, DCB maps it to a passthrough item status `"CANCELLED"` (not `TRANSIT`), and mod-dcb never advances it. A borrower-only gate therefore strands the request in `AWAITING` forever even after the supplier (e.g. Polaris) reports the item `AVAILABLE`. Same mod-dcb terminal-state trap as the supplier side — do not depend on a cancelled FOLIO transaction to emit item-lifecycle signals, on **either** side.
   - (Alternative if you prefer reuse over a new class: add `AWAITING_RETURN_TO_SUPPLIER` to `HandleBorrowerSkippedLoanTransit`'s sources — but it, too, gates on the borrower item status, so it would hit the same FOLIO-borrower stall unless you add the supplier-back check. A dedicated class carrying the supplier-back guard is cleaner.)

4. **`HandleSupplierItemAvailable`** — **revert** the branch's source-list change (back to `RETURN_TRANSIT` only). **Also make the borrower "received back" poke best-effort.** In the normal flow the borrower's mod-dcb transaction is `ITEM_CHECKED_IN` when this fires, so `updateItemStatus(COMPLETED) → CLOSED` is valid. In the cancel-while-out flow the borrower transaction is terminal `CANCELLED`, and mod-dcb only allows `→ CLOSED` from `ITEM_CHECKED_IN` (`BorrowingLibraryServiceImpl`) — so the call **throws** and wedges the request in `RETURN_TRANSIT`. Wrap the poke in `onErrorResume` that audits and continues to `COMPLETED`. This mirrors the existing defensive pattern in FOLIO `deleteHold` (which already returns `RESULT_OK_NOT_RESOLVED` for exactly this mod-dcb limitation) and finalisation cleanup, which already tolerate it.
   - **Not FOLIO-only:** Sierra and Polaris no-op `updateItemStatus(COMPLETED)`, but **Alma** performs a real `scanIn` on every state, so an Alma borrower's poke can also fail on a cancelled virtual item. The best-effort wrapper is the general fix; do not special-case FOLIO.

5. **`PatronRequestWorkflowPath`** — set the next-expected hint to `AWAITING_RETURN_TO_SUPPLIER → RETURN_TRANSIT` (branch currently has `→ COMPLETED`). Without this, tracking's auto-progress and "next expected status" break.

6. **`CancelledPatronRequestTransition`** — keep the branch's narrowing to `{REQUEST_PLACED_AT_BORROWING_AGENCY, REQUEST_PLACED_AT_PICKUP_AGENCY}` (item-still-at-supplier → auto-finalise). Just fix the two comments that still say "routes to RETURN_TRANSIT."

7. **`DCBStartupEventListener`** — keep `saveOrUpdateStatusCode("DCBRequest", "AWAITING_RETURN_TO_SUPPLIER", TRUE)`. ✔ (already on branch — this is what makes tracking poll the parked request; do not drop it.)

## 4. What you must NOT touch

- **Supplier cancellation / re-resolution.** `HandleSupplierRequestCancelled` (keys off `SupplierRequest.localStatus`) → `NOT_SUPPLIED_CURRENT_SUPPLIER` → `ResolveNextSupplierTransition`. This is a *different, non-terminal* concern (it can find a new supplier). Your path is driven by the **borrower/pickup** hold and is terminal. They never intersect — keep it that way. Don't add the new status to any supplier-side transition.
- **`FinaliseRequestTransition`.** Leave `CANCELLED` auto-finalise exactly as is.

## 5. Tests (the branch tests are Sierra-only, which is why every one of these bugs was invisible)

1. **Entry deletes the supplier hold.** Cancel-while-out on Sierra supplier → status `AWAITING_RETURN_TO_SUPPLIER` **and** `verifyDeleteHoldRequestMade(holdId)`. Audit entry present. (Guards the Polaris "transfer for hold" re-capture.)
2. **Release hop.** From `AWAITING_RETURN_TO_SUPPLIER`, item on shelf and supplier not yet back → **not** applicable; supplier item `AVAILABLE` (or borrower item `TRANSIT`) → applicable and moves to `RETURN_TRANSIT`.
3. **FOLIO-supplier lifecycle (the missing test).** Drive a FOLIO-supplied request to `READY_FOR_PICKUP`, cancel the borrower hold, run entry → `AWAITING_RETURN_TO_SUPPLIER`; assert **zero** `updateTransactionStatus(...CANCELLED)` calls to mod-dcb (the `deleteHold` attempt at `CLOSED` may be rejected — that's expected and harmless); then simulate the lender `CHECK_IN` → transaction `CLOSED` → supplier item `AVAILABLE` → request reaches `COMPLETED`/`FINALISED`. With `cancelHold` this test wedges in `AWAITING_RETURN_TO_SUPPLIER` — that is the regression guard.
   - **FOLIO-borrower release test.** Parked request with borrower `localItemStatus == "CANCELLED"` (FOLIO passthrough) and supplier item `AVAILABLE` → release transition **is** applicable and moves to `RETURN_TRANSIT`. This is the guard for the observed Polaris-supplier/FOLIO-borrower stall.
   - **FOLIO-borrower completion resilience test.** In `RETURN_TRANSIT` with supplier item `AVAILABLE` and a borrower client whose `updateItemStatus(COMPLETED)` throws (simulating mod-dcb rejecting `CANCELLED → CLOSED`), `HandleSupplierItemAvailable` must still reach `COMPLETED` (best-effort poke, audited). Without the fix the request wedges in `RETURN_TRANSIT`.
4. **Still-at-supplier path unchanged.** Cancel at `REQUEST_PLACED_*` still auto-finalises.

## 6. Revert the scope creep (separate PR, or drop from this branch)

None of this belongs in a cancellation ticket, and one item is a live regression:
- FOLIO `getItemByBarcode` rewritten to hand-rolled `JsonNode` + deletion of the typed inventory models and their 311-line test suite.
- `ping()` reformatted **and regressed** — it now returns `Mono.empty()` on error instead of `PING_STATUS_ERROR`. Restore the error branch.
- Deleted `docs/ncip-support-exploration-1.md` (911 lines) and FOLIO mock JSON.
- `BorrowingAgencyService` uses magic strings `"TRANSIT"`/`"LOANED"` — use `HostLmsItem` constants.
- `PatronRequestController.cleanupPatronRequest` calls `trackingService.forceUpdate(...)` and drops the result (unsubscribed Reactor publisher = no-op). Compose it into the chain or delete the line.

## 7. Verify

- `./gradlew compileTestJava` — clean.
- Targeted: `./gradlew test --tests "*HandleCancelledRequestItemOutTests" --tests "*CancelledPatronRequestTransitionTests" --tests "*DCBStartupEventListenerTests"` plus your new FOLIO test.
- Full gate before merge: `timeout 30m ./gradlew test --no-daemon --no-build-cache --rerun-tasks`.

## Definition of done

Cancel-while-out parks in `AWAITING_RETURN_TO_SUPPLIER` with **no** supplier or borrower LMS call; the request rejoins `RETURN_TRANSIT` when the item is routed home and finalises only when the supplier has it back; a FOLIO request completes end-to-end because the mod-dcb transaction was never cancelled; supplier cancellation/re-resolution behaviour is byte-for-byte unchanged.

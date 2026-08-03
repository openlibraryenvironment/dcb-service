package org.olf.dcb.request.workflow;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * HandleCancelledRequestReturnTransit.
 * A patron-cancelled-while-out request parked in AWAITING_RETURN_TO_SUPPLIER by
 * HandleCancelledRequestItemOut. It rejoins the normal return leg (RETURN_TRANSIT), from where the
 * existing HandleSupplierItemAvailable completes and finalises it. This preserves RETURN_TRANSIT
 * rather than bypassing it, and involves no LMS calls.
 *
 * Only Sierra / Polaris / Alma suppliers reach this transition: they track the real inventory item
 * independently of the DCB hold, so a cancelled-while-out request is parked here until the item is
 * physically back. A FOLIO supplier never parks - cancelling its transaction releases the item and
 * there is nothing to wait on, so HandleCancelledRequestItemOut finalises it at entry.
 *
 * Release is gated SOLELY on the SUPPLIER item being back (AVAILABLE / RECEIVED) - the one signal that
 * means the item has genuinely returned. We must NOT release on the borrower/pickup item reaching TRANSIT:
 * the outbound leg (item on its way TO the borrower) uses the exact same TRANSIT status, so a request
 * cancelled during PICKUP_TRANSIT would release immediately - before the item is anywhere near coming
 * back. There is no status that distinguishes outbound transit from return transit, so the borrower
 * side cannot be used as a trigger at all; the request stays parked in AWAITING_RETURN_TO_SUPPLIER
 * until the supplier actually has the item.
 */
@Slf4j
@Singleton
@Named("CancelledRequestReturnTransit")
public class HandleCancelledRequestReturnTransit implements PatronRequestStateTransition {
	private static final List<Status> possibleSourceStatus = List.of(Status.AWAITING_RETURN_TO_SUPPLIER);

	// The only reliable, ILS-agnostic release trigger: the supplier has the item back.
	// Mirrors HandleSupplierItemAvailable.
	private static final List<String> supplierItemBackStatus = List.of(
		HostLmsItem.ITEM_AVAILABLE, HostLmsItem.ITEM_RECEIVED);

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final var patronRequest = getValueOrNull(ctx, RequestWorkflowContext::getPatronRequest);
		final var status = getValueOrNull(patronRequest, PatronRequest::getStatus);

		if (status == null || !possibleSourceStatus.contains(status)) return false;
		if (getValueOrNull(patronRequest, PatronRequest::getActiveWorkflow) == null) return false;

		// Release only once the supplier actually has the item back. The borrower side cannot be used:
		// outbound and return transit share the same TRANSIT status, so a borrower-item gate misfires at
		// park time for requests cancelled during PICKUP_TRANSIT.
		return supplierHasItemBack(ctx);
	}

	private static boolean supplierHasItemBack(RequestWorkflowContext ctx) {
		final var supplierRequest = getValueOrNull(ctx, RequestWorkflowContext::getSupplierRequest);
		if (supplierRequest == null) return false;

		final var supplierItemStatus = getValueOrNull(supplierRequest, SupplierRequest::getLocalItemStatus);

		// N.B. supplierItemBackStatus is an immutable List, whose contains(null) throws - guard it.
		return supplierItemStatus != null && supplierItemBackStatus.contains(supplierItemStatus);
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		ctx.getPatronRequest().setStatus(Status.RETURN_TRANSIT);
		return Mono.just(ctx);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.RETURN_TRANSIT);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleCancelledRequestReturnTransit";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition(
			"DCBPatronRequest state is AWAITING_RETURN_TO_SUPPLIER and the supplier has the item back "
				+ "(supplier item AVAILABLE/RECEIVED)"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("RETURNING", Status.RETURN_TRANSIT.toString()));
	}
}

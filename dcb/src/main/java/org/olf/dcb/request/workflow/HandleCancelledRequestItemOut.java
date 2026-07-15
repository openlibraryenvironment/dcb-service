package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * HandleCancelledRequestItemOut.
 * The patron has cancelled (or lost) their local hold while the item is physically "out" of the
 * supplying library - the DCB status is PICKUP_TRANSIT, RECEIVED_AT_PICKUP or READY_FOR_PICKUP.
 * Finalising here would delete the borrowing library's virtual records and orphan the item, so
 * instead we park the request in AWAITING_RETURN_TO_SUPPLIER: the durable "patron cancelled, item
 * still out, we do not yet know whether it has been sent back" state. Records stay intact and the
 * request stays tracked.
 *
 * We deliberately do NOT touch the supplier hold/transaction here. The supplier record is the
 * mechanism by which we observe the item coming home:
 *  - FOLIO: the mod-dcb transaction IS the tracking channel. Cancelling it makes mod-dcb ignore the
 *    eventual lender check-in event (its lookup excludes CANCELLED/ERROR transactions), so the
 *    transaction never reaches CLOSED and the supplier item is never reported AVAILABLE - the request
 *    would be orphaned forever.
 *  - Sierra / Polaris / Koha / Alma: while the item is out its capturing hold has already been
 *    consumed into the supplier loan; there is nothing to cancel, and the existing return leg never
 *    cancels a supplier hold anyway.
 *
 * Once the borrower routes the item back (virtual item goes TRANSIT), HandleCancelledRequestReturnTransit
 * moves the request onto the normal RETURN_TRANSIT return leg, which completes and finalises it when the
 * supplier has the item back. NOTE: this is the PATRON cancellation path (borrower/pickup hold gone).
 * Supplier cancellation is a different, non-terminal concern handled by HandleSupplierRequestCancelled
 * and re-resolution - this transition never interacts with it.
 */
@Slf4j
@Singleton
@Named("CancelledRequestItemOut")
public class HandleCancelledRequestItemOut implements PatronRequestStateTransition {
	private static final List<Status> possibleSourceStatus = List.of(
		Status.PICKUP_TRANSIT,
		Status.RECEIVED_AT_PICKUP,
		Status.READY_FOR_PICKUP
	);
	private static final List<String> cancelledHoldStatus = List.of(HOLD_MISSING, HOLD_CANCELLED);

	static final String ITEM_OUT_HELD_AWAITING_RETURN =
		"CancelledRequestItemOut : patron cancelled while item is out, awaiting its return to the supplier";

	private final PatronRequestAuditService patronRequestAuditService;

	public HandleCancelledRequestItemOut(PatronRequestAuditService patronRequestAuditService) {
		this.patronRequestAuditService = patronRequestAuditService;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final var patronRequest = getValueOrNull(ctx, RequestWorkflowContext::getPatronRequest);
		final var status = getValueOrNull(patronRequest, PatronRequest::getStatus);

		if (status == null || !possibleSourceStatus.contains(status)) return false;
		if (getValueOrNull(patronRequest, PatronRequest::getActiveWorkflow) == null) return false;

		// For Pickup Anywhere the patron holds against the pickup system, otherwise the borrower.
		final var holdStatus = patronRequest.isUsingPickupAnywhereWorkflow()
			? getValueOrNull(patronRequest, PatronRequest::getPickupRequestStatus)
			: getValueOrNull(patronRequest, PatronRequest::getLocalRequestStatus);

		return cancelledHoldStatus.contains(holdStatus);
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		final var patronRequest = ctx.getPatronRequest();

		// Park the request only. No supplier or borrower LMS calls: the borrower hold is already gone
		// (that is the trigger), and the supplier record must stay live so we can observe the return.
		return patronRequestAuditService.addAuditEntry(patronRequest,
				ITEM_OUT_HELD_AWAITING_RETURN, buildAuditData(patronRequest))
			.doOnSuccess(audit -> patronRequest.setStatus(Status.AWAITING_RETURN_TO_SUPPLIER))
			.thenReturn(ctx);
	}

	private static HashMap<String, Object> buildAuditData(PatronRequest patronRequest) {
		var auditData = new HashMap<String, Object>();
		auditData.put("dcb-patron-request-status-on-entry", patronRequest.getStatus());
		auditData.put("local-patron-request-status-on-entry", patronRequest.getLocalRequestStatus());
		auditData.put("pickup-patron-request-status-on-entry", patronRequest.getPickupRequestStatus());
		auditData.put("virtual-item-status-on-entry", patronRequest.getLocalItemStatus());
		auditData.put("pickup-item-status-on-entry", patronRequest.getPickupItemStatus());
		return auditData;
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.AWAITING_RETURN_TO_SUPPLIER);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleCancelledRequestItemOut";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition(
			"DCBPatronRequest state is PICKUP_TRANSIT, RECEIVED_AT_PICKUP or READY_FOR_PICKUP and the patron hold is MISSING or CANCELLED"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("HELD", Status.AWAITING_RETURN_TO_SUPPLIER.toString()));
	}
}

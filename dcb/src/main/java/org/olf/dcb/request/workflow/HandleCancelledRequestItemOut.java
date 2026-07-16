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
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
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
 * We must DELETE the supplier hold here. The supplier hold is normally consumed by the supplier-side
 * checkout, but that only happens in HandleBorrowerItemLoaned - i.e. when the patron actually loans the
 * item. Here the patron cancelled before loaning, so nothing ever consumed it. Left in place it
 * re-captures the item when it is checked back in at the supplier (Polaris reports "transfer for hold"
 * and routes it straight back out), so the supplier item never becomes AVAILABLE and the request can
 * never complete.
 *
 * Use cleanUp/DELETE, never cancelHold/CANCEL. The distinction is critical for FOLIO:
 *  - CANCEL (cancelHoldRequest) sets the mod-dcb transaction to CANCELLED. That transaction IS our
 *    tracking channel; mod-dcb's check-in lookup excludes CANCELLED/ERROR, so it would never reach
 *    CLOSED and the supplier item would never report AVAILABLE - orphaning the request forever.
 *  - DELETE (deleteHold) is FOLIO-safe: it attempts CLOSED, mod-dcb rejects it (the lender close
 *    processor is 'manual', and the chain is validated before any mutation), the client swallows that
 *    into RESULT_OK_NOT_RESOLVED, and the transaction is left intact to close naturally on check-in.
 *    For Sierra / Polaris / Alma it removes the native hold - with Polaris's delete/fallback-cancel
 *    quirk already handled inside deleteHold - which is what stops the re-capture.
 *
 * Once the item is back at the supplier, HandleCancelledRequestReturnTransit moves the request onto the
 * normal RETURN_TRANSIT return leg, which completes and finalises it. NOTE: this is the PATRON
 * cancellation path (borrower/pickup hold gone). Supplier cancellation is a different, non-terminal
 * concern handled by HandleSupplierRequestCancelled and re-resolution - this transition never interacts
 * with it, and deleting the supplier hold here cannot trigger re-resolution because
 * AWAITING_RETURN_TO_SUPPLIER is not one of that transition's source states.
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
	private final SupplyingAgencyService supplyingAgencyService;

	public HandleCancelledRequestItemOut(PatronRequestAuditService patronRequestAuditService,
		SupplyingAgencyService supplyingAgencyService) {
		this.patronRequestAuditService = patronRequestAuditService;
		this.supplyingAgencyService = supplyingAgencyService;
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

		// Delete (never cancel) the supplier hold, so the returning item is not re-captured by it and can
		// go AVAILABLE at the supplier. cleanUp is already ILS-aware and FOLIO-safe, and is defensive -
		// it audits and swallows its own failures rather than erroring the transition. No borrower LMS
		// call is needed: the borrower hold is already gone, which is this transition's trigger.
		return supplyingAgencyService.cleanUp(ctx)
			.flatMap(c -> patronRequestAuditService.addAuditEntry(patronRequest,
				ITEM_OUT_HELD_AWAITING_RETURN, buildAuditData(patronRequest)))
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

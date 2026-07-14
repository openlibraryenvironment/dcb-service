package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
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
 * Cancelling and finalising here would delete the borrowing library's virtual records and orphan
 * the item, so instead we park the request in AWAITING_RETURN_TO_SUPPLIER. The request stays there -
 * records intact and still tracked - until the item makes it back to the supplier, at which point
 * HandleSupplierItemAvailable completes it (supplier item AVAILABLE/RECEIVED) and it is finalised.
 *
 * This transition sorts ahead of the HandleBorrower* transitions by name, so it wins over
 * HandleBorrowerSkippedLoanTransit / HandleBorrowerItemReceived whenever the hold is gone - the
 * request is never routed to RETURN_TRANSIT.
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
		"CancelledRequestItemOut : item is out, holding request until it is returned to the supplier";

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

		// Cancel the supplier hold now so that when the item is checked back in at the supplier it goes
		// AVAILABLE, rather than the supplier re-filling its still-active hold and shipping the item out
		// to the borrower again. Then park the request - borrower virtual records intact - until the item
		// is back and HandleSupplierItemAvailable completes it. The borrower hold is already gone (that is
		// the trigger for this transition), so no borrower LMS calls are needed.
		return cancelSupplierHoldIfPresent(ctx)
			.flatMap(c -> patronRequestAuditService.addAuditEntry(patronRequest,
				ITEM_OUT_HELD_AWAITING_RETURN, buildAuditData(patronRequest)))
			.doOnSuccess(audit -> patronRequest.setStatus(Status.AWAITING_RETURN_TO_SUPPLIER))
			.thenReturn(ctx);
	}

	private Mono<RequestWorkflowContext> cancelSupplierHoldIfPresent(RequestWorkflowContext ctx) {
		final var supplierRequest = getValueOrNull(ctx, RequestWorkflowContext::getSupplierRequest);
		if (supplierRequest == null) return Mono.just(ctx);

		// Tracking may already have observed the supplier hold as gone - nothing to cancel then.
		final var supplierHoldStatus = getValueOrNull(supplierRequest, SupplierRequest::getLocalStatus);
		if (cancelledHoldStatus.contains(supplierHoldStatus)) return Mono.just(ctx);

		return supplyingAgencyService.cancelHold(ctx);
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

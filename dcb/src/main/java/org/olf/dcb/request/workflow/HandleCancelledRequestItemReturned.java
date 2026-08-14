package org.olf.dcb.request.workflow;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * HandleCancelledRequestItemReturned.
 * Releases a request parked in AWAITING_RETURN_TO_SUPPLIER by HandleCancelledRequestItemOut, once the
 * item is home (or once we know we will never be told that it is). It moves to CANCELLED, from where
 * FinaliseRequestTransition does the record cleanup it was always going to do - just at the point where
 * deleting the virtual records no longer orphans a physical item.
 *
 * <p>Release is gated on the SUPPLIER having the item back. The borrower side cannot be used: the
 * outbound leg (item on its way TO the borrower) reports the exact same TRANSIT status as the return
 * leg, so a borrower-item gate fires at park time for anything cancelled during PICKUP_TRANSIT - before
 * the item is anywhere near coming back.
 *
 * <p>A supplier that cannot report the return at all
 * (HostLmsClient.canReportItemReturnedAfterHoldTerminated - FOLIO today, because terminating the hold
 * makes the mod-dcb transaction terminal and it can never report AVAILABLE again) never reaches this
 * transition: waiting on a signal that provably cannot arrive is a permanent stall, so
 * HandleCancelledRequestItemOut cancels those at entry instead of parking them.
 *
 * <p>Terminating as CANCELLED rather than rejoining RETURN_TRANSIT keeps the record honest: nothing was
 * supplied, and Outcome.CANCELLED is what reporting keys off. Rejoining the return leg would land in
 * HandleSupplierItemAvailable and stamp Outcome.SUPPLIED on a request nobody ever received.
 */
@Slf4j
@Singleton
@Named("CancelledRequestItemReturned")
public class HandleCancelledRequestItemReturned implements PatronRequestStateTransition {
	private static final List<Status> possibleSourceStatus = List.of(Status.AWAITING_RETURN_TO_SUPPLIER);

	// The supplier has the item back. Mirrors HandleSupplierItemAvailable.
	private static final List<String> supplierItemBackStatus = List.of(
		HostLmsItem.ITEM_AVAILABLE, HostLmsItem.ITEM_RECEIVED);

	static final String RELEASED_ITEM_BACK =
		"CancelledRequestItemReturned : item is back at the supplier, cancelling and finalising";

	private final PatronRequestAuditService patronRequestAuditService;

	public HandleCancelledRequestItemReturned(PatronRequestAuditService patronRequestAuditService) {
		this.patronRequestAuditService = patronRequestAuditService;
	}

	/**
	 * Note this guard is deliberately synchronous and deliberately narrow. A transition that is
	 * applicable but leaves the status untouched loops forever: applyTransition recurses into
	 * progressAll, which selects the same transition again. So the release condition lives here, not in
	 * attempt, and the "supplier cannot report a return" case is resolved once at park time by
	 * HandleCancelledRequestItemOut rather than being re-asked on every poll.
	 */
	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final var patronRequest = getValueOrNull(ctx, RequestWorkflowContext::getPatronRequest);
		final var status = getValueOrNull(patronRequest, PatronRequest::getStatus);

		if (status == null || !possibleSourceStatus.contains(status)) return false;
		if (getValueOrNull(patronRequest, PatronRequest::getActiveWorkflow) == null) return false;

		return supplierHasItemBack(ctx);
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		return release(ctx, RELEASED_ITEM_BACK);
	}

	private static boolean supplierHasItemBack(RequestWorkflowContext ctx) {
		final var supplierItemStatus = getValueOrNull(ctx,
			RequestWorkflowContext::getSupplierRequest, SupplierRequest::getLocalItemStatus);

		return supplierItemStatus != null && supplierItemBackStatus.contains(supplierItemStatus);
	}

	private Mono<RequestWorkflowContext> release(RequestWorkflowContext ctx, String message) {
		final var patronRequest = ctx.getPatronRequest();

		return patronRequestAuditService.addAuditEntry(patronRequest, message, buildAuditData(ctx))
			.map(audit -> {
				patronRequest
					.setOutcome(PatronRequest.Outcome.CANCELLED)
					.setStatus(Status.CANCELLED);
				return ctx;
			});
	}

	private static HashMap<String, Object> buildAuditData(RequestWorkflowContext ctx) {
		final var auditData = new HashMap<String, Object>();
		final var supplierRequest = getValueOrNull(ctx, RequestWorkflowContext::getSupplierRequest);

		auditData.put("supplier-item-status", getValueOrNull(supplierRequest, SupplierRequest::getLocalItemStatus));
		auditData.put("supplier-host-lms-code", getValueOrNull(supplierRequest, SupplierRequest::getHostLmsCode));
		return auditData;
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.CANCELLED);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleCancelledRequestItemReturned";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition(
			"DCBPatronRequest state is AWAITING_RETURN_TO_SUPPLIER and either the supplier item is "
				+ "AVAILABLE/RECEIVED or the supplier cannot report the item's return"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("RETURNED", Status.CANCELLED.toString()));
	}
}

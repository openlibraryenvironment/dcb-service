package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
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
 * Auto-finalising here (the default cancellation behaviour) would delete the borrowing library's
 * virtual records while the item is out, so we handle it explicitly.
 *
 * We always DELETE the supplier hold via cleanUp - each client implements the correct terminal
 * operation for its ILS. The supplier hold is normally consumed by the supplier-side checkout, but
 * that only happens once the patron actually loans the item; here they cancelled before loaning, so
 * nothing consumed it. Left in place it re-captures the item when it is checked back in at the
 * supplier (Polaris reports "transfer for hold" and routes it straight out; FOLIO re-fulfils the
 * still-open transaction), so it must go.
 *
 * What happens next depends on whether terminating that hold returns the item to the supplier
 * (HostLmsClient.cancellingSupplierHoldReleasesItem):
 *  - FOLIO (releases the item): cancelling the mod-dcb transaction cancels the circulation request
 *    and returns the item to AVAILABLE in inventory. There is no physical return to wait for, and no
 *    signal DCB could wait on (the terminal transaction can no longer report the item), and nothing is
 *    orphaned at the supplier. So we set CANCELLED and let the request finalise immediately.
 *  - Sierra / Polaris / Alma (does not release the item): deleteHold removes the native hold but the
 *    real inventory item stays out, tracked independently. Finalising now would orphan it, so we park
 *    the request in AWAITING_RETURN_TO_SUPPLIER (records intact, still tracked) until the real item is
 *    back, when HandleCancelledRequestReturnTransit joins the normal RETURN_TRANSIT return leg.
 *
 * No borrower LMS call is needed either way: the borrower/pickup hold is already gone, which is this
 * transition's trigger. This is the PATRON cancellation path; supplier cancellation / re-resolution is
 * a separate concern (HandleSupplierRequestCancelled) that this transition never interacts with.
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
	static final String ITEM_OUT_CANCELLED_AND_FINALISING =
		"CancelledRequestItemOut : patron cancelled while item is out; supplier releases the item on cancel, finalising";

	private final PatronRequestAuditService patronRequestAuditService;
	private final SupplyingAgencyService supplyingAgencyService;
	private final HostLmsService hostLmsService;

	public HandleCancelledRequestItemOut(PatronRequestAuditService patronRequestAuditService,
		SupplyingAgencyService supplyingAgencyService,
		HostLmsService hostLmsService) {
		this.patronRequestAuditService = patronRequestAuditService;
		this.supplyingAgencyService = supplyingAgencyService;
		this.hostLmsService = hostLmsService;
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

		return supplyingAgencyService.cleanUp(ctx)
			.flatMap(c -> supplierReleasesItemOnCancel(ctx))
			.flatMap(releasesItem -> {
				// FOLIO releases the item on cancel -> finalise now (CANCELLED); everyone else parks until
				// the real item is physically back at the supplier.
				final var finalise = Boolean.TRUE.equals(releasesItem);
				final var target = finalise ? Status.CANCELLED : Status.AWAITING_RETURN_TO_SUPPLIER;
				final var message = finalise ? ITEM_OUT_CANCELLED_AND_FINALISING : ITEM_OUT_HELD_AWAITING_RETURN;

				return patronRequestAuditService.addAuditEntry(patronRequest, message, buildAuditData(patronRequest))
					.doOnSuccess(audit -> patronRequest.setStatus(target))
					.thenReturn(ctx);
			});
	}

	private Mono<Boolean> supplierReleasesItemOnCancel(RequestWorkflowContext ctx) {
		final var supplierRequest = getValueOrNull(ctx, RequestWorkflowContext::getSupplierRequest);
		final var hostLmsCode = getValueOrNull(supplierRequest, SupplierRequest::getHostLmsCode);

		if (hostLmsCode == null) return Mono.just(Boolean.FALSE);

		return hostLmsService.getClientFor(hostLmsCode)
			.map(HostLmsClient::cancellingSupplierHoldReleasesItem)
			// If we cannot resolve the client, park (the safe default - never finalise an out item on a guess).
			.onErrorReturn(Boolean.FALSE)
			.defaultIfEmpty(Boolean.FALSE);
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
		// Two outcomes depending on the supplier (see getOutcomes); AWAITING_RETURN_TO_SUPPLIER is the
		// common park case.
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
		return List.of(
			new DCBTransitionResult("HELD", Status.AWAITING_RETURN_TO_SUPPLIER.toString()),
			new DCBTransitionResult("CANCELLED", Status.CANCELLED.toString()));
	}
}

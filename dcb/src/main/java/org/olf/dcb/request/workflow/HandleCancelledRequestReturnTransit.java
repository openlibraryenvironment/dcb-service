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
 * Release is driven primarily by the SUPPLIER item being back (AVAILABLE / RECEIVED, or a FOLIO
 * supplier transaction CLOSED) - the one signal that is reliable across every ILS. We deliberately
 * do NOT rely solely on the borrower item reaching TRANSIT: when the borrower is FOLIO, its item
 * status is a projection of the now-terminal (CANCELLED) mod-dcb transaction, which never reports
 * TRANSIT, so a borrower-only gate would strand the request in AWAITING even though the supplier
 * already has the item back. The borrower/pickup TRANSIT check is kept as a secondary, earlier
 * trigger so non-FOLIO borrowers still enter RETURN_TRANSIT while the item is genuinely in transit.
 */
@Slf4j
@Singleton
@Named("CancelledRequestReturnTransit")
public class HandleCancelledRequestReturnTransit implements PatronRequestStateTransition {
	private static final List<Status> possibleSourceStatus = List.of(Status.AWAITING_RETURN_TO_SUPPLIER);

	// Secondary early trigger: the borrower/pickup item is on its way back (observable on non-FOLIO borrowers).
	private static final List<String> borrowerReturningItemStatus = List.of(
		HostLmsItem.ITEM_TRANSIT, HostLmsItem.ITEM_MISSING, HostLmsItem.ITEM_AVAILABLE);

	// Primary reliable trigger: the supplier has the item back. Mirrors HandleSupplierItemAvailable.
	private static final List<String> supplierItemBackStatus = List.of(
		HostLmsItem.ITEM_AVAILABLE, HostLmsItem.ITEM_RECEIVED);
	private static final String SUPPLIER_TRANSACTION_CLOSED = "CLOSED";

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final var patronRequest = getValueOrNull(ctx, RequestWorkflowContext::getPatronRequest);
		final var status = getValueOrNull(patronRequest, PatronRequest::getStatus);

		if (status == null || !possibleSourceStatus.contains(status)) return false;
		if (getValueOrNull(patronRequest, PatronRequest::getActiveWorkflow) == null) return false;

		// Reliable, ILS-agnostic exit: the supplier has the item back.
		if (supplierHasItemBack(ctx)) return true;

		// Otherwise release early if the borrower/pickup item is genuinely in transit back.
		final var itemStatus = patronRequest.isUsingPickupAnywhereWorkflow()
			? getValueOrNull(patronRequest, PatronRequest::getPickupItemStatus)
			: getValueOrNull(patronRequest, PatronRequest::getLocalItemStatus);

		return borrowerReturningItemStatus.contains(itemStatus);
	}

	private static boolean supplierHasItemBack(RequestWorkflowContext ctx) {
		final var supplierRequest = getValueOrNull(ctx, RequestWorkflowContext::getSupplierRequest);
		if (supplierRequest == null) return false;

		final var supplierItemStatus = getValueOrNull(supplierRequest, SupplierRequest::getLocalItemStatus);
		final var supplierLocalStatus = getValueOrNull(supplierRequest, SupplierRequest::getLocalStatus);

		return supplierItemBackStatus.contains(supplierItemStatus)
			|| SUPPLIER_TRANSACTION_CLOSED.equals(supplierLocalStatus);
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
			"DCBPatronRequest state is AWAITING_RETURN_TO_SUPPLIER and either the supplier item is back "
				+ "(AVAILABLE/RECEIVED or supplier transaction CLOSED) or the borrower/pickup item is TRANSIT/MISSING/AVAILABLE"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("RETURNING", Status.RETURN_TRANSIT.toString()));
	}
}

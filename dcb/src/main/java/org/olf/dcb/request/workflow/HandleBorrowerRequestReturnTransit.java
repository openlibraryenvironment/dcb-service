package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import org.olf.dcb.storage.PatronRequestRepository;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;


/**
 * HandleBorrowerRequestReturnTransit.
 * When the DCB status is LOANED and the item at the borrowing library is IN_TRANSIT or AVAILABLE then
 * we note that the item has been returned by the patron and is starting it's return leg.
 */
@Slf4j
@Singleton
@Named("BorrowerRequestReturnTransit")
public class HandleBorrowerRequestReturnTransit implements PatronRequestStateTransition {
	private final PatronRequestRepository patronRequestRepository;

	private static final List<Status> possibleSourceStatus = List.of(Status.LOANED);
	private static final List<String> possibleLocalItemStatus = List.of(
		HostLmsItem.ITEM_TRANSIT, HostLmsItem.ITEM_AVAILABLE);
	private static final List<String> possibleSupplierLocalItemStatus = List.of(
		HostLmsItem.ITEM_AVAILABLE);
	
	public HandleBorrowerRequestReturnTransit(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {

		final var patronRequest = getValueOrNull(ctx, RequestWorkflowContext::getPatronRequest);
		//final var supplierRequest = getValueOrNull(ctx, RequestWorkflowContext::getSupplierRequest);
		// Handles expedited checkout situations where the local item ID can be null and isn't needed, but we still need to progress
		if (isPatronRequestStatusApplicable(patronRequest) && patronRequest.getIsExpeditedCheckout() !=null && patronRequest.getIsExpeditedCheckout()) {
			return true;
		}
		else
		{
			return isPatronRequestStatusApplicable(patronRequest) &&
				( isLocalItemStatusApplicable(patronRequest) || isPickupItemStatusApplicable(patronRequest) );
		}

	}

	private boolean isPatronRequestStatusApplicable(PatronRequest patronRequest) {
		return getPossibleSourceStatus().contains(patronRequest.getStatus());
	}

	private boolean isLocalItemStatusApplicable(PatronRequest patronRequest) {
		return getPossibleLocalItemStatus().contains(patronRequest.getLocalItemStatus());
	}

	private boolean isPickupItemStatusApplicable(PatronRequest patronRequest) {
		return patronRequest.getPickupItemStatus() != null
			&& getPossibleLocalItemStatus().contains(patronRequest.getPickupItemStatus());
	}

	private boolean isSupplierLocalItemStatusApplicable(SupplierRequest supplierRequest) {
		return possibleSupplierLocalItemStatus.contains(supplierRequest.getLocalItemStatus());
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		ctx.getPatronRequest().setStatus(PatronRequest.Status.RETURN_TRANSIT);
		return Mono.just(ctx);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}

	public List<String> getPossibleLocalItemStatus() {
		return possibleLocalItemStatus;
	}
	
	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(PatronRequest.Status.RETURN_TRANSIT);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleBorrowerRequestReturnTransit";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of( new DCBGuardCondition("DCBPatronRequest state is LOANED and Item at Patron or Pickup Library state is TRANSIT"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("RETURNED","RETURN_TRANSIT"));
	}
}

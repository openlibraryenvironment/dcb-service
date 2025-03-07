package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import org.olf.dcb.storage.PatronRequestRepository;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("BorrowerRequestItemReceived")
public class HandleBorrowerItemReceived implements PatronRequestStateTransition {
	private final PatronRequestRepository patronRequestRepository;

	// If the item is received at the patron library, then maybe we just skipped the whole transit dance because
	// it wasn't checked out before it was put on the van
	private static final List<Status> possibleSourceStatus = List.of(Status.PICKUP_TRANSIT, Status.REQUEST_PLACED_AT_BORROWING_AGENCY);

	// If we're in one of posisbleSourceStates, but  the actual item state is one of these - then we have been received - do that first
	// This will allow us to wind forwards through the model in the event that we missed a state change
	private static final List<String> triggeringItemStates = List.of(HostLmsItem.ITEM_RECEIVED, HostLmsItem.ITEM_LOANED, HostLmsItem.ITEM_ON_HOLDSHELF );
	
	public HandleBorrowerItemReceived(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final PatronRequest patronRequest = ctx.getPatronRequest();

		final boolean isPatronRequestStatusApplicable = isStatusApplicable(patronRequest);
		final boolean isLocalItemStatusApplicable = isPatronRequestStatusApplicable && isLocalItemStatusApplicable(patronRequest);
		final boolean isPickupItemStatusApplicable = isPatronRequestStatusApplicable && isPickupItemStatusApplicable(patronRequest);

		return isLocalItemStatusApplicable || isPickupItemStatusApplicable;
	}

	private boolean isStatusApplicable(PatronRequest patronRequest) {
		final var status = Optional.ofNullable(patronRequest).map(PatronRequest::getStatus).orElse(null);
		return getPossibleSourceStatus().contains(status);
	}

	private boolean isLocalItemStatusApplicable(PatronRequest patronRequest) {
		final var localItemStatus = Optional.ofNullable(patronRequest).map(PatronRequest::getLocalItemStatus).orElse(null);
		return localItemStatus != null && triggeringItemStates.contains(localItemStatus);
	}

	private boolean isPickupItemStatusApplicable(PatronRequest patronRequest) {
		final var pickupItemStatus = Optional.ofNullable(patronRequest).map(PatronRequest::getPickupItemStatus).orElse(null);
		return pickupItemStatus != null && triggeringItemStates.contains(pickupItemStatus);
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		ctx.getPatronRequest().setStatus(PatronRequest.Status.RECEIVED_AT_PICKUP);

		return Mono.from(patronRequestRepository.saveOrUpdate(ctx.getPatronRequest()))
			.thenReturn(ctx);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(PatronRequest.Status.RECEIVED_AT_PICKUP);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleBorrowerItemReceived";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition("DCBPatronRequest status is PICKUP_TRANSIT AND Item at pickup location is RECEIVED"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("RECEIVED",PatronRequest.Status.RECEIVED_AT_PICKUP.toString()));
	}
}

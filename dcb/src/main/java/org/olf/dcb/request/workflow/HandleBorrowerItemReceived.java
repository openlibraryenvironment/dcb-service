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
import org.olf.dcb.tracking.model.StateChange;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("BorrowerRequestItemReceived")
public class HandleBorrowerItemReceived implements PatronRequestStateTransition {
	private final PatronRequestRepository patronRequestRepository;

	private static final List<Status> possibleSourceStatus = List.of(Status.PICKUP_TRANSIT);
	
	public HandleBorrowerItemReceived(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return ( getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus()) &&
			ctx.getPatronRequest().getLocalItemStatus().equals(HostLmsItem.ITEM_RECEIVED) );
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		ctx.getPatronRequest().setStatus(PatronRequest.Status.RECEIVED_AT_PICKUP);
		// For now, PatronRequestWorkflowService will save te patron request, but we should do that here
		// and not there - flagging this as a change needed when we refactor.
		return Mono.just(ctx);
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

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
@Named("BorrowerRequestReturnTransit")
public class HandleBorrowerRequestReturnTransit implements PatronRequestStateTransition {
	private final PatronRequestRepository patronRequestRepository;

	private static final List<Status> possibleSourceStatus = List.of(Status.LOANED);
	private static final List<String> possibleLocalItemStatus = List.of(
		HostLmsItem.ITEM_TRANSIT, HostLmsItem.ITEM_AVAILABLE);
	
	public HandleBorrowerRequestReturnTransit(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return ( ( getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus()) ) &&
			getPossibleLocalItemStatus().contains(ctx.getPatronRequest().getLocalItemStatus()) );
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

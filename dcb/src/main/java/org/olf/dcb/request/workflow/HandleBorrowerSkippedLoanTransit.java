package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRequest;
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


/**
 * HandleBorrowerSkippedLoanTransit.
 * The DCB status is READY_FOR_PICKUP or PICKUP_TRANSIT but the patron reqest is missing AND the item status is TRANSIT
 * this indicates that EITHER we failed to detect the patron loan OR the request is cancelled and the item is on it's way back
 * to the lending system. We move directly to RETURN_TRANSIT.
 */
@Slf4j
@Singleton
@Named("BorrowerSkippedLoanTransit")
public class HandleBorrowerSkippedLoanTransit implements PatronRequestStateTransition {
	private final PatronRequestRepository patronRequestRepository;

	private static final List<Status> possibleSourceStatus = List.of(Status.PICKUP_TRANSIT, Status.READY_FOR_PICKUP);
	private static final List<String> possibleLocalItemStatus = List.of(HostLmsItem.ITEM_TRANSIT, HostLmsItem.ITEM_MISSING);
	private static final List<String> possibleLocalRequestStatus = List.of(HostLmsRequest.HOLD_MISSING);
	
	public HandleBorrowerSkippedLoanTransit(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {

		if ( ctx.getPatronRequest().getActiveWorkflow() != null ) {
			if ( ctx.getPatronRequest().getActiveWorkflow().equals("RET-STD") ) {
				return ( ( getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus()) ) &&
					getPossibleLocalItemStatus().contains(ctx.getPatronRequest().getLocalItemStatus()) &&
					possibleLocalRequestStatus.contains(ctx.getPatronRequest().getLocalRequestStatus()) ) ;
			}
			else if ( ctx.getPatronRequest().getActiveWorkflow().equals("RET-LOCAL") ) {
				// For now we repeat the same configuration for RET-LOCAL - to allow LOCAL loans to also bypass the loan stage
				// There may be a better way to deal with RET-LOCAL requests.
				return ( ( getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus()) ) &&
					getPossibleLocalItemStatus().contains(ctx.getPatronRequest().getLocalItemStatus()) &&
					possibleLocalRequestStatus.contains(ctx.getPatronRequest().getLocalRequestStatus()) ) ;
			}
		}
		return false;
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
		return "HandleBorrowerSkippedLoanTransit";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of( new DCBGuardCondition("DCBPatronRequest state is PICKUP_TRANSIT or READY_FOR_PICKUP and Item at Patron or Pickup Library state is TRANSIT and patron request is MISSING"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("RETURNED","RETURN_TRANSIT"));
	}
}

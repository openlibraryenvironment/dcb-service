package org.olf.dcb.request.workflow;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


/**
 * HandleBorrowerSkippedLoanTransit.
 * The DCB status is READY_FOR_PICKUP or PICKUP_TRANSIT but the patron request is missing AND the item status is TRANSIT
 * this indicates that EITHER we failed to detect the patron loan OR the request is cancelled and the item is on it's way back
 * to the lending system. We move directly to RETURN_TRANSIT.
 */
@Slf4j
@Singleton
@Named("BorrowerSkippedLoanTransit")
public class HandleBorrowerSkippedLoanTransit implements PatronRequestStateTransition {
	private static final List<Status> possibleSourceStatus = List.of(Status.PICKUP_TRANSIT, Status.READY_FOR_PICKUP);
	private static final List<String> possibleLocalItemStatus = List.of(HostLmsItem.ITEM_TRANSIT, HostLmsItem.ITEM_MISSING, HostLmsItem.ITEM_AVAILABLE);
	private static final List<String> possibleLocalRequestStatus = List.of(HostLmsRequest.HOLD_MISSING);

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final var patronRequest = getValue(ctx, RequestWorkflowContext::getPatronRequest, null);

		final var status = getValue(patronRequest, PatronRequest::getStatus, "Unknown");
		final var localItemStatus = getValue(patronRequest, PatronRequest::getLocalItemStatus,
			"Unknown");
		final var localRequestStatus = getValue(patronRequest, PatronRequest::getLocalRequestStatus,
			"Unknown");

		if ( patronRequest.getActiveWorkflow() != null ) {

			if ( patronRequest.isUsingStandardWorkflow() ) {
				return ( ( getPossibleSourceStatus().contains(status) ) &&
					getPossibleLocalItemStatus().contains(localItemStatus) &&
					possibleLocalRequestStatus.contains(localRequestStatus) ) ;
			}
			else if ( patronRequest.isUsingLocalWorkflow() ) {
				// For now we repeat the same configuration for RET-LOCAL - to allow LOCAL loans to also bypass the loan stage
				// There may be a better way to deal with RET-LOCAL requests.
				return ( ( getPossibleSourceStatus().contains(status) ) &&
					getPossibleLocalItemStatus().contains(localItemStatus) &&
					possibleLocalRequestStatus.contains(localRequestStatus) ) ;
			}
			else if ( patronRequest.isUsingPickupAnywhereWorkflow() ) {
				final var localPickupItemStatus = getValue(patronRequest, PatronRequest::getPickupItemStatus,
					"Unknown");
				final var localPickupRequestStatus = getValue(patronRequest, PatronRequest::getPickupRequestStatus,
					"Unknown");

				return ( ( getPossibleSourceStatus().contains(status) ) &&
					getPossibleLocalItemStatus().contains(localPickupItemStatus) &&
					possibleLocalRequestStatus.contains(localPickupRequestStatus) ) ;
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

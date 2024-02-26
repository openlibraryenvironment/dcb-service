package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
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
@Named("BorrowerRequestItemOnHoldShelf")
public class HandleBorrowerItemOnHoldShelf implements PatronRequestStateTransition {
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private final PatronRequestRepository patronRequestRepository;
	private final HostLmsService hostLmsService;

	private static final List<Status> possibleSourceStatus = List.of(Status.RECEIVED_AT_PICKUP, Status.PICKUP_TRANSIT);
	
	public HandleBorrowerItemOnHoldShelf(
		PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService,
		RequestWorkflowContextHelper requestWorkflowContextHelper) {
		this.patronRequestRepository = patronRequestRepository;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.hostLmsService = hostLmsService;
	}

	public Mono<RequestWorkflowContext> updateSupplierItemToReceived(
		RequestWorkflowContext rwc) {
		if ((rwc.getSupplierRequest() != null) &&
			(rwc.getSupplierRequest().getLocalItemId() != null) &&
			(rwc.getLenderSystemCode() != null)) {

			final var supplierRequest = rwc.getSupplierRequest();

			return hostLmsService.getClientFor(rwc.getLenderSystemCode())
				// updateItemStatus here should be clearing the m-flag (Message)
				.flatMap(hostLmsClient -> hostLmsClient.updateItemStatus(
					supplierRequest.getLocalItemId(),
					CanonicalItemState.RECEIVED, supplierRequest.getLocalId()))
				.thenReturn(rwc);
		}

		return Mono.just(rwc);
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return (
			( getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus()) ) &&
			ctx.getPatronRequest().getLocalItemStatus().equals(HostLmsItem.ITEM_ON_HOLDSHELF) );
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
			ctx.getPatronRequest().setStatus(PatronRequest.Status.READY_FOR_PICKUP);
			return updateSupplierItemToReceived(ctx)
				// For now, PatronRequestWorkflowService will save the patron request, but we should do that here
				// and not there - flagging this as a change needed when we refactor.
				.thenReturn(ctx);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(PatronRequest.Status.READY_FOR_PICKUP);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleBorrowerItemOnHoldShelf";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition("DCBPatronRequest status is one of ( RECEIVED_AT_PICKUP OR PICKUP_TRANSIT ) AND Item status at pickup location is ITEM_ON_HOLDSHELF"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("READY_FOR_PICKUP",PatronRequest.Status.READY_FOR_PICKUP.toString()));
	}
}

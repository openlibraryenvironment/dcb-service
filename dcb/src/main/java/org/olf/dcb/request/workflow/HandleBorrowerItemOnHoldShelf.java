package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState;
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
@Named("BorrowerRequestItemOnHoldShelf")
public class HandleBorrowerItemOnHoldShelf implements PatronRequestStateTransition {
	private final PatronRequestRepository patronRequestRepository;
	private final HostLmsService hostLmsService;

	private static final List<Status> possibleSourceStatus = List.of(Status.RECEIVED_AT_PICKUP, Status.PICKUP_TRANSIT);
	private static final List<String> triggeringItemStates = List.of(HostLmsItem.ITEM_ON_HOLDSHELF, HostLmsItem.ITEM_LOANED);
	
	public HandleBorrowerItemOnHoldShelf(
		PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService) {

		this.patronRequestRepository = patronRequestRepository;
		this.hostLmsService = hostLmsService;
	}

	// If the patron request status is applicable
	// and either the local or pickup item status is applicable, the method returns true.
	// Otherwise, it returns false.
	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final PatronRequest patronRequest = ctx.getPatronRequest();
		final boolean isStatusApplicable = isStatusApplicable(patronRequest);
		if (isStatusApplicable) {

			final boolean isLocalItemStatusApplicable = isLocalItemStatusApplicable(patronRequest);
			final boolean isPickupItemStatusApplicable = isPickupItemStatusApplicable(patronRequest);

			if (isLocalItemStatusApplicable) {
				log.debug("Applicable for status: {}, local item status: {}",
					patronRequest.getStatus(), patronRequest.getLocalItemStatus());

				return true;
			} else if (isPickupItemStatusApplicable) {
				log.debug("Applicable for status: {}, pickup item status: {}",
					patronRequest.getStatus(), patronRequest.getPickupItemStatus());

				return true;
			}
		}

		return false;
	}

	private boolean isStatusApplicable(PatronRequest patronRequest) {
		return getPossibleSourceStatus().contains(patronRequest.getStatus());
	}

	private boolean isLocalItemStatusApplicable(PatronRequest patronRequest) {
		return patronRequest.getLocalItemStatus() != null
			&& triggeringItemStates.contains(patronRequest.getLocalItemStatus());
	}

	private boolean isPickupItemStatusApplicable(PatronRequest patronRequest) {
		return patronRequest.getPickupItemStatus() != null
			&& triggeringItemStates.contains(patronRequest.getPickupItemStatus());
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		ctx.getPatronRequest().setStatus(PatronRequest.Status.READY_FOR_PICKUP);

		final var patronRequest = ctx.getPatronRequest();
		final var activeWorkflow = patronRequest.getActiveWorkflow();

		if ("RET-PUA".equals(activeWorkflow)) {

			// check we need to update the borrower item
			final var isPickupItemStatusApplicable = isPickupItemStatusApplicable(patronRequest);

			if (isPickupItemStatusApplicable) {
				return updateSupplierItemToReceived(ctx)
					.flatMap(this::updateBorrowerItemToReceived)
					.flatMap(this::updatePatronRequest);
			}
			else {
				// this should not happen in normal operation
				log.warn("Borrower item was not updated");
			}
		}

		return updateSupplierItemToReceived(ctx)
			.flatMap(this::updatePatronRequest);
	}

	private Mono<RequestWorkflowContext> updatePatronRequest(RequestWorkflowContext requestWorkflowContext) {
		return Mono.from(patronRequestRepository.saveOrUpdate(requestWorkflowContext.getPatronRequest()))
			.thenReturn(requestWorkflowContext);
	}

	public Mono<RequestWorkflowContext> updateSupplierItemToReceived(
		RequestWorkflowContext rwc) {
		if ((rwc.getSupplierRequest() != null) && (rwc.getLenderSystemCode() != null)) {

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


	private Mono<RequestWorkflowContext> updateBorrowerItemToReceived(
		RequestWorkflowContext requestWorkflowContext) {
		log.debug("updateBorrowerItemToReceived");

		if (requestWorkflowContext.getPatronSystemCode() == null ||
			requestWorkflowContext.getPatronRequest() == null ||
			requestWorkflowContext.getPatronRequest().getLocalRequestId() == null) {

			return Mono.error(new IllegalStateException("updateBorrowerItemToReceived called with missing data"));
		}

		final var patronSystemCode = requestWorkflowContext.getPatronSystemCode();
		final var patronRequest = requestWorkflowContext.getPatronRequest();
		final var localItemId = patronRequest.getLocalItemId();
		final var localRequestId = patronRequest.getLocalRequestId();

		return hostLmsService.getClientFor(patronSystemCode)
			// updateItemStatus here should be clearing the m-flag (Message)
			.flatMap(hostLmsClient -> hostLmsClient.updateItemStatus(localItemId,
				CanonicalItemState.RECEIVED, localRequestId))
			.thenReturn(requestWorkflowContext);
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

package org.olf.dcb.request.workflow;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState.TRANSIT;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.*;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;


/** TODO: Convert this into a PatronRequestStateTransition */

@Slf4j
@Singleton
@Named("SupplierRequestInTransit")
public class HandleSupplierInTransit implements PatronRequestStateTransition {
	private final SupplierRequestRepository supplierRequestRepository;
	private final PatronRequestRepository patronRequestRepository;
	private final HostLmsService hostLmsService;
	private final PatronRequestAuditService patronRequestAuditService;

	private static final List<Status> possibleSourceStatus = List.of(
		REQUEST_PLACED_AT_BORROWING_AGENCY,
		REQUEST_PLACED_AT_PICKUP_AGENCY);
	
	public HandleSupplierInTransit(
		SupplierRequestRepository supplierRequestRepository,
		PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService,
		PatronRequestAuditService patronRequestAuditService) {

		this.supplierRequestRepository = supplierRequestRepository;
		this.patronRequestRepository = patronRequestRepository;
		this.hostLmsService = hostLmsService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	public Mono<RequestWorkflowContext> saveSupplierRequest(RequestWorkflowContext rwc) {
		return Mono.from(supplierRequestRepository.saveOrUpdate(rwc.getSupplierRequest()))
			.thenReturn(rwc);
	}

	public Mono<RequestWorkflowContext> updatePatronRequest(RequestWorkflowContext rwc) {
		final var pr = rwc.getPatronRequest();

		pr.setStatus(PICKUP_TRANSIT);

		return Mono.from(patronRequestRepository.saveOrUpdate(pr))
			.thenReturn(rwc);
	}

	// If there is a separate pickup location, the pickup location needs to be updated
	// If there is a separate patron request (There always will be EXCEPT for the "Local" case) update it
	public Mono<RequestWorkflowContext> updateUpstreamSystems(RequestWorkflowContext rwc) {
		log.debug("updateUpstreamSystems rwc={},{}", rwc.getPatronSystemCode(), rwc.getPatronRequest().getPickupRequestId());

		return updatePatronItem(rwc).flatMap(this::updatePickupItem);

		// rwc.getPatronRequest().getLocalRequestId() == The request placed at the patron home system to represent this loan
		// rwc.getPatronRequest().getPickupRequestId() == The request placed at a third party pickup location
	}

	public Mono<RequestWorkflowContext> updatePatronItem(RequestWorkflowContext rwc) {

		log.debug("updatePatronItem: {}", rwc);

		if (rwc.getPatronRequest().getLocalItemId() != null) {
			log.debug("Update patron system item: {}", rwc.getPatronRequest().getLocalItemId());

			final var patronRequest = rwc.getPatronRequest();
			final var localItemId = getValueOrNull(patronRequest, PatronRequest::getLocalItemId);
			final var localBibId = getValueOrNull(patronRequest, PatronRequest::getLocalBibId);
			final var localHoldingsId = getValueOrNull(patronRequest, PatronRequest::getLocalHoldingId);
			final var localRequestId = getValueOrNull(patronRequest, PatronRequest::getLocalRequestId);
			final var hostLmsItem = HostLmsItem.builder()
				.localId(localItemId)
				.bibId(localBibId)
				.holdingId(localHoldingsId)
				.localRequestId(localRequestId)
				.build();

			return hostLmsService.getClientFor(rwc.getPatronSystemCode())
		 		.flatMap(hostLmsClient -> hostLmsClient.updateItemStatus(hostLmsItem, TRANSIT))
				.thenReturn(rwc);
		}
		else {
			log.error("No patron system to update -- this is unlikely");
			return Mono.just(rwc);
		}
	}

	public Mono<RequestWorkflowContext> updatePickupItem(RequestWorkflowContext rwc) {

		final var patronRequest = rwc.getPatronRequest();
		final var pickupItemId = Optional.ofNullable(patronRequest).map(PatronRequest::getPickupItemId).orElse(null);

		if (pickupItemId != null && "RET-PUA".equals(patronRequest.getActiveWorkflow())) {
			log.debug("Update PUA item: {}", pickupItemId);

			final var pickupSystem = rwc.getPickupSystem();
			final var localItemId = getValueOrNull(patronRequest, PatronRequest::getPickupItemId);
			final var localBibId = getValueOrNull(patronRequest, PatronRequest::getPickupBibId);
			final var localHoldingsId = getValueOrNull(patronRequest, PatronRequest::getPickupHoldingId);
			final var localRequestId = getValueOrNull(patronRequest, PatronRequest::getPickupRequestId);
			final var hostLmsItem = HostLmsItem.builder()
				.localId(localItemId)
				.bibId(localBibId)
				.holdingId(localHoldingsId)
				.localRequestId(localRequestId)
				.build();

			return pickupSystem.updateItemStatus(hostLmsItem, TRANSIT)
				.doOnSuccess(item -> log.debug("Updated PUA item: {}", item))
				.thenReturn(rwc);
		}
		else {
			log.debug("No PUA item to update");
			return Mono.just(rwc);
		}
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		// This action fires when the state is REQUEST_PLACED_AT_BORROWING_AGENCY and we detected
		// that the supplying library has placed its request IN TRANSIT

		log.debug("Consider HandleSupplierInTransit - status: {}, supplierRequest: {}",
			ctx.getPatronRequest().getStatus(),
			ctx.getSupplierRequest());

		boolean hasValidStatus = getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus());
		boolean hasSupplierRequest = ctx.getSupplierRequest() != null;

		// Check item status
		boolean hasLocalItemStatus = hasSupplierRequest && ctx.getSupplierRequest().getLocalItemStatus() != null;
		boolean isItemStatusInTransit = hasLocalItemStatus && ctx.getSupplierRequest().getLocalItemStatus().equals(ITEM_TRANSIT);

		// Check hold status
		boolean hasLocalHoldStatus = hasSupplierRequest && ctx.getSupplierRequest().getLocalStatus() != null;
		boolean isHoldStatusInTransit = hasLocalHoldStatus && ctx.getSupplierRequest().getLocalStatus().equals(HostLmsRequest.HOLD_TRANSIT);

		// Combined transit check (either item OR hold status indicates transit)
		boolean isInTransit = isItemStatusInTransit || isHoldStatusInTransit;

		// Log each condition result
		log.debug("HandleSupplierInTransit conditions: hasValidStatus={}, hasSupplierRequest={}, isInTransit={}",
			hasValidStatus, hasSupplierRequest, isInTransit);

		return hasValidStatus && hasSupplierRequest && isInTransit;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		log.info("Attempting HandleSupplierInTransit for {}", ctx.getPatronRequest().getId());

		ctx.getPatronRequest().setStatus(PICKUP_TRANSIT);

		return updateUpstreamSystems(ctx)
			// If we managed to update other systems, then update the supplier request
			// This will cause st.setLocalStatus("TRANSIT") above to be saved and mean our local state is aligned with the supplier req
			.flatMap(this::saveSupplierRequest)
			.flatMap(this::updatePatronRequest)
			.doOnError(error -> patronRequestAuditService.addErrorAuditEntry(
				ctx.getPatronRequest(), "Error attempting to set inTransit state on downstream systems:" + error))
			.thenReturn(ctx);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(PICKUP_TRANSIT);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleSupplierInTransit";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return PatronRequestStateTransition.super.getGuardConditions();
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return PatronRequestStateTransition.super.getOutcomes();
	}
}

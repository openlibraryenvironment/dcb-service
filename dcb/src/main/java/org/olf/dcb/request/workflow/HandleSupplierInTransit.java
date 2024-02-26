package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;


import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.olf.dcb.request.fulfilment.PatronRequestAuditService;


/** TODO: Convert this into a PatronRequestStateTransition */

@Slf4j
@Singleton
@Named("SupplierRequestInTransit")
public class HandleSupplierInTransit implements PatronRequestStateTransition {

	private final SupplierRequestRepository supplierRequestRepository;
	private final PatronRequestRepository patronRequestRepository;
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private final HostLmsService hostLmsService;
	private final PatronRequestAuditService patronRequestAuditService;

	public HandleSupplierInTransit(
		SupplierRequestRepository supplierRequestRepository,
		PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService,
		RequestWorkflowContextHelper requestWorkflowContextHelper,
		PatronRequestAuditService patronRequestAuditService) {

		this.supplierRequestRepository = supplierRequestRepository;
		this.patronRequestRepository = patronRequestRepository;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.hostLmsService = hostLmsService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	public Mono<RequestWorkflowContext> saveSupplierRequest(RequestWorkflowContext rwc) {
		return Mono.from(supplierRequestRepository.saveOrUpdate(rwc.getSupplierRequest()))
			.thenReturn(rwc);
	}

	public Mono<RequestWorkflowContext> updatePatronRequest(RequestWorkflowContext rwc) {
		PatronRequest pr = rwc.getPatronRequest();
		pr.setStatus(PatronRequest.Status.PICKUP_TRANSIT);
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
		if (rwc.getPatronRequest().getLocalItemId() != null) {
			log.debug("Update patron system item: {}", rwc.getPatronRequest().getLocalItemId());

			final var patronRequest = rwc.getPatronRequest();

			return hostLmsService.getClientFor(rwc.getPatronSystemCode())
		 		.flatMap(hostLmsClient -> hostLmsClient.updateItemStatus(
					patronRequest.getLocalItemId(), HostLmsClient.CanonicalItemState.TRANSIT, patronRequest.getLocalRequestId()))
				.thenReturn(rwc);
		}
		else {
			log.error("No patron system to update -- this is unlikely");
			return Mono.just(rwc);
		}	
	}

	public Mono<RequestWorkflowContext> updatePickupItem(RequestWorkflowContext rwc) {
		if (rwc.getPatronRequest().getPickupItemId() != null) {
			log.warn("Pickup item ID is SET but pickup anywhere is not yet implemented. No action");
			return Mono.just(rwc);
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
		return ctx.getPatronRequest().getStatus() == PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY &&
			ctx.getSupplierRequest() != null &&
			ctx.getSupplierRequest().getLocalStatus().equals(HostLmsItem.ITEM_TRANSIT);
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		log.debug("Attempting transit transition for supplier request");
		ctx.getPatronRequest().setStatus(PatronRequest.Status.PICKUP_TRANSIT);
		return updateUpstreamSystems(ctx)
			// If we managed to update other systems, then update the supplier request
			// This will cause st.setLocalStatus("TRANSIT") above to be saved and mean our local state is aligned with the supplier req
			.flatMap(this::saveSupplierRequest)
			.flatMap(this::updatePatronRequest)
			.flatMap(ctxp -> patronRequestAuditService.addAuditEntry(ctx, ctx.getPatronRequestStateOnEntry(), ctx.getPatronRequest().getStatus(), Optional.of("HandleSupplierInTransit"), Optional.empty()))
			.doOnError(error -> patronRequestAuditService.addErrorAuditEntry(
				ctx.getPatronRequest(), "Error attempting to set inTransit state on downstream systems:" + error))
			.thenReturn(ctx);
	}

	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(PatronRequest.Status.PICKUP_TRANSIT);
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

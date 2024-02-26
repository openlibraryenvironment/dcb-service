package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("SupplierRequestConfirmed")
public class HandleSupplierRequestConfirmed implements PatronRequestStateTransition {
	private final SupplierRequestRepository supplierRequestRepository;

	public HandleSupplierRequestConfirmed(SupplierRequestRepository supplierRequestRepository) {
		this.supplierRequestRepository = supplierRequestRepository;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return ( ctx.getPatronRequest().getStatus() == PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY ) &&
			( ctx.getSupplierRequest() != null ) &&
			( ctx.getSupplierRequest().getLocalStatus() != null ) &&
			( ctx.getSupplierRequest().getLocalStatus().equals(HOLD_CONFIRMED) );
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		PatronRequest patronRequest = ctx.getPatronRequest();
		// patronRequest.setStatus(PatronRequest.Status.CONFIRMED)
		// We need to call get hold on the supplying system and retrieve the actual item ID and barcode and then update
		// the supplier request with the actual details so we know what to use when placing the borrower request
		return Mono.just(ctx);
	}

	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.empty();
		// return Optional.of(PatronRequest.Status.CONFIRMED);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleSupplierRequestConfirmed";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition("DCBPatronRequest status is REQUEST_PLACED_AT_SUPPLYING_AGENCY and Supplier Item Status is CONFIRMED"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("CONFIRMED","CONFIRMED"));
	}
}

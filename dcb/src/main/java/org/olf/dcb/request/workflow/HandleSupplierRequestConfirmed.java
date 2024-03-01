package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("SupplierRequestConfirmed")
public class HandleSupplierRequestConfirmed implements PatronRequestStateTransition {
	private final SupplierRequestService supplierRequestService;
	private final HostLmsService hostLmsService;

	private static final List<Status> possibleSourceStatus = List.of(REQUEST_PLACED_AT_SUPPLYING_AGENCY);
	
	public HandleSupplierRequestConfirmed(SupplierRequestService supplierRequestRepository,
		HostLmsService hostLmsService) {

		this.supplierRequestService = supplierRequestRepository;
		this.hostLmsService = hostLmsService;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return ( getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus()) ) &&
			( ctx.getSupplierRequest() != null ) &&
			( ctx.getSupplierRequest().getLocalStatus() != null ) &&
			( ctx.getSupplierRequest().getLocalStatus().equals(HOLD_CONFIRMED) );
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		// The presence of a supplier is checked in `isApplicableFor` so not repeated here
		final var supplierRequest = ctx.getSupplierRequest();
		final var patronRequest = ctx.getPatronRequest();

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.getRequest(supplierRequest.getLocalId()))
			.flatMap(localRequest -> {
				final var localItemId = localRequest.getRequestedItemId();

				if (localItemId != null) {
					supplierRequest.setLocalItemId(localItemId);

					return supplierRequestService.updateSupplierRequest(supplierRequest);
				}
				else {
					return Mono.just(supplierRequest);
				}
			})
			.flatMap(updatedSupplierRequest -> Mono.defer(() -> Mono.just(patronRequest.setStatus(CONFIRMED))))
			.then(Mono.just(ctx));
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(CONFIRMED);
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

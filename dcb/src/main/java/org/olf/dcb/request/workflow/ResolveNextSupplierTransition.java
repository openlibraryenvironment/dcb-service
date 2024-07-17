package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.PatronRequest.Status.NOT_SUPPLIED_CURRENT_SUPPLIER;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("ResolveNextSupplier")
public class ResolveNextSupplierTransition extends AbstractPatronRequestStateTransition
	implements PatronRequestStateTransition {

	private final HostLmsService hostLmsService;

	ResolveNextSupplierTransition(HostLmsService hostLmsService) {
		super(List.of(NOT_SUPPLIED_CURRENT_SUPPLIER));
		this.hostLmsService = hostLmsService;
	}

	@Override
	protected boolean checkApplicability(RequestWorkflowContext context) {
		return true;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext context) {
		return Mono.just(context)
			.flatMap(this::markNoItemsAvailableAtAnyAgency);
	}

	private Mono<RequestWorkflowContext> markNoItemsAvailableAtAnyAgency(RequestWorkflowContext context) {
		final var patronRequest = getValue(context, RequestWorkflowContext::getPatronRequest, null);

		return Mono.just(context.setPatronRequest(
			patronRequest.setStatus(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY)));
	}

	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "ResolveNextSupplierTransition";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition("DCBRequestStatus is (NOT_SUPPLIED_CURRENT_SUPPLIER)"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("NO_ITEMS_AVAILABLE_AT_ANY_AGENCY", NO_ITEMS_AVAILABLE_AT_ANY_AGENCY.toString()));
	}
}

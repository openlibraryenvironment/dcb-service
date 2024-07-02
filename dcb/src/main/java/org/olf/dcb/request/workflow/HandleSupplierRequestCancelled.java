package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.PatronRequest.Status.NOT_SUPPLIED_CURRENT_SUPPLIER;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;

import java.util.List;
import java.util.Optional;

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
@Named("SupplierRequestCancelled")
public class HandleSupplierRequestCancelled extends AbstractPatronRequestStateTransition
	implements PatronRequestStateTransition {

	HandleSupplierRequestCancelled() {
		super(List.of(REQUEST_PLACED_AT_SUPPLYING_AGENCY));
	}

	@Override
	protected boolean checkApplicability(RequestWorkflowContext context) {
		return true;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		return Mono.just(ctx);
	}

	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(NOT_SUPPLIED_CURRENT_SUPPLIER);
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
		return List.of(new DCBGuardCondition("DCBRequestStatus is (REQUEST_PLACED_AT_SUPPLYING_AGENCY OR CONFIRMED OR REQUEST_PLACED_AT_BORROWING_AGENCY)" +
			"supplying request local status is (MISSING OR CANCELLED)"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("NOT_SUPPLIED_CURRENT_SUPPLIER", NOT_SUPPLIED_CURRENT_SUPPLIER.toString()));
	}
}

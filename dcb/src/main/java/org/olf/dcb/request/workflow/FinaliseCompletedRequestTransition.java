package org.olf.dcb.request.workflow;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class FinaliseCompletedRequestTransition implements PatronRequestStateTransition {
	private final PatronRequestAuditService patronRequestAuditService;
	private final SupplyingAgencyService supplyingAgencyService;
	private final BorrowingAgencyService borrowingAgencyService;

	// Provider to prevent circular reference exception by allowing lazy access to
	// this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	private static final List<Status> possibleSourceStatus = List.of(Status.COMPLETED);
	
	public FinaliseCompletedRequestTransition(PatronRequestAuditService patronRequestAuditService,
		SupplyingAgencyService supplyingAgencyService, BorrowingAgencyService borrowingAgencyService,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider) {

		this.patronRequestAuditService = patronRequestAuditService;
		this.supplyingAgencyService = supplyingAgencyService;
		this.borrowingAgencyService = borrowingAgencyService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
	}

	/**
	 * Attempts to transition the patron request to the next state, 
	 *
	 * @param ctx The patron request context to transition.
	 * @return A Mono that emits the patron request after the transition, or an
	 *         error if the transition is not possible.
	 */
	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {

		PatronRequest patronRequest = ctx.getPatronRequest();
		log.debug("WORKFLOW FinaliseCompletedRequestTransition firing for {}", patronRequest);

		return Mono.just(patronRequest)
			.flatMap(supplyingAgencyService::cleanUp)
			.flatMap(borrowingAgencyService::cleanUp)
			.flatMap(checkForErrorString(patronRequest))
			.then(Mono.just(patronRequest.setStatus(Status.FINALISED)))
			.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest))
			.thenReturn(ctx);
	}

	// Method providing an audit for returned ERROR strings.
	// ERROR Strings are returned instead of empties
	// TODO: Needs reviewing, should this be a hard stop?
	private Function<String, Mono<? extends String>> checkForErrorString(PatronRequest patronRequest) {
		return string -> {
			if (Objects.equals(string, "ERROR")) {
				final var auditData = new HashMap<String, Object>();
				auditData.put("Reason", "Mono with 'ERROR' String returned from cleanup.");
				return patronRequestAuditService.addAuditEntry(patronRequest,
						"Action Failed : FinaliseCompletedRequestTransition", auditData)
					.then(Mono.just(string));
			}
			return Mono.just(string);
		};
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus());
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.FINALISED);
	}

  @Override
	public boolean attemptAutomatically() {
		return true;
	}

  @Override
  public String getName() {
    return "FinaliseCompletedRequestTransition";
  }

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("FINALISED", Status.FINALISED.toString()));
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition("DCBPatronRequest status is COMPLETED"));
	}
}

package org.olf.dcb.request.workflow;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import org.zalando.problem.Problem;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowableMonoWrap;
import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.putAuditData;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.ReactorUtils.raiseError;

@Slf4j
@Prototype
public class FinaliseRequestTransition implements PatronRequestStateTransition {
	private final PatronRequestAuditService patronRequestAuditService;
	private final SupplyingAgencyService supplyingAgencyService;
	private final BorrowingAgencyService borrowingAgencyService;

	// Provider to prevent circular reference exception by allowing lazy access to
	// this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	private static final List<Status> possibleSourceStatus = List.of(Status.COMPLETED, Status. CANCELLED);

	public FinaliseRequestTransition(PatronRequestAuditService patronRequestAuditService,
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
		log.debug("WORKFLOW FinaliseRequestTransition firing for {}", patronRequest);

		return Mono.just(ctx)
			.flatMap(supplyingAgencyService::cleanUp)
			.flatMap(borrowingAgencyService::cleanUp)
			.then(Mono.just(patronRequest.setStatus(Status.FINALISED)))
			.flatMap(auditStateOfVirtualRecordsAfterCleanUp(ctx));
	}

	private Function<PatronRequest, Mono<RequestWorkflowContext>> auditStateOfVirtualRecordsAfterCleanUp(
		RequestWorkflowContext ctx) {

		return patronRequest -> {

			var auditData = new HashMap<String, Object>();

			return fetchVirtualItem(ctx, auditData)
				.flatMap(data -> fetchVirtualPatron(ctx, data))
				.flatMap(data -> fetchVirtualRequest(ctx, data))
				.flatMap(data -> patronRequestAuditService.addAuditEntry(ctx.getPatronRequest(), "Clean up result", data))
				.thenReturn(ctx);
		};
	}

	private Mono<HashMap<String, Object>> fetchVirtualRequest(RequestWorkflowContext ctx, HashMap<String, Object> auditData) {
		return supplyingAgencyService.getRequest(ctx.getSupplierRequest().getHostLmsCode(), ctx.getSupplierRequest().getLocalId())
			.map(request -> putAuditData(auditData,"VirtualRequest", getValueOrNull(request, HostLmsRequest::toString)))
			.onErrorResume(error -> auditThrowableMonoWrap(auditData, "VirtualRequest", error))
			.thenReturn(auditData);
	}

	private Mono<HashMap<String, Object>> fetchVirtualPatron(RequestWorkflowContext ctx, HashMap<String, Object> auditData) {
		return supplyingAgencyService.getPatron(ctx)
			.map(patron -> putAuditData(auditData, "VirtualPatron", getValueOrNull(patron, Patron::toString)))
			.onErrorResume(error -> auditThrowableMonoWrap(auditData, "VirtualPatron", error))
			.thenReturn(auditData);
	}

	private Mono<HashMap<String, Object>> fetchVirtualItem(RequestWorkflowContext ctx, HashMap<String, Object> auditData) {
		return borrowingAgencyService.getItem(ctx.getPatronRequest())
			.map(item -> putAuditData(auditData, "VirtualItem", getValueOrNull(item, HostLmsItem::toString)))
			.onErrorResume(error -> auditThrowableMonoWrap(auditData, "VirtualItem", error))
			.thenReturn(auditData);
	}

	private Function<String, Mono<String>> checkForErrorString(RequestWorkflowContext ctx) {
		return string -> {

			if (Objects.equals(string, "ERROR")) {
				return raiseError(Problem.builder()
					.withTitle("Mono with 'ERROR' String returned from cleanup")
					.build());
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
    return "FinaliseRequestTransition";
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

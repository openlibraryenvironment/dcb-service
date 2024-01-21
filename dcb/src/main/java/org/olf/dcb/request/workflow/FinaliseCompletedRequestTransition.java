package org.olf.dcb.request.workflow;

import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
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
	 * @param patronRequest The patron request to transition.
	 * @return A Mono that emits the patron request after the transition, or an
	 *         error if the transition is not possible.
	 */
	@Override
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {
		log.debug("FinaliseCompletedRequestTransition firing for {}", patronRequest);

		assert isApplicableFor(patronRequest);

// auditService.addAuditEntry(pr, "Supplier Item Available - Infer item back on the shelf after loan. Completing request");

		return Mono.just(patronRequest)
			.flatMap(supplyingAgencyService::cleanUp)
			.flatMap(borrowingAgencyService::cleanUp)
			.then(Mono.just(patronRequest.setStatus(Status.FINALISED)))
			.flatMap(this::createAuditEntry)
			.transform(patronRequestWorkflowServiceProvider.get()
				.getErrorTransformerFor(patronRequest));
	}


	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {

		if (patronRequest.getStatus() == Status.ERROR)
			return Mono.just(patronRequest);

		return patronRequestAuditService.addAuditEntry(patronRequest, Status.COMPLETED, getTargetStatus().get())
			.thenReturn(patronRequest);
	}

	@Override
	public boolean isApplicableFor(PatronRequest pr) {
		return pr.getStatus() == Status.COMPLETED;
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.FINALISED);
	}
}

package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;

import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class PlacePatronRequestAtBorrowingAgencyStateTransition implements PatronRequestStateTransition {
	private final BorrowingAgencyService borrowingAgencyService;
	private final PatronRequestAuditService patronRequestAuditService;

	public PlacePatronRequestAtBorrowingAgencyStateTransition(
		BorrowingAgencyService borrowingAgencyService,
		PatronRequestAuditService patronRequestAuditService) {

		this.borrowingAgencyService = borrowingAgencyService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	/**
	 * Attempts to transition the patron request to the next state, which is placing
	 * the request at the borrowing agency.
	 *
	 * @param ctx the request context
	 * @return A Mono that emits the patron request after the transition, or an
	 *         error if the transition is not possible.
	 */
	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {

		log.info("makeTransition({})", ctx.getPatronRequest());
		return borrowingAgencyService.placePatronRequestAtBorrowingAgency(ctx.getPatronRequest())
				.doOnSuccess(pr -> log.info("Placed patron request to borrowing agency: {}", pr))
        .doOnError(error -> log.error("Error occurred during placing a patron request to borrowing agency: {}", error.getMessage()))
				.flatMap(this::createAuditEntry)
				.thenReturn(ctx);
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return ctx.getPatronRequest().getStatus() == Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
	}

	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {
		if (patronRequest.getStatus() == ERROR)
			return Mono.just(patronRequest);

		return patronRequestAuditService
				.addAuditEntry(patronRequest, REQUEST_PLACED_AT_SUPPLYING_AGENCY, getTargetStatus().get())
			.map(PatronRequestAudit::getPatronRequest);
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(REQUEST_PLACED_AT_BORROWING_AGENCY);
	}

  @Override
  public String getName() {
    return "PlacePatronRequestAtBorrowingAgencyStateTransition";
  }

  @Override
  public boolean attemptAutomatically() {
    return true;
  }


}

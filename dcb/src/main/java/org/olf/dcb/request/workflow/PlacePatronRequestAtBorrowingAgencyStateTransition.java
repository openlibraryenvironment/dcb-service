package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class PlacePatronRequestAtBorrowingAgencyStateTransition implements PatronRequestStateTransition {
	private final BorrowingAgencyService borrowingAgencyService;
	private final PatronRequestAuditService patronRequestAuditService;

	private static final List<Status> possibleSourceStatus = List.of(CONFIRMED);
	
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

		final var statusUponEntry = ctx.getPatronRequest().getStatus();

		return borrowingAgencyService.placePatronRequestAtBorrowingAgency(ctx)
			.doOnSuccess(pr -> {
				log.info("Placed patron request to borrowing agency: {}", pr);
				ctx.getWorkflowMessages().add("Placed patron request to borrowing agency");
			})
			.doOnError(error -> {
				log.error("Error occurred during placing a patron request to borrowing agency: {}", error.getMessage());
				ctx.getWorkflowMessages().add("Error occurred during placing a patron request to borrowing agency: "+error.getMessage());
			})
			.thenReturn(ctx);
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus());
	}

	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest,
		Status statusUponEntry) {

		if (patronRequest.getStatus() == ERROR)
			return Mono.just(patronRequest);

		return patronRequestAuditService
			.addAuditEntry(patronRequest, statusUponEntry, getTargetStatus().get())
			.map(PatronRequestAudit::getPatronRequest);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
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

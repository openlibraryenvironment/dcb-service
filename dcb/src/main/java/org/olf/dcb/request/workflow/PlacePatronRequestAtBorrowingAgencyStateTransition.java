package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Slf4j
@Prototype
public class PlacePatronRequestAtBorrowingAgencyStateTransition implements PatronRequestStateTransition {
	private final BorrowingAgencyService borrowingAgencyService;

	private static final List<Status> possibleSourceStatus = List.of(CONFIRMED);
	
	public PlacePatronRequestAtBorrowingAgencyStateTransition(BorrowingAgencyService borrowingAgencyService) {
		this.borrowingAgencyService = borrowingAgencyService;
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

		final var patronRequest = getValueOrNull(ctx, RequestWorkflowContext::getPatronRequest);
		final var resolutionCount = getValueOrNull(patronRequest, PatronRequest::getResolutionCount);

		if (resolutionCount != null && resolutionCount > 1) {
			return borrowingAgencyService.updatePatronRequestAtBorrowingAgency(ctx)
				.doOnSuccess(pr -> {
					log.info("Updated patron request at borrowing agency: {}", pr);
					ctx.getWorkflowMessages().add("Updated patron request at borrowing agency");
				})
				.doOnError(error -> {
					log.error("Error occurred during updating a patron request at borrowing agency: {}", error.getMessage());
					ctx.getWorkflowMessages().add("Error occurred during updating a patron request at borrowing agency: "+error.getMessage());
				})
				.thenReturn(ctx);
		}

		return borrowingAgencyService.placePatronRequestAtBorrowingAgency(ctx)
			.doOnSuccess(pr -> {
				log.info("Placed patron request to borrowing agency: {}", pr);

				final var localBibId = getValue(pr, PatronRequest::getLocalBibId, "none");
				final var localHoldingId = getValue(pr, PatronRequest::getLocalHoldingId, "none");
				final var localItemId = getValue(pr, PatronRequest::getLocalItemId, "none");
				final var localRequestId = getValue(pr, PatronRequest::getLocalRequestId, "none");

				ctx.getWorkflowMessages().add("Placed patron request to borrowing agency");
				ctx.getWorkflowMessages().add("Local bib ID: \"%s\"".formatted(localBibId));
				ctx.getWorkflowMessages().add("Local holding ID: \"%s\"".formatted(localHoldingId));
				ctx.getWorkflowMessages().add("Local item ID: \"%s\"".formatted(localItemId));
				ctx.getWorkflowMessages().add("Local request ID: \"%s\"".formatted(localRequestId));
			})
			.doOnError(error -> {
				String msg = "Error occurred during placing a patron request to borrowing agency(%s@%s): %s".format(
					ctx.getPatronAgencyCode() != null ? ctx.getPatronAgencyCode() : "MISSING",
					ctx.getPatronSystemCode() != null ? ctx.getPatronSystemCode() : "MISSING",
					error.getMessage());
				log.error(msg);
				ctx.getWorkflowMessages().add(msg);
			})
			.thenReturn(ctx);
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		// Local should only be handed off to the local system.
		if (ctx.getPatronRequest().isUsingLocalWorkflow()) return false;
		
		return getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus());
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

package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
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

	public PlacePatronRequestAtBorrowingAgencyStateTransition(BorrowingAgencyService borrowingAgencyService,
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

		final var patronRequest = getValueOrNull(ctx, RequestWorkflowContext::getPatronRequest);
		final var resolutionCount = getValueOrNull(patronRequest, PatronRequest::getResolutionCount);
		final var initialLocalRequestId = getValueOrNull(patronRequest, PatronRequest::getLocalRequestId);

		if (resolutionCount != null && resolutionCount > 1) {
			if (initialLocalRequestId == null) {
				// In a scenario where we are re-resolving from the local workflow to a standard request,
				// there won't be a request at the borrower to update (see DCB-2111) and thus no local request ID
				// If that is the case, we must make sure we place one, or we'll end up trying to update something that doesn't exist
				log.info("Potential re-resolution from RET-LOCAL to RET-STD detected for {}", patronRequest.getId());
				final Map<String, Object> auditData = getAuditData(ctx, patronRequest);
				return patronRequestAuditService.addAuditEntry(patronRequest, "Re-resolution: potential re-resolution from RET-LOCAL to RET-STD: attempting to place request at borrower", auditData)
					.then(borrowingAgencyService.placePatronRequestAtBorrowingAgency(ctx))
					.doOnSuccess(pr -> log.info("Re-resolution: RET-LOCAL to RET-STD detected, successfully placed request at borrower: {}", pr))
					.doOnError(error -> log.error("Re-resolution: RET-LOCAL to RET-STD, error occurred when placing a patron request at borrowing agency({}@{}): {}",
						patronRequest.getRequestingIdentity().getLocalId(),
						patronRequest.getPatronHostlmsCode(),
						error.getMessage()))
					.thenReturn(ctx);
			}
			else
			{
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

	private static Map<String, Object> getAuditData(RequestWorkflowContext ctx, PatronRequest patronRequest) {
		final Map<String, Object> auditData = new HashMap<>();
		auditData.put("activeWorkflow", patronRequest.getActiveWorkflow());
		auditData.put("resolutionCount", patronRequest.getResolutionCount());
		auditData.put("borrowingAgencyCode", ctx.getPatronAgencyCode());
		auditData.put("patronId", patronRequest.getRequestingIdentity().getLocalId());
		auditData.put("description", "Re-resolution detected without LocalRequestId, suggesting change in workflow from RET-LOCAL to RET-STD. Creating new request at borrowing agency.");
		return auditData;
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

package org.olf.dcb.request.workflow;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.resolution.SupplierRequestService;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.olf.dcb.core.model.PatronRequest.Status.*;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

@Slf4j
@Prototype
public class PlaceRequestAtOwnLibraryStateTransition implements PatronRequestStateTransition {
	private final BorrowingAgencyService borrowingAgencyService;
	private final SupplierRequestService supplierRequestService;
	private final PatronRequestAuditService patronRequestAuditService;

	private static final List<Status> possibleSourceStatus = List.of(RESOLVED);

	public PlaceRequestAtOwnLibraryStateTransition(
		BorrowingAgencyService borrowingAgencyService,
		SupplierRequestService supplierRequestService,
		PatronRequestAuditService patronRequestAuditService) {

		this.borrowingAgencyService = borrowingAgencyService;
		this.supplierRequestService = supplierRequestService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
			log.info("makeTransition({})", ctx.getPatronRequest());

			final var supplierRequest = ctx.getSupplierRequest();
			final var patronRequest = ctx.getPatronRequest();

			return borrowingAgencyService.placeSingularRequest(ctx)
				.map(lr -> supplierRequest.placed(
					lr.getLocalId(), lr.getLocalStatus(),
					lr.getRawLocalStatus(), lr.getRequestedItemId(),
					lr.getRequestedItemBarcode()))
				.flatMap(supplierRequestService::updateSupplierRequest)
				.thenReturn(patronRequest)
				.map(PatronRequest::placedAtSupplyingAgency)
				.doOnSuccess(pr -> {
					log.info("Placed singular patron request: {}", pr);
					ctx.getWorkflowMessages().add("Placed singular patron request at own library");
				})
				.doOnError(error -> {
					log.error("Error occurred during placing a singular patron request: {}", error.getMessage());
					ctx.getWorkflowMessages().add("Error occurred during updating a patron request at borrowing agency: "+error.getMessage());
				})
				.thenReturn(ctx);
		}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final var patronRequest = getValueOrNull(ctx, RequestWorkflowContext::getPatronRequest);
		return isStatusApplicable(patronRequest) && isActiveWorkflowApplicable(patronRequest);
	}

	private boolean isStatusApplicable(PatronRequest patronRequest) {
		return Optional.ofNullable(patronRequest)
			.map(PatronRequest::getStatus)
			.map(getPossibleSourceStatus()::contains)
			.orElse(false);
	}

	private boolean isActiveWorkflowApplicable(PatronRequest patronRequest) {
		return Optional.ofNullable(patronRequest)
			.map(PatronRequest::getActiveWorkflow)
			.map("RET-LOCAL"::equals)
			.orElse(false);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(REQUEST_PLACED_AT_SUPPLYING_AGENCY);
	}

  @Override
  public String getName() {
    return "PlaceRequestAtOwnLibraryStateTransition";
  }

  @Override
  public boolean attemptAutomatically() {
    return true;
  }
}

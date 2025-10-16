package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class PlacePatronRequestAtSupplyingAgencyStateTransition implements PatronRequestStateTransition {
	private final SupplyingAgencyService supplyingAgencyService;
	private final PatronRequestAuditService patronRequestAuditService;

	private static final List<Status> possibleSourceStatus = List.of(Status.RESOLVED);
	
	public PlacePatronRequestAtSupplyingAgencyStateTransition(
		SupplyingAgencyService supplierRequestService,
		PatronRequestAuditService patronRequestAuditService) {

		this.supplyingAgencyService = supplierRequestService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	/**
	 * Attempts to transition the patron request to the next state, which is placing
	 * the request at the supplying agency.
	 *
	 * @param ctx The requesting context containing all needed data
	 * @return A Mono that emits the patron request after the transition, or an
	 *         error if the transition is not possible.
	 */
	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {

		log.debug("PlacePatronRequestAtSupplyingAgencyStateTransition firing for {}", ctx.getPatronRequest());

		// Note: supplyingAgencyService.placePatronRequestAtSupplyingAgency will eventually call PatronRequest::placedAtSupplyingAgency

		return supplyingAgencyService.placePatronRequestAtSupplyingAgency(ctx.getPatronRequest())
			.doOnSuccess(pr -> {
				log.debug("Placed patron request to supplier: pr={}", pr);
				ctx.getWorkflowMessages().add("Placed patron request to supplier");
				addAuditDetail(ctx);
			})
			.doOnError(error -> {
				log.error("Error occurred during placing a patron request to supplier: {}", error.getMessage());
				ctx.getWorkflowMessages().add("Error occurred during placing a patron request to supplier: "+error.getMessage());
				addAuditDetail(ctx);
			})
			.thenReturn(ctx);
	}

	private void addAuditDetail(RequestWorkflowContext ctx) {
		if ( ctx != null ) {
			ctx.getWorkflowMessages().add("Pickup library "+ ( ctx.getPickupLibrary() != null ? ctx.getPickupLibrary().getAbbreviatedName() : "MISSING" ) );
			ctx.getWorkflowMessages().add("Pickup symbol "+ctx.getPatronRequest().getPickupLocationCode());
			ctx.getWorkflowMessages().add("Pickup agency code "+ctx.getPickupAgencyCode());
			ctx.getWorkflowMessages().add("Pickup system code "+ctx.getPickupSystemCode());
			ctx.getWorkflowMessages().add("Pickup location code "+ ( ctx.getPickupLocation() != null ? ctx.getPickupLocation().getCode() : "MISSING" ) );
			ctx.getWorkflowMessages().add("Transaction note "+ ctx.generateTransactionNote() );
		}
	}

	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {

		if (patronRequest.getStatus() == Status.ERROR)
			return Mono.just(patronRequest);
		return patronRequestAuditService.addAuditEntry(patronRequest, Status.RESOLVED, getTargetStatus().get())
				.map(PatronRequestAudit::getPatronRequest);
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final PatronRequest patronRequest = ctx.getPatronRequest();
		final boolean isStatusApplicable = getPossibleSourceStatus().contains(patronRequest.getStatus());
		final boolean isNotLocalWorkflow = !patronRequest.isUsingLocalWorkflow();

		return isStatusApplicable && isNotLocalWorkflow;
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY);
	}

  @Override     
  public String getName() {
    return "PlacePatronRequestAtSupplyingAgencyStateTransition";
  }

	@Override
	public boolean attemptAutomatically() {
		return true;
	}
}

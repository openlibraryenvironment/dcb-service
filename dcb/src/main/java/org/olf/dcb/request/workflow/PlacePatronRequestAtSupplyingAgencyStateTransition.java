package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class PlacePatronRequestAtSupplyingAgencyStateTransition implements PatronRequestStateTransition {

	private static final Logger log = LoggerFactory.getLogger(PlacePatronRequestAtSupplyingAgencyStateTransition.class);

	private final SupplyingAgencyService supplyingAgencyService;
	private final PatronRequestAuditService patronRequestAuditService;

	private static final List<Status> possibleSourceStatus = List.of(Status.RESOLVED);
	
	public PlacePatronRequestAtSupplyingAgencyStateTransition(SupplyingAgencyService supplierRequestService,
			PatronRequestRepository patronRequestRepository, PatronRequestAuditService patronRequestAuditService) {
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

		return supplyingAgencyService.placePatronRequestAtSupplyingAgency(ctx.getPatronRequest())
				.doOnSuccess(pr -> log.debug("Placed patron request to supplier: pr={}", pr))
				.doOnError(error -> log.error("Error occurred during placing a patron request to supplier: {}", error.getMessage()))
				.flatMap(this::createAuditEntry)
				.thenReturn(ctx);
	}

	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {

		if (patronRequest.getStatus() == Status.ERROR)
			return Mono.just(patronRequest);
		return patronRequestAuditService.addAuditEntry(patronRequest, Status.RESOLVED, getTargetStatus().get())
				.map(PatronRequestAudit::getPatronRequest);
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

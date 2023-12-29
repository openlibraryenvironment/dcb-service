package org.olf.dcb.request.workflow;

import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
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

	public PlacePatronRequestAtSupplyingAgencyStateTransition(SupplyingAgencyService supplierRequestService,
			PatronRequestRepository patronRequestRepository, PatronRequestAuditService patronRequestAuditService) {
		this.supplyingAgencyService = supplierRequestService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	/**
	 * Attempts to transition the patron request to the next state, which is placing
	 * the request at the supplying agency.
	 *
	 * @param patronRequest The patron request to transition.
	 * @return A Mono that emits the patron request after the transition, or an
	 *         error if the transition is not possible.
	 */
	@Override
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {

		// Some of the tests seem to set up odd states and then explicitly invoke the
		// attempt method. Transitions should
		// assert the correct state.
		assert isApplicableFor(patronRequest);

		log.debug("PlacePatronRequestAtSupplyingAgencyStateTransition firing for {}", patronRequest);

		return supplyingAgencyService.placePatronRequestAtSupplyingAgency(patronRequest)
				.doOnSuccess(pr -> log.debug("Placed patron request to supplier: pr={}", pr))
				.doOnError(error -> log.error("Error occurred during placing a patron request to supplier: {}", error.getMessage()))
				.flatMap(this::createAuditEntry);
	}

	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {

		if (patronRequest.getStatus() == Status.ERROR)
			return Mono.just(patronRequest);
		return patronRequestAuditService.addAuditEntry(patronRequest, Status.RESOLVED, getTargetStatus().get())
				.map(PatronRequestAudit::getPatronRequest);
	}

	@Override
	public boolean isApplicableFor(PatronRequest pr) {
		return pr.getStatus() == Status.RESOLVED;
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY);
	}
}

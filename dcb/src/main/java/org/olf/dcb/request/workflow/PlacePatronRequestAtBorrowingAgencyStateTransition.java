package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;

import java.time.Instant;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class PlacePatronRequestAtBorrowingAgencyStateTransition implements PatronRequestStateTransition {

	private static final Logger log =
		LoggerFactory.getLogger(PlacePatronRequestAtBorrowingAgencyStateTransition.class);

	private final BorrowingAgencyService borrowingAgencyService;
	private final PatronRequestAuditService patronRequestAuditService;

	public PlacePatronRequestAtBorrowingAgencyStateTransition(
		BorrowingAgencyService borrowingAgencyService,
		PatronRequestRepository patronRequestRepository, PatronRequestAuditService patronRequestAuditService) {
		this.borrowingAgencyService = borrowingAgencyService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	/**
	 * Attempts to transition the patron request to the next state, which is placing the request at the borrowing agency.
	 *
	 * @param patronRequest The patron request to transition.
	 * @return A Mono that emits the patron request after the transition, or an error if the transition is not possible.
	 */
	@Override
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {
		log.debug("makeTransition({})", patronRequest);
		return borrowingAgencyService.placePatronRequestAtBorrowingAgency(patronRequest)
			.doOnSuccess(
				pr -> log.debug("Placed patron request to borrowing agency: {}", pr))
			.doOnError(
				error -> log.error(
					"Error occurred during placing a patron request to borrowing agency: {}",
					error.getMessage()))
			.flatMap(this::createAuditEntry);

	}
	@Override
	public boolean isApplicableFor(PatronRequest pr) {
    return pr.getStatus() == Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
	}
	
	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {
		
		if (patronRequest.getStatus() == Status.ERROR) return Mono.just(patronRequest);
		return patronRequestAuditService
				.addAuditEntry(patronRequest,	Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY, Status.REQUEST_PLACED_AT_BORROWING_AGENCY)
					.map(PatronRequestAudit::getPatronRequest);
	}
}



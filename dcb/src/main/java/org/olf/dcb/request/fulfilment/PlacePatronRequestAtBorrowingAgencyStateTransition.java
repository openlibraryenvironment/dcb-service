package org.olf.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.request.fulfilment.PatronRequestStatusConstants.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.request.fulfilment.PatronRequestStatusConstants.REQUEST_PLACED_AT_SUPPLYING_AGENCY;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Prototype
public class PlacePatronRequestAtBorrowingAgencyStateTransition implements PatronRequestStateTransition {

	private static final Logger log =
		LoggerFactory.getLogger(PlacePatronRequestAtBorrowingAgencyStateTransition.class);

	private final BorrowingAgencyService borrowingAgencyService;
	private final PatronRequestRepository patronRequestRepository;

	private final PatronRequestAuditService patronRequestAuditService;

	public PlacePatronRequestAtBorrowingAgencyStateTransition(
		BorrowingAgencyService borrowingAgencyService,
		PatronRequestRepository patronRequestRepository,
		PatronRequestAuditService patronRequestAuditService) {

		this.borrowingAgencyService = borrowingAgencyService;
		this.patronRequestRepository = patronRequestRepository;
		this.patronRequestAuditService = patronRequestAuditService;
	}
	public String getGuardCondition() {
                return "state=="+REQUEST_PLACED_AT_SUPPLYING_AGENCY;
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
			.flatMap(this::updatePatronRequest)
			.flatMap(this::createAuditEntry);

	}

	private Mono<PatronRequest> updatePatronRequest(PatronRequest patronRequest) {
		log.debug("updatePatronRequest {}", patronRequest);
		return Mono.from(patronRequestRepository.update(patronRequest));
	}

	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {

		var audit = PatronRequestAudit.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.auditDate(Instant.now())
			.fromStatus(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.toStatus(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.build();

		return patronRequestAuditService.audit(audit, false).map(PatronRequestAudit::getPatronRequest);
	}
}



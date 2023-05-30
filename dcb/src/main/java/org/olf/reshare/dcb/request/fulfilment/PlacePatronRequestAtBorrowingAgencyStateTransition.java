package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.REQUEST_PLACED_AT_SUPPLYING_AGENCY;

@Prototype
public class PlacePatronRequestAtBorrowingAgencyStateTransition implements PatronRequestStateTransition {

	private static final Logger log =
		LoggerFactory.getLogger(PlacePatronRequestAtBorrowingAgencyStateTransition.class);

	private final BorrowingAgencyService borrowingAgencyService;
	private final PatronRequestRepository patronRequestRepository;

	public PlacePatronRequestAtBorrowingAgencyStateTransition(
		BorrowingAgencyService borrowingAgencyService,
		PatronRequestRepository patronRequestRepository) {
		this.borrowingAgencyService = borrowingAgencyService;
		this.patronRequestRepository = patronRequestRepository;
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
			.flatMap(this::updatePatronRequest);
	}

	private Mono<PatronRequest> updatePatronRequest(PatronRequest patronRequest) {
		log.debug("updatePatronRequest {}", patronRequest);
		return Mono.from(patronRequestRepository.update(patronRequest));
	}
}


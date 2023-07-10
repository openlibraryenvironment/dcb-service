package org.olf.dcb.request.workflow;

import static org.olf.dcb.request.workflow.PatronRequestStatusConstants.RESOLVED;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class PlacePatronRequestAtSupplyingAgencyStateTransition implements PatronRequestStateTransition {

	private static final Logger log =
		LoggerFactory.getLogger(PlacePatronRequestAtSupplyingAgencyStateTransition.class);

	private final SupplyingAgencyService supplyingAgencyService;
	private final PatronRequestRepository patronRequestRepository;

	public PlacePatronRequestAtSupplyingAgencyStateTransition(
		SupplyingAgencyService supplierRequestService,
		PatronRequestRepository patronRequestRepository) {
		this.supplyingAgencyService = supplierRequestService;
		this.patronRequestRepository = patronRequestRepository;
	}

        public String getGuardCondition() {
                return "state=="+RESOLVED;
        }

	/**
	 * Attempts to transition the patron request to the next state, which is placing the request at the supplying agency.
	 *
	 * @param patronRequest The patron request to transition.
	 * @return A Mono that emits the patron request after the transition, or an error if the transition is not possible.
	 */
	@Override
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {
		log.debug("makeTransition({})", patronRequest);
		return supplyingAgencyService.placePatronRequestAtSupplyingAgency(patronRequest)
			.doOnSuccess(
				pr -> log.debug("Placed patron request to supplier: {}", pr))
			.doOnError(
				error -> log.error(
					"Error occurred during placing a patron request to supplier: {}",
					error.getMessage()))
			.flatMap(this::updatePatronRequest);
	}

	private Mono<PatronRequest> updatePatronRequest(PatronRequest patronRequest) {
		log.debug("updatePatronRequest {}", patronRequest);
		return Mono.from(patronRequestRepository.update(patronRequest));
	}
}


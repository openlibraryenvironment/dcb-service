package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.ERROR;

@Prototype
public class PatronRequestTransitionErrorService {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestTransitionErrorService.class);

	private final PatronRequestRepository patronRequestRepository;

	public PatronRequestTransitionErrorService(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	/**
	 * Sets the status code of the patron request to ERROR and returns a Mono that completes with an error,
	 * mimicking the behavior of 'doOnError'.
	 * This function is called using 'onErrorResume' instead of 'doOnError'
	 * to ensure the patron request is updated before the error is returned.
	 */
	public Mono<PatronRequest> moveRequestToErrorStatus(Throwable error, PatronRequest patronRequest) {
		log.debug("Setting patron request status code: {}", ERROR);

		patronRequest.setStatusCode(ERROR);
		return Mono.from(patronRequestRepository.update(patronRequest)).then(Mono.error(error));
	}

}

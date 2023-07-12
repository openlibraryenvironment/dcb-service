package org.olf.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;

import static org.olf.dcb.request.fulfilment.PatronRequestStatusConstants.ERROR;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@Prototype
public class PatronRequestTransitionErrorService {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestTransitionErrorService.class);

	private final PatronRequestRepository patronRequestRepository;

	private final PatronRequestAuditService patronRequestAuditService;


	public PatronRequestTransitionErrorService(PatronRequestRepository patronRequestRepository,
		PatronRequestAuditService patronRequestAuditService) {

		this.patronRequestRepository = patronRequestRepository;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	/**
	 * Sets the status code of the patron request to ERROR and returns a Mono that completes with an error,
	 * mimicking the behavior of 'doOnError'.
	 * This function is called using 'onErrorResume' instead of 'doOnError'
	 * to ensure the patron request is updated before the error is returned.
	 */
	public Mono<PatronRequest> recordError(Throwable error, PatronRequestAudit patronRequestAudit) {

		log.debug("Setting patron request status code: {}", ERROR);

		var patronRequest = patronRequestAudit.getPatronRequest();
		patronRequest.setStatusCode(ERROR);
		patronRequest.setErrorMessage(error.getMessage());

		return Mono.from(patronRequestRepository.update(patronRequest))
			.flatMap(pr -> patronRequestAuditService.audit(patronRequestAudit, true))
			.then(Mono.error(error));
	}
}

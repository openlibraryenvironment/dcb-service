package org.olf.dcb.request.fulfilment;

import static java.lang.Boolean.TRUE;
import static org.olf.dcb.request.fulfilment.PatronRequestStatusConstants.PATRON_VERIFIED;
import static org.olf.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.time.Instant;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class ValidatePatronTransition implements PatronRequestStateTransition {
	private static final Logger log = LoggerFactory.getLogger(ValidatePatronTransition.class);

	private final PatronRequestRepository patronRequestRepository;
	private final PatronIdentityRepository patronIdentityRepository;
	private final HostLmsService hostLmsService;
	private final PatronRequestTransitionErrorService errorService;

	public ValidatePatronTransition(PatronRequestRepository patronRequestRepository,
		PatronIdentityRepository patronIdentityRepository, HostLmsService hostLmsService,
		PatronRequestTransitionErrorService errorService) {

		this.patronRequestRepository = patronRequestRepository;
		this.patronIdentityRepository = patronIdentityRepository;
		this.hostLmsService = hostLmsService;
		this.errorService = errorService;
	}

	public String getGuardCondition() {
		return "state=="+SUBMITTED_TO_DCB;
	}

	/**
	 * We are passed in a local patron identity record
	 * Validate and refresh any local properties we wish to sync before commencement of the requesting process.
 	 */
	private Mono<PatronIdentity> validatePatronIdentity(PatronIdentity pi) {
		log.debug("validatePatronIdentity by calling out to host LMS - PI is {} host lms client is {}", pi, pi.getHostLms());
		return hostLmsService.getClientFor(pi.getHostLms())
			.flatMap(client -> client.getPatronByLocalId(pi.getLocalId()))
			.flatMap(hostLmsPatron -> {
				log.debug("update patron identity with latest info from host {}", hostLmsPatron);

				// Update the patron identity with the current patron type and set the last validated date to now()
				pi.setLocalPtype(hostLmsPatron.getLocalPatronType());
				pi.setLastValidated(Instant.now());
				pi.setLocalBarcode(hostLmsPatron.getLocalBarcodes());
				pi.setLocalNames(hostLmsPatron.getLocalNames());

				return Mono.fromDirect(patronIdentityRepository.saveOrUpdate(pi));
			});
	}

	private PatronRequest setRequestingPatronIdentity(PatronRequest patronRequest,
		PatronIdentity pi) {

		patronRequest.setRequestingIdentity(pi);

		return patronRequest;
	}

	/**
	 * Attempts to transition the patron request to the next state, which is placing the request at the supplying agency.
	 *
	 * @param patronRequest The patron request to transition.
	 * @return A Mono that emits the patron request after the transition, or an error if the transition is not possible.
	 */
	@Override
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {
		log.debug("verifyPatron {}", patronRequest);

		patronRequest.setStatusCode(PATRON_VERIFIED);
		
		// pull out patronRequest.patron and get the home patron then use the web service to look up the patron
		// patronRequest.patron
		return Mono.from(patronIdentityRepository.findOneByPatronIdAndHomeIdentity(patronRequest.getPatron().getId(), TRUE))
			.flatMap(this::validatePatronIdentity)
			.map(pi -> this.setRequestingPatronIdentity(patronRequest, pi))
			.then(updatePatronRequest(patronRequest))
			.onErrorResume(error -> errorService.moveRequestToErrorStatus(error, patronRequest));
	}

	private Mono<PatronRequest> updatePatronRequest(PatronRequest patronRequest) {
		return Mono.fromDirect(patronRequestRepository.update(patronRequest));
	}
}

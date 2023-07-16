package org.olf.dcb.request.workflow;

import static java.lang.Boolean.TRUE;

import java.time.Instant;
import java.util.Objects;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import io.micronaut.context.BeanProvider;


@Prototype
public class ValidatePatronTransition implements PatronRequestStateTransition {
	private static final Logger log = LoggerFactory.getLogger(ValidatePatronTransition.class);

	private final PatronIdentityRepository patronIdentityRepository;
	private final HostLmsService hostLmsService;
	private final PatronRequestAuditService patronRequestAuditService;

	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
        private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	public ValidatePatronTransition(PatronIdentityRepository patronIdentityRepository, 
			HostLmsService hostLmsService, 
			PatronRequestAuditService patronRequestAuditService,
			BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider) {

		this.patronIdentityRepository = patronIdentityRepository;
		this.hostLmsService = hostLmsService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
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
				pi.setLocalBarcode(Objects.toString(hostLmsPatron.getLocalBarcodes(), null));
				pi.setLocalNames(Objects.toString(hostLmsPatron.getLocalNames(), null));
				pi.setLocalAgency(hostLmsPatron.getLocalPatronAgency());

                                log.debug("ConvertToAgency host={} agency={}",pi.getHostLms(), hostLmsPatron.getLocalPatronAgency());

				return Mono.fromDirect(patronIdentityRepository.saveOrUpdate(pi));
			});

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

		assert isApplicableFor(patronRequest);

		patronRequest.setStatus(Status.PATRON_VERIFIED);
		
		// pull out patronRequest.patron and get the home patron then use the web service to look up the patron
		// patronRequest.patron
		return Mono.from(patronIdentityRepository.findOneByPatronIdAndHomeIdentity(patronRequest.getPatron().getId(), TRUE))
			.flatMap(this::validatePatronIdentity)
			.map(patronRequest::setRequestingIdentity)
			.doOnSuccess( pr -> log.debug("Validated patron request: {}", pr))
                        .doOnError( error -> log.error( "Error occurred validating a patron request: {}", error.getMessage()))
			.flatMap(this::createAuditEntry)
		        .transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest));
	}
	
	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {

		if (patronRequest.getStatus() == Status.ERROR) return Mono.just(patronRequest);
		return patronRequestAuditService.addAuditEntry(patronRequest, Status.SUBMITTED_TO_DCB, Status.PATRON_VERIFIED)
				.thenReturn(patronRequest);
	}
	
	@Override
	public boolean isApplicableFor(PatronRequest pr) {
		return pr.getStatus() == Status.SUBMITTED_TO_DCB;
	}
}

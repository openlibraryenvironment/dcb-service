package org.olf.dcb.request.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.PatronDeletedInHostLmsException;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.PatronIdentityRepository;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class ValidatePatronTransition implements PatronRequestStateTransition {
	private final PatronIdentityRepository patronIdentityRepository;
	private final HostLmsService hostLmsService;
	private final PatronRequestAuditService patronRequestAuditService;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final AgencyRepository agencyRepository;

	// Provider to prevent circular reference exception by allowing lazy access to
	// this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	private static final String DEFAULT_AGENCY_CODE_KEY = "default-agency-code";

	private static final List<Status> possibleSourceStatus = List.of(Status.SUBMITTED_TO_DCB);
	
	public ValidatePatronTransition(PatronIdentityRepository patronIdentityRepository,
		HostLmsService hostLmsService, PatronRequestAuditService patronRequestAuditService,
		ReferenceValueMappingService referenceValueMappingService,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
		AgencyRepository agencyRepository) {

		this.patronIdentityRepository = patronIdentityRepository;
		this.hostLmsService = hostLmsService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.referenceValueMappingService = referenceValueMappingService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.agencyRepository = agencyRepository;
	}

	/**
	 * We are passed in a local patron identity record Validate and refresh any
	 * local properties we wish to sync before commencement of the requesting
	 * process.
	 */
	private Mono<PatronIdentity> validatePatronIdentity(PatronIdentity pi) {

		// We have a patron id from elsewhere, call the patrons home system to get a record which describes
		// the patron.
		log.info("ValidatePatronTransition CIRC validatePatronIdentity by calling out to host LMS - PI is {} host lms client is {}", pi, pi.getHostLms());

		return hostLmsService.getClientFor(pi.getHostLms())
			.flatMap(client -> client.getPatronByLocalId(pi.getLocalId()))
			.flatMap(hostLmsPatron -> {
				log.info("CIRC update patron identity with latest info from host {}", hostLmsPatron);

				// Update the patron identity with the current patron type and set the last
				// validated date to now()
				pi.setLocalPtype(hostLmsPatron.getLocalPatronType());
				pi.setCanonicalPtype(hostLmsPatron.getCanonicalPatronType());
				pi.setLastValidated(Instant.now());
				pi.setLocalBarcode(Objects.toString(hostLmsPatron.getLocalBarcodes(), null));
				pi.setLocalNames(Objects.toString(hostLmsPatron.getLocalNames(), null));

				log.debug("setLocalHomeLibraryCode({})", hostLmsPatron.getLocalHomeLibraryCode());
				pi.setLocalHomeLibraryCode(hostLmsPatron.getLocalHomeLibraryCode());

				if ( hostLmsPatron.getLocalBarcodes() == null )
					log.warn("Patron does not have barcodes.. Will not be able to circulate items");

				// pi.setResolvedAgency(resolveHomeLibraryCodeFromSystemToAgencyCode(pi.getHostLms().getCode(),
				// hostLmsPatron.getLocalHomeLibraryCode()));

				if (hostLmsPatron.getIsDeleted() != null && hostLmsPatron.getIsDeleted()) {
					throw new PatronDeletedInHostLmsException(
						"Patron with local id " + hostLmsPatron.getLocalId() +
							" and home library code " + hostLmsPatron.getLocalHomeLibraryCode() +
							" had a deleted flag.");
				}

				return Mono.just(pi);
			}).flatMap(updatedPatronIdentity -> {
					return Mono.fromDirect(resolveHomeLibraryCodeFromSystemToAgencyCode(pi.getHostLms().getCode(),
							pi.getLocalHomeLibraryCode(), pi));
				}).flatMap(updatedPatronIdentity -> {
					return Mono.fromDirect(patronIdentityRepository.saveOrUpdate(updatedPatronIdentity));
				});
	}

	private Mono<PatronIdentity> resolveHomeLibraryCodeFromSystemToAgencyCode(String systemCode, String homeLibraryCode,
			PatronIdentity pi) {
		// DataAgency result = null;

		log.debug("resolveHomeLibraryCodeFromSystemToAgencyCode({},{})", systemCode, homeLibraryCode);
		if ((systemCode == null))
			throw new java.lang.RuntimeException("Missing system code. Unable to accept request");

		log.debug(
			"findMapping(targetContext=DCB, targetCategory=AGENCY, sourceCategory=Location, sourceContext={}, sourceValue={}",
			systemCode, homeLibraryCode);

		return Mono.justOrEmpty(homeLibraryCode)
			.flatMap(code -> findAgencyForLocation(code, systemCode))
			.switchIfEmpty( getDefaultAgency(systemCode) )
			.flatMap(locatedAgency -> {
				log.debug("Located agency {}", locatedAgency);
				pi.setResolvedAgency(locatedAgency);
				return Mono.just(pi);
			})
			.switchIfEmpty(Mono.error(new RuntimeException(
				"Unable to resolve patron home library code(" + systemCode + "/" + homeLibraryCode + ") to an agency")));
	}

	private Mono<DataAgency> findAgencyForLocation(String code, String systemCode) {
		return referenceValueMappingService.findMapping("Location", systemCode, code, "AGENCY", "DCB")
			.doOnNext(locatedMapping -> log.debug("Located Loc-to-agency mapping {}", locatedMapping))
			.flatMap(locatedMapping -> findOneAgencyByCode( locatedMapping.getToValue() ));
	}

	private Mono<DataAgency> getDefaultAgency(String systemCode) {
		log.info("Attempting to use default agency from config.");

		return hostLmsService.getClientFor(systemCode)
			.flatMap(client -> Mono.justOrEmpty((String) client.getConfig().get(DEFAULT_AGENCY_CODE_KEY)))
			.flatMap(this::findOneAgencyByCode)
			.doOnError( error -> log.error("Error occurred getting default Agency: {}", error.getMessage()) );
	}

	private Mono<DataAgency> findOneAgencyByCode(String code) {
		return Mono.from(agencyRepository.findOneByCode(code));
	}

	/**
	 * Attempts to transition the patron request to the next state, which is placing
	 * the request at the supplying agency.
	 *
	 * @param ctx The patron request to transition.
	 * @return A Mono that emits the patron request after the transition, or an
	 * error if the transition is not possible.
	 */
	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {

		PatronRequest patronRequest = ctx.getPatronRequest();

		log.debug("verifyPatron {}", patronRequest);

		patronRequest.setStatus(Status.PATRON_VERIFIED);

				
		// This version searches through the patron identities attached to the patron request and selects the home identity
		return Flux.fromIterable(patronRequest.getPatron().getPatronIdentities())
			.filter(PatronIdentity::getHomeIdentity)
			.flatMap(this::validatePatronIdentity)
			.map( resolvedPatronIdentity -> {
				if ( ( resolvedPatronIdentity != null ) && ( resolvedPatronIdentity.getHostLms() != null ) ) {
					patronRequest.setRequestingIdentity(resolvedPatronIdentity);
					patronRequest.setPatronHostlmsCode(resolvedPatronIdentity.getHostLms().getCode());
					ctx.getWorkflowMessages().add("Resolved patron home agency to "+resolvedPatronIdentity.getHostLms().getCode());
				}
				else {
					ctx.getWorkflowMessages().add("Unable to resolve patron identity");
				}
				return patronRequest;
			})
			.then(validateLocations(patronRequest))
			.doOnSuccess( pr -> log.debug("Validated patron request: {}", pr))
			.doOnError( error -> log.error( "Error occurred validating a patron request: {}", error.getMessage()))
			.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest))
			.thenReturn(ctx);
	}

	private Mono<PatronRequest> validateLocations(PatronRequest patronRequest) {
		log.debug("validateLocations({})", patronRequest);
		return Mono.just(patronRequest);
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
		return Optional.of(Status.PATRON_VERIFIED);
	}

  @Override     
  public String getName() {
    return "ValidatePatronTransition";
  }

	@Override
	public boolean attemptAutomatically() {
		return true;
	}
}

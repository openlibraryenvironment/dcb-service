package org.olf.dcb.request.workflow;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.olf.dcb.core.interaction.LocalPatronService;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.workflow.exceptions.NoAgencyFoundException;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveAgencyProblem;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.PatronIdentityRepository;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.zalando.problem.Problem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

@Slf4j
@Prototype
public class ValidatePatronTransition implements PatronRequestStateTransition {
	private final PatronIdentityRepository patronIdentityRepository;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final AgencyRepository agencyRepository;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final LocalPatronService localPatronService;

	// Provider to prevent circular reference exception by allowing lazy access to
	// this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	private static final List<Status> possibleSourceStatus = List.of(Status.SUBMITTED_TO_DCB);

	public ValidatePatronTransition(PatronIdentityRepository patronIdentityRepository,
		ReferenceValueMappingService referenceValueMappingService,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
		AgencyRepository agencyRepository,
		LocationToAgencyMappingService locationToAgencyMappingService,
		LocalPatronService localPatronService) {

		this.patronIdentityRepository = patronIdentityRepository;
		this.referenceValueMappingService = referenceValueMappingService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.agencyRepository = agencyRepository;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.localPatronService = localPatronService;
	}

	/**
	 * We are passed in a local patron identity record Validate and refresh any
	 * local properties we wish to sync before commencement of the requesting
	 * process.
	 */
	private Mono<PatronIdentity> validatePatronIdentity(PatronIdentity pi) {
		final var hostLms = getValue(pi, PatronIdentity::getHostLms, null);

		// We have a patron id from elsewhere, call the patrons home system to get a record which describes
		// the patron.
		log.info("ValidatePatronTransition CIRC validatePatronIdentity by calling out to host LMS - PI is {} host lms client is {}",
			pi, hostLms);

		return findLocalPatron(pi)
			.flatMap(hostLmsPatron -> {
				log.info("CIRC update patron identity with latest info from host {}", hostLmsPatron);

				// Update the patron identity with the current patron type and set the last
				// validated date to now()
				pi.setLocalId(extractLocalIdFrom(hostLmsPatron));
				pi.setLocalPtype(hostLmsPatron.getLocalPatronType());
				pi.setCanonicalPtype(hostLmsPatron.getCanonicalPatronType());
				pi.setLastValidated(Instant.now());
				pi.setLocalBarcode(Objects.toString(hostLmsPatron.getLocalBarcodes(), null));
				pi.setLocalNames(Objects.toString(hostLmsPatron.getLocalNames(), null));

				log.debug("setLocalHomeLibraryCode({})", hostLmsPatron.getLocalHomeLibraryCode());
				pi.setLocalHomeLibraryCode(hostLmsPatron.getLocalHomeLibraryCode());

				if (hostLmsPatron.getLocalBarcodes() == null)
					log.warn("Patron does not have barcodes.. Will not be able to circulate items");

				return Mono.just(pi);
			}).flatMap(updatedPatronIdentity -> {
					return Mono.fromDirect(resolveHomeLibraryCodeFromSystemToAgencyCode(
						hostLms.getCode(),
							pi.getLocalHomeLibraryCode(), pi));
				}).flatMap(updatedPatronIdentity -> {
					return Mono.fromDirect(patronIdentityRepository.saveOrUpdate(updatedPatronIdentity));
				});
	}

	private static String extractLocalIdFrom(Patron hostLmsPatron) {

		if (hostLmsPatron.getLocalId() == null || hostLmsPatron.getLocalId().isEmpty()) {
			throw Problem.builder()
				.withTitle("ValidatePatronIdentity failed")
				.withDetail("HostLmsPatron didn't return a local patron id")
				.with("hostLmsPatron", hostLmsPatron)
				.build();
		}

		else if (hostLmsPatron.getLocalId().size() > 1) {

			if (hostLmsPatron.getFirstLocalId() != null && !hostLmsPatron.getFirstLocalId().isEmpty()) {
				log.debug("Using first local id from hostLmsPatron: {}", hostLmsPatron.getFirstLocalId());
				return hostLmsPatron.getFirstLocalId();
			}

			throw Problem.builder()
				.withTitle("ValidatePatronIdentity failed")
				.withDetail("HostLmsPatron returned more than one local patron ID")
				.with("hostLmsPatron", hostLmsPatron)
				.build();
		}

		return hostLmsPatron.getLocalId().get(0);
	}

	private Mono<Patron> findLocalPatron(PatronIdentity pi) {
		// when we get a localId here, beware, it may be whatever identifier DCB was passed
		// the hostLmsClient class will handle this in getPatronByIdentifier
		final var identifier = getValue(pi, PatronIdentity::getLocalId, "Unknown");
		final var hostLmsCode = getValue(pi, PatronIdentity::getHostLms, HostLms::getCode, "Unknown");

		return localPatronService.findLocalPatronAndAgency(identifier, hostLmsCode)
			.map(TupleUtils.function((patron, agency) -> patron))
			.switchIfEmpty(Mono.error(new PatronNotFoundInHostLmsException(identifier, hostLmsCode)));
	}

	private Mono<PatronIdentity> resolveHomeLibraryCodeFromSystemToAgencyCode(String systemCode, String homeLibraryCode,
			PatronIdentity pi) {

		log.debug("resolveHomeLibraryCodeFromSystemToAgencyCode({},{})", systemCode, homeLibraryCode);

		if ((systemCode == null))
			throw new java.lang.RuntimeException("Missing system code. Unable to accept request");

		log.debug(
			"findMapping(targetContext=DCB, targetCategory=AGENCY, sourceCategory=Location, sourceContext={}, sourceValue={}",
			systemCode, homeLibraryCode);

		return Mono.justOrEmpty(homeLibraryCode)

			// findAgencyForLocation only when homeLibraryCode is not null
			.flatMap(code -> findAgencyForLocation(code, systemCode))

			// If homeLibraryCode or findAgencyForLocation produced empty,
			// try to use a default agency code from config
			.switchIfEmpty(Mono.defer(() -> locationToAgencyMappingService.findDefaultAgencyCode(systemCode)))
			.switchIfEmpty(UnableToResolveAgencyProblem.raiseError(homeLibraryCode, systemCode))

			// when either findAgencyForLocation or findAgencyForDefaultAgencyCode
			// successfully found an agency code then..
			.flatMap(this::findOneAgencyByCode)

			// If an agency is found from either findAgencyForLocation or findAgencyForDefaultAgencyCode
			.flatMap(locatedAgency -> {
				log.debug("Located agency {}", locatedAgency);
				pi.setResolvedAgency(locatedAgency);
				return Mono.just(pi);
			})

			// Last fallback if the empty was not already caught
			.switchIfEmpty(Mono.defer(() -> Mono.error(new NoAgencyFoundException(
				"Unable to resolve patron home library code(" + systemCode + "/" + homeLibraryCode + ") to an agency"))));
	}

	private Mono<String> findAgencyForLocation(String code, String systemCode) {
		log.debug("findAgencyForLocation({}, {})", code, systemCode);

		return referenceValueMappingService.findMapping("Location", systemCode, code, "AGENCY", "DCB")
			.doOnNext(locatedMapping -> log.debug("Located Loc-to-agency mapping {}", locatedMapping))
			.map(ReferenceValueMapping::getToValue);
	}

	private Mono<DataAgency> findOneAgencyByCode(String code) {
		log.debug("findOneAgencyByCode({})", code);

		return Mono.from(agencyRepository.findOneByCode(code))
			.doOnSuccess(dataAgency -> log.debug("Agency found by code {}", dataAgency))
			.switchIfEmpty(Mono.defer(() -> Mono.error(new NoAgencyFoundException("No agency found with code: " + code))));
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

					// TODO: We could be setting this already when we mapToPatronRequest,
					// to be tested in full workflow testing before removing
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

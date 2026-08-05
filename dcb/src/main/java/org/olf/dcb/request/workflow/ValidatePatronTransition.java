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
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.workflow.exceptions.AgencyNotParticipatingInBorrowingException;
import org.olf.dcb.storage.PatronIdentityRepository;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.zalando.problem.Problem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import static reactor.function.TupleUtils.function;

@Slf4j
@Prototype
public class ValidatePatronTransition implements PatronRequestStateTransition {
	private final PatronIdentityRepository patronIdentityRepository;
	private final LocalPatronService localPatronService;

	// Provider to prevent circular reference exception by allowing lazy access to
	// this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	private static final List<Status> possibleSourceStatus = List.of(Status.SUBMITTED_TO_DCB);

	public ValidatePatronTransition(PatronIdentityRepository patronIdentityRepository,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
		LocalPatronService localPatronService) {

		this.patronIdentityRepository = patronIdentityRepository;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
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

		// findLocalPatronAndAgency resolves both in one pass. This used to take only the
		// patron from it and then resolve the agency a second time from the same home
		// library code - which was how the two resolutions came to drift apart in the
		// first place.
		return findLocalPatronAndAgency(pi)
			.flatMap(function((hostLmsPatron, agency) -> {
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

				log.debug("Located agency {}", agency);

				return assertParticipatesInBorrowing(agency)
					.map(pi::setResolvedAgency);
			}))
			.flatMap(updatedPatronIdentity ->
				Mono.fromDirect(patronIdentityRepository.saveOrUpdate(updatedPatronIdentity)));
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

	/**
	 * The patron as their own system currently describes them, together with the
	 * agency that home library code resolves to.
	 * <p>
	 * Deliberately the same call preflight makes. This transition used to carry its
	 * own resolution that went straight to findMapping, skipping the context
	 * hierarchy and the wildcard fallback, so a request could pass preflight against
	 * one agency and then be validated against another.
	 */
	private Mono<Tuple2<Patron, DataAgency>> findLocalPatronAndAgency(PatronIdentity pi) {
		// when we get a localId here, beware, it may be whatever identifier DCB was passed
		// the hostLmsClient class will handle this in getPatronByIdentifier
		final var identifier = getValue(pi, PatronIdentity::getLocalId, "Unknown");
		final var hostLmsCode = getValue(pi, PatronIdentity::getHostLms, HostLms::getCode, "Unknown");

		return localPatronService.findLocalPatronAndAgency(identifier, hostLmsCode)
			.switchIfEmpty(Mono.error(new PatronNotFoundInHostLmsException(identifier, hostLmsCode)));
	}

	/**
	 * Re-check what preflight already checked.
	 * <p>
	 * ResolvePatronPreflightCheck is the only other place this is asserted and it can
	 * be switched off entirely. Nothing else between there and hold placement looks
	 * again, so on a shared system a patron of a co-tenant library that is not in the
	 * consortium could have an interlending request placed on their behalf.
	 */
	private static Mono<DataAgency> assertParticipatesInBorrowing(DataAgency agency) {
		if (!Boolean.TRUE.equals(agency.getIsBorrowingAgency())) {
			return Mono.error(new AgencyNotParticipatingInBorrowingException(agency.getCode()));
		}

		return Mono.just(agency);
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

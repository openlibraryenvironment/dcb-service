package org.olf.dcb.core.patroncheck;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.UnableToConvertLocalPatronTypeException;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.request.fulfilment.CheckResult;
import org.olf.dcb.request.fulfilment.PatronEligibilityChecks;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveAgencyProblem;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.PatronIdentityRepository;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Whether one of a library's own patrons would be refused, and why, in
 * {@link PatronCheckReason}s only. The same rules placement applies, through
 * {@link PatronEligibilityChecks}.
 * <p>
 * A patron whose home library is not one of the caller's is NOT_FOUND, so a library on a
 * shared system cannot learn about its co-tenants' patrons.
 */
@Singleton
public class PatronCheckService {
	private final HostLmsService hostLmsService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final PatronEligibilityChecks patronEligibilityChecks;
	private final PatronIdentityRepository patronIdentityRepository;
	private final AgencyRepository agencyRepository;
	private final Duration timeout;

	public PatronCheckService(HostLmsService hostLmsService,
		LocationToAgencyMappingService locationToAgencyMappingService,
		PatronEligibilityChecks patronEligibilityChecks,
		PatronIdentityRepository patronIdentityRepository, AgencyRepository agencyRepository,
		@Value("${dcb.patron-checks.timeout}") Duration timeout) {

		this.hostLmsService = hostLmsService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.patronEligibilityChecks = patronEligibilityChecks;
		this.patronIdentityRepository = patronIdentityRepository;
		this.agencyRepository = agencyRepository;
		this.timeout = timeout;
	}

	/**
	 * @param agency the caller's library, whose system is asked
	 * @param callerAgencyCodes every agency the caller's token names
	 * @param identifier what the patron signs in with; never logged
	 * @return the outcome, or an error when the library system could not be asked
	 */
	public Mono<PatronCheckResult> check(DataAgency agency,
		Collection<String> callerAgencyCodes, String identifier) {

		final var hostLms = agency.getHostLms();

		return hostLmsService.getClientFor(hostLms)
			.flatMap(client -> client.getPatronByUsername(identifier))
			.timeout(timeout)
			.filter(Patron::isNotDeleted)
			.filter(patron -> patron.getLocalId() != null && !patron.getLocalId().isEmpty())
			.filterWhen(patron -> isNotVirtualPatron(patron.getLocalId().getFirst(), hostLms))
			.flatMap(patron -> judge(patron, hostLms, callerAgencyCodes))
			.onErrorResume(PatronNotFoundInHostLmsException.class, notFound -> Mono.empty())
			.onErrorResume(NoPatronTypeMappingFoundException.class, unmapped ->
				unattributable(hostLms, callerAgencyCodes, PatronCheckReason.UNKNOWN_PATRON_TYPE))
			.onErrorResume(UnableToConvertLocalPatronTypeException.class, unmapped ->
				unattributable(hostLms, callerAgencyCodes, PatronCheckReason.UNKNOWN_PATRON_TYPE))
			.switchIfEmpty(Mono.fromSupplier(() -> PatronCheckResult.refused(List.of(PatronCheckReason.NOT_FOUND))));
	}

	private Mono<PatronCheckResult> judge(Patron patron, DataHostLms hostLms,
		Collection<String> callerAgencyCodes) {

		final var hostLmsCode = hostLms.getCode();

		return locationToAgencyMappingService.resolveAgencyForPatronHomeLocation(hostLmsCode,
				getValueOrNull(patron, Patron::getLocalHomeLibraryCode))
			.flatMap(home -> callerAgencyCodes.contains(home.getCode())
				? patronEligibilityChecks.check(patron, patron.getLocalId().getFirst(), home, hostLmsCode)
					.map(PatronCheckService::toResult)
				: Mono.just(PatronCheckResult.refused(List.of(PatronCheckReason.NOT_FOUND))))
			.onErrorResume(UnableToResolveAgencyProblem.class, unresolved ->
				unattributable(hostLms, callerAgencyCodes, PatronCheckReason.NO_HOME_LIBRARY_MAPPING));
	}

	/**
	 * A refusal that could not be traced to a library. Said as it is only when every library on
	 * the system is the caller's; on a shared system it would confirm a co-tenant's patron exists.
	 */
	private Mono<PatronCheckResult> unattributable(DataHostLms hostLms,
		Collection<String> callerAgencyCodes, PatronCheckReason reason) {

		return Flux.from(agencyRepository.findByHostLmsIds(List.of(hostLms.getId())))
			.map(DataAgency::getCode)
			.all(callerAgencyCodes::contains)
			.map(allTheCallers -> PatronCheckResult.refused(
				List.of(allTheCallers ? reason : PatronCheckReason.NOT_FOUND)));
	}

	private Mono<Boolean> isNotVirtualPatron(String localId, DataHostLms hostLms) {
		return Mono.from(patronIdentityRepository
				.existsByLocalIdAndHostLmsAndHomeIdentity(localId, hostLms, false))
			.map(isVirtual -> !isVirtual);
	}

	private static PatronCheckResult toResult(List<CheckResult> results) {
		final var reasons = results.stream()
			.filter(result -> !Boolean.TRUE.equals(result.getPassed()))
			.map(result -> PatronCheckReason.fromFailureCode(result.getFailureCode()))
			.distinct()
			.toList();

		return reasons.isEmpty() ? PatronCheckResult.eligible() : PatronCheckResult.refused(reasons);
	}
}

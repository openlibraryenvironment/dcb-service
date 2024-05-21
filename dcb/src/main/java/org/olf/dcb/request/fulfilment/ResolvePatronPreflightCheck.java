package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static org.olf.dcb.request.fulfilment.CheckResult.failed;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrDefault;
import static reactor.core.publisher.Mono.defer;
import static reactor.function.TupleUtils.function;

import java.util.ArrayList;
import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.UnableToConvertLocalPatronTypeException;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveAgencyProblem;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
@Requires(property = "dcb.requests.preflight-checks.resolve-patron.enabled", defaultValue = "true", notEquals = "false")
public class ResolvePatronPreflightCheck implements PreflightCheck {
	private final HostLmsService hostLmsService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final AgencyService agencyService;

	public ResolvePatronPreflightCheck(HostLmsService hostLmsService,
		LocationToAgencyMappingService locationToAgencyMappingService,
		AgencyService agencyService) {

		this.hostLmsService = hostLmsService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.agencyService = agencyService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var hostLmsCode = command.getRequestorLocalSystemCode();
		final var localPatronId = command.getRequestorLocalId();

		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap(client -> client.getPatronByLocalId(localPatronId))
			// Could be done inside the Host LMS client method
			// Was not done initially due to potentially affecting other uses
			.filter(Patron::isNotDeleted)
			// This uses a tuple because the patron does not directly have an association with an agency
			.zipWhen(patron -> findAgencyForPatron(patron, hostLmsCode))
			.map(function((patron, agency) -> checkEligibility(localPatronId, patron, hostLmsCode)))
			.onErrorResume(PatronNotFoundInHostLmsException.class, this::patronNotFound)
			.onErrorResume(NoPatronTypeMappingFoundException.class, this::noPatronTypeMappingFound)
			.onErrorResume(UnableToConvertLocalPatronTypeException.class, this::nonNumericPatronType)
			.onErrorResume(UnableToResolveAgencyProblem.class, error -> agencyNotFound(error, localPatronId))
			.onErrorReturn(UnknownHostLmsException.class, unknownHostLms(hostLmsCode))
			.switchIfEmpty(patronDeleted(localPatronId, hostLmsCode));
	}

	private Mono<DataAgency> findAgencyForPatron(Patron patron, String hostLmsCode) {
		return findHomeLocationMapping(patron, hostLmsCode)
			.switchIfEmpty(defer(() -> locationToAgencyMappingService.findDefaultAgencyCode(hostLmsCode)))
			.flatMap(agencyService::findByCode)
			.switchIfEmpty(UnableToResolveAgencyProblem.raiseError(patron.getLocalHomeLibraryCode(), hostLmsCode));
	}

	private Mono<String> findHomeLocationMapping(Patron patron, String hostLmsCode) {
		return locationToAgencyMappingService.findLocationToAgencyMapping(
				hostLmsCode,
				patron.getLocalHomeLibraryCode())
			.map(ReferenceValueMapping::getToValue);
	}

	private List<CheckResult> checkEligibility(String localPatronId, Patron patron, String hostLmsCode) {
		// Uses the incoming local patron ID
		// rather than the list of IDs that could be returned from the Host LMS
		// in order to avoid having to choose (and potential data leakage)

		final var checkResults = new ArrayList<CheckResult>();

		final var eligible = getValueOrDefault(patron, Patron::isEligible, true);

		if (!eligible) {
			checkResults.add(failed("PATRON_INELIGIBLE",
				"Patron \"%s\" from \"%s\" is of type \"%s\" which is \"%s\" for consortial borrowing"
					.formatted(localPatronId, hostLmsCode, patron.getLocalPatronType(),
						patron.getCanonicalPatronType())));
		}

		final var blocked = getValueOrDefault(patron, Patron::getBlocked, false);

		if (blocked) {
			checkResults.add(failed("PATRON_BLOCKED",
				"Patron \"%s\" from \"%s\" has a local account block"
					.formatted(localPatronId, hostLmsCode)));
		}

		if (isEmpty(checkResults)) {
			checkResults.add(passed());
		}

		return checkResults;
	}

	private Mono<List<CheckResult>> patronNotFound(PatronNotFoundInHostLmsException error) {
		return Mono.just(List.of(
			failed("PATRON_NOT_FOUND", error.getMessage())
		));
	}

	private Mono<List<CheckResult>> patronDeleted(String localPatronId, String hostLmsCode) {
		return Mono.just(List.of(
			failed("PATRON_DELETED",
				"Patron \"%s\" from \"%s\" has been deleted".formatted(localPatronId, hostLmsCode))
		));
	}

	private Mono<List<CheckResult>> noPatronTypeMappingFound(NoPatronTypeMappingFoundException error) {
		return Mono.just(List.of(
			failed("PATRON_TYPE_NOT_MAPPED",
				"Local patron type \"%s\" from \"%s\" is not mapped to a DCB canonical patron type"
					.formatted(error.getLocalPatronType(), error.getHostLmsCode()))
		));
	}

	private Mono<List<CheckResult>> nonNumericPatronType(UnableToConvertLocalPatronTypeException error) {
		return Mono.just(List.of(
			failed("LOCAL_PATRON_TYPE_IS_NON_NUMERIC",
				"Local patron \"%s\" from \"%s\" has non-numeric patron type \"%s\""
					.formatted(error.getLocalId(), error.getLocalSystemCode(), error.getLocalPatronTypeCode()))
		));
	}

	private Mono<List<CheckResult>> agencyNotFound(
		UnableToResolveAgencyProblem error, String localPatronId) {

		return Mono.just(List.of(
			failed("PATRON_NOT_ASSOCIATED_WITH_AGENCY",
				"Patron \"%s\" with home library code \"%s\" from \"%s\" is not associated with an agency"
					.formatted(localPatronId, error.getHomeLibraryCode(),
						error.getSystemCode()))));
	}

	private static List<CheckResult> unknownHostLms(String localSystemCode) {
		return List.of(failed("UNKNOWN_BORROWING_HOST_LMS",
			"\"%s\" is not a recognised Host LMS".formatted(localSystemCode)));
	}
}

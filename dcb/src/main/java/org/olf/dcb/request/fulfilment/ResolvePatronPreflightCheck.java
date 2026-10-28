package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;

import java.util.List;

import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.core.interaction.LocalPatronService;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.UnableToConvertLocalPatronTypeException;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveAgencyProblem;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@AllArgsConstructor
@Requires(property = "dcb.requests.preflight-checks.resolve-patron.enabled", defaultValue = "true", notEquals = "false")
public class ResolvePatronPreflightCheck implements PreflightCheck {
	private final LocalPatronService localPatronService;
	private final IntMessageService intMessageService;
	private final PatronEligibilityChecks patronEligibilityChecks;

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var hostLmsCode = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalSystemCode);
		final var localPatronId = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalId);

		return localPatronService.findLocalPatronAndAgency(localPatronId, hostLmsCode)
			.flatMap(function((patron, agency) -> checkPatron(patron, localPatronId, agency, hostLmsCode)))
			.onErrorResume(PatronNotFoundInHostLmsException.class, error -> patronNotFound(hostLmsCode))
			.onErrorResume(NoPatronTypeMappingFoundException.class, this::noPatronTypeMappingFound)
			.onErrorResume(UnableToConvertLocalPatronTypeException.class, this::nonNumericPatronType)
			.onErrorResume(UnableToResolveAgencyProblem.class, this::agencyNotFound)
			.onErrorReturn(UnknownHostLmsException.class, unknownHostLms(hostLmsCode))
			.switchIfEmpty(patronDeleted(hostLmsCode));
	}

	private Mono<List<CheckResult>> checkPatron(Patron patron, String localPatronId,
		DataAgency agency, String hostLmsCode) {

		return patronEligibilityChecks.check(patron, localPatronId, agency, hostLmsCode);
	}

	// Not the exception's message: that names the patron, and this description is kept in event_log
	private Mono<List<CheckResult>> patronNotFound(String hostLmsCode) {
		return Mono.just(List.of(
			failedUm("PATRON_NOT_FOUND", "Patron is not recognised in \"%s\"".formatted(hostLmsCode),
				intMessageService.getMessage("PATRON_NOT_FOUND") )
		));
	}

	private Mono<List<CheckResult>> patronDeleted(String hostLmsCode) {
		return Mono.just(List.of(
			failedUm("PATRON_NOT_FOUND",
				"Patron from \"%s\" has likely been deleted".formatted(hostLmsCode),
				intMessageService.getMessage("PATRON_NOT_FOUND")
			)
		));
	}

	private Mono<List<CheckResult>> noPatronTypeMappingFound(NoPatronTypeMappingFoundException error) {
		return Mono.just(List.of(
			failedUm("PATRON_TYPE_NOT_MAPPED",
				"Local patron type \"%s\" from \"%s\" is not mapped to a DCB canonical patron type".formatted(error.getLocalPatronType(), error.getHostLmsCode()),
				intMessageService.getMessage("PATRON_TYPE_NOT_MAPPED")
			)
		));
	}

	private Mono<List<CheckResult>> nonNumericPatronType(UnableToConvertLocalPatronTypeException error) {
		return Mono.just(List.of(
			failedUm("LOCAL_PATRON_TYPE_IS_NON_NUMERIC",
				"Local patron from \"%s\" has non-numeric patron type \"%s\""
					.formatted(error.getLocalSystemCode(), error.getLocalPatronTypeCode()),
					intMessageService.getMessage("LOCAL_PATRON_TYPE_IS_NON_NUMERIC")
			)
		));
	}

	private Mono<List<CheckResult>> agencyNotFound(UnableToResolveAgencyProblem error) {

		return Mono.just(List.of(
			failedUm("PATRON_NOT_ASSOCIATED_WITH_AGENCY",
				"Patron with home library code \"%s\" from \"%s\" is not associated with an agency"
					.formatted(error.getHomeLibraryCode(), error.getSystemCode()),
				intMessageService.getMessage("PATRON_NOT_ASSOCIATED_WITH_AGENCY")
			)));
	}

	private List<CheckResult> unknownHostLms(String localSystemCode) {
		return List.of(failedUm("UNKNOWN_BORROWING_HOST_LMS",
			"\"%s\" is not a recognised Host LMS".formatted(localSystemCode),
			intMessageService.getMessage("UNKNOWN_BORROWING_HOST_LMS")
		));
	}

	// There may be scope here to address other things that would kill a request instantly, such as fines.
	// This is helpful to avoid requests going straight to error.
	// But we must be conscious that we don't have much time in pre-flights.

}

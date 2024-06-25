package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.CollectionUtils.concat;
import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static org.olf.dcb.request.fulfilment.CheckResult.failed;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrDefault;
import static reactor.function.TupleUtils.function;

import java.util.ArrayList;
import java.util.List;

import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.core.interaction.LocalPatronService;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.UnableToConvertLocalPatronTypeException;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveAgencyProblem;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Requires(property = "dcb.requests.preflight-checks.resolve-patron.enabled", defaultValue = "true", notEquals = "false")
public class ResolvePatronPreflightCheck implements PreflightCheck {
	private final LocalPatronService localPatronService;

	public ResolvePatronPreflightCheck(LocalPatronService localPatronService) {
		this.localPatronService = localPatronService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var hostLmsCode = getValue(command, PlacePatronRequestCommand::getRequestorLocalSystemCode);
		final var localPatronId = getValue(command, PlacePatronRequestCommand::getRequestorLocalId);

		return localPatronService.findLocalPatronAndAgency(localPatronId, hostLmsCode)
			.map(function((patron, agency) -> checkPatron(patron, localPatronId, agency, hostLmsCode)))
			.onErrorResume(PatronNotFoundInHostLmsException.class, this::patronNotFound)
			.onErrorResume(NoPatronTypeMappingFoundException.class, this::noPatronTypeMappingFound)
			.onErrorResume(UnableToConvertLocalPatronTypeException.class, this::nonNumericPatronType)
			.onErrorResume(UnableToResolveAgencyProblem.class, error -> agencyNotFound(error, localPatronId))
			.onErrorReturn(UnknownHostLmsException.class, unknownHostLms(hostLmsCode))
			.switchIfEmpty(patronDeleted(localPatronId, hostLmsCode));
	}

	private List<CheckResult> checkPatron(Patron patron, String localPatronId,
		DataAgency agency, String hostLmsCode) {

		// Uses the incoming local patron ID
		// rather than the list of IDs that could be returned from the Host LMS
		// in order to avoid having to choose (and potential data leakage)

		final var barcodeChecksResults = checkBarcode(localPatronId, patron, hostLmsCode);
		final var eligibilityCheckResults = checkEligibility(localPatronId, patron, hostLmsCode);
		final var agencyCheckResults = checkAgency(localPatronId, agency, hostLmsCode);

		final var allCheckResults = concat(
			concat(eligibilityCheckResults, agencyCheckResults), barcodeChecksResults);

		if (isEmpty(allCheckResults)) {
			allCheckResults.add(passed());
		}

		return allCheckResults;
	}

	private List<CheckResult> checkBarcode(String localPatronId, Patron patron,
		String hostLmsCode) {

		final var eligibilityCheckResults = new ArrayList<CheckResult>();

		final var firstBarcode = getValue(patron, p -> p.getFirstBarcode(""));

		if (StringUtils.isEmpty(firstBarcode)) {
			eligibilityCheckResults.add(failed("INVALID_PATRON_BARCODE",
				"Patron \"%s\" from \"%s\" has an invalid barcode: \"%s\""
					.formatted(localPatronId, hostLmsCode, firstBarcode)));
		}

		return eligibilityCheckResults;
	}

	private List<CheckResult> checkEligibility(String localPatronId, Patron patron, String hostLmsCode) {
		final var eligibilityCheckResults = new ArrayList<CheckResult>();

		final var eligible = getValueOrDefault(patron, Patron::isEligible, true);

		if (!eligible) {
			eligibilityCheckResults.add(failed("PATRON_INELIGIBLE",
				"Patron \"%s\" from \"%s\" is of type \"%s\" which is \"%s\" for consortial borrowing"
					.formatted(localPatronId, hostLmsCode,
						getValueOrDefault(patron, Patron::getLocalPatronType, "Unknown local patron type"),
						getValueOrDefault(patron, Patron::getCanonicalPatronType, "Unknown canonical patron type"))));
		}

		final var blocked = getValueOrDefault(patron, Patron::getIsBlocked, false);

		if (blocked) {
			eligibilityCheckResults.add(failed("PATRON_BLOCKED",
				"Patron \"%s\" from \"%s\" has a local account block"
					.formatted(localPatronId, hostLmsCode)));
		}

		final var active = getValueOrDefault(patron, Patron::getIsActive, true);

		if (!active) {
			eligibilityCheckResults.add(failed("PATRON_INACTIVE",
				"Patron \"%s\" from \"%s\" is inactive"
					.formatted(localPatronId, hostLmsCode)));
		}

		return eligibilityCheckResults;
	}

	private static ArrayList<CheckResult> checkAgency(String localPatronId,
		DataAgency agency, String hostLmsCode) {

		final var agencyCheckResults = new ArrayList<CheckResult>();

		final var participatingInBorrowing = getValueOrDefault(agency,
			DataAgency::getIsBorrowingAgency, false);

		if (!participatingInBorrowing) {
			agencyCheckResults.add(failed("PATRON_AGENCY_NOT_PARTICIPATING_IN_BORROWING",
				"Patron \"%s\" from \"%s\" is associated with agency \"%s\" which is not participating in borrowing"
					.formatted(localPatronId, hostLmsCode, getValue(agency, DataAgency::getCode))));
		}

		return agencyCheckResults;
	}

	private Mono<List<CheckResult>> patronNotFound(PatronNotFoundInHostLmsException error) {
		return Mono.just(List.of(
			failed("PATRON_NOT_FOUND", error.getMessage())
		));
	}

	private Mono<List<CheckResult>> patronDeleted(String localPatronId, String hostLmsCode) {
		return Mono.just(List.of(
			failed("PATRON_NOT_FOUND",
				"Patron \"%s\" from \"%s\" has likely been deleted".formatted(localPatronId, hostLmsCode))
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

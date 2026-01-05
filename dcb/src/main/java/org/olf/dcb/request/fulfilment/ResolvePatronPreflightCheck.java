package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.CollectionUtils.concat;
import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;

import org.olf.dcb.core.IntMessageService;

import java.util.ArrayList;
import java.util.List;

import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.core.interaction.LocalPatronService;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.UnableToConvertLocalPatronTypeException;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveItemAgencyProblem;

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
  private final IntMessageService intMessageService;

	public ResolvePatronPreflightCheck(LocalPatronService localPatronService,
    IntMessageService intMessageService) {
		this.localPatronService = localPatronService;
    this.intMessageService = intMessageService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var hostLmsCode = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalSystemCode);
		final var localPatronId = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalId);

		return localPatronService.findLocalPatronAndAgency(localPatronId, hostLmsCode)
			.map(function((patron, agency) -> checkPatron(patron, localPatronId, agency, hostLmsCode)))
			.onErrorResume(PatronNotFoundInHostLmsException.class, this::patronNotFound)
			.onErrorResume(NoPatronTypeMappingFoundException.class, this::noPatronTypeMappingFound)
			.onErrorResume(UnableToConvertLocalPatronTypeException.class, this::nonNumericPatronType)
			.onErrorResume(UnableToResolveItemAgencyProblem.class, error -> agencyNotFound(error, localPatronId))
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

		final var firstBarcode = getValueOrNull(patron, p -> p.getFirstBarcode(""));
		// LOGGING FOR DCB-1907
		log.debug("The patron is {}", patron);
		if (StringUtils.isEmpty(firstBarcode)) {
			eligibilityCheckResults.add(failedUm("INVALID_PATRON_BARCODE",
				"Patron \"%s\" from \"%s\" has an invalid barcode: \"%s\""
					.formatted(localPatronId, hostLmsCode, firstBarcode),
					intMessageService.getMessage("INVALID_PATRON_BARCODE")
				));
		}

		return eligibilityCheckResults;
	}

	private List<CheckResult> checkEligibility(String localPatronId, Patron patron, String hostLmsCode) {
		final var eligibilityCheckResults = new ArrayList<CheckResult>();

		final var eligible = getValue(patron, Patron::isEligible, true);

		if (!eligible) {
			eligibilityCheckResults.add(failedUm("PATRON_INELIGIBLE",
				"Patron \"%s\" from \"%s\" is of type \"%s\" which is \"%s\" for consortial borrowing"
					.formatted(localPatronId, hostLmsCode,
						getValue(patron, Patron::getLocalPatronType, "Unknown local patron type"),
						getValue(patron, Patron::getCanonicalPatronType, "Unknown canonical patron type")),
          intMessageService.getMessage("PATRON_INELIGIBLE")));
		}

		final var blocked = getValue(patron, Patron::getIsBlocked, false);

		if (blocked) {
			eligibilityCheckResults.add(failedUm("PATRON_BLOCKED",
				"Patron \"%s\" from \"%s\" has a local account block"
					.formatted(localPatronId, hostLmsCode),
					intMessageService.getMessage("PATRON_INELIGIBLE")
				));
		}

		final var active = getValue(patron, Patron::getIsActive, true);

		if (!active) {
			eligibilityCheckResults.add(failedUm("PATRON_INACTIVE",
				"Patron \"%s\" from \"%s\" is inactive".formatted(localPatronId, hostLmsCode),
				intMessageService.getMessage("PATRON_INACTIVE"))
			);
		}

		return eligibilityCheckResults;
	}

	private ArrayList<CheckResult> checkAgency(String localPatronId, DataAgency agency, String hostLmsCode) {

		final var agencyCheckResults = new ArrayList<CheckResult>();

		final var participatingInBorrowing = getValue(agency,
			DataAgency::getIsBorrowingAgency, false);

		if (!participatingInBorrowing) {
			agencyCheckResults.add(failedUm("PATRON_AGENCY_NOT_PARTICIPATING_IN_BORROWING",
				"Patron \"%s\" from \"%s\" is associated with agency \"%s\" which is not participating in borrowing"
					.formatted(localPatronId, hostLmsCode, getValueOrNull(agency, DataAgency::getCode)),
					intMessageService.getMessage("PATRON_AGENCY_NOT_PARTICIPATING_IN_BORROWING")
				));
		}

		return agencyCheckResults;
	}

	private Mono<List<CheckResult>> patronNotFound(PatronNotFoundInHostLmsException error) {
		return Mono.just(List.of(
			failedUm("PATRON_NOT_FOUND", error.getMessage(),
				intMessageService.getMessage("PATRON_NOT_FOUND") )
		));
	}

	private Mono<List<CheckResult>> patronDeleted(String localPatronId, String hostLmsCode) {
		return Mono.just(List.of(
			failedUm("PATRON_NOT_FOUND",
				"Patron \"%s\" from \"%s\" has likely been deleted".formatted(localPatronId, hostLmsCode),
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
				"Local patron \"%s\" from \"%s\" has non-numeric patron type \"%s\""
					.formatted(error.getLocalId(), error.getLocalSystemCode(), error.getLocalPatronTypeCode()),
					intMessageService.getMessage("LOCAL_PATRON_TYPE_IS_NON_NUMERIC")
			)
		));
	}

	private Mono<List<CheckResult>> agencyNotFound(
		UnableToResolveItemAgencyProblem error, String localPatronId) {

		return Mono.just(List.of(
			failedUm("PATRON_NOT_ASSOCIATED_WITH_AGENCY",
				"Patron \"%s\" with home library code \"%s\" from \"%s\" is not associated with an agency".formatted(localPatronId, error.getHomeLibraryCode(),
					error.getSystemCode()),
				intMessageService.getMessage("PATRON_NOT_ASSOCIATED_WITH_AGENCY")
			)));
	}

	private List<CheckResult> unknownHostLms(String localSystemCode) {
		return List.of(failedUm("UNKNOWN_BORROWING_HOST_LMS",
			"\"%s\" is not a recognised Host LMS".formatted(localSystemCode),
			intMessageService.getMessage("UNKNOWN_BORROWING_HOST_LMS")
		));
	}
}

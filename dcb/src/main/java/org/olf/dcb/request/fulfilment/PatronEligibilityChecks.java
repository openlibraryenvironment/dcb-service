package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.CollectionUtils.concat;
import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.interaction.LocalPatronService;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * The rules a borrowing patron is held to, once their library system has returned them and
 * their home agency is known. Placement runs them as a preflight check, and library staff run
 * them to learn why a patron would be refused, so the two cannot disagree.
 */
@Singleton
@AllArgsConstructor
public class PatronEligibilityChecks {
	private final LocalPatronService localPatronService;
	private final IntMessageService intMessageService;
	private final PatronRequestRepository patronRequestRepository;

	/** Every failed rule, or a single passed result when none failed. */
	public Mono<List<CheckResult>> check(Patron patron, String localPatronId,
		DataAgency agency, String hostLmsCode) {

		final var barcodeChecksResults = checkBarcode(patron, hostLmsCode);
		final var eligibilityCheckResults = checkEligibility(patron, hostLmsCode);
		final var agencyCheckResults = checkAgency(agency, hostLmsCode);

		return Mono.zip(checkHoldLimit(localPatronId, agency, hostLmsCode),
				checkConsortialLoanLimit(localPatronId, agency, hostLmsCode))
			.map(function((holdLimitCheckResults, loanLimitCheckResults) -> {
				final var allCheckResults = concat(concat(concat(
					concat(eligibilityCheckResults, agencyCheckResults), barcodeChecksResults),
					holdLimitCheckResults), loanLimitCheckResults);

				if (isEmpty(allCheckResults)) {
					allCheckResults.add(passed());
				}

				return allCheckResults;
			}));
	}

	/**
	 * Typically a Host LMS enforces this limit itself, but rejects the hold later in the workflow, causing requests to
	 * enter an ERROR state. The message received is typically unhelpful: Sierra's XCirc
	 * reports "There is a problem with your library record" and blames nothing in
	 * particular. Checking here turns that into an actionable message at placement.
	 */
	private Mono<List<CheckResult>> checkHoldLimit(String localPatronId,
		DataAgency agency, String hostLmsCode) {

		final var holdLimit = getValueOrNull(agency, DataAgency::getMaxLocalHolds);

		// If a library doesn't tell us their hold limit, we fail-safe: it cannot be checked.
		// Declining to judge is the only safe option.
		if (holdLimit == null) {
			return Mono.just(List.of());
		}

		return localPatronService.countHoldsForPatron(localPatronId, hostLmsCode)
			.filter(holdCount -> holdCount >= holdLimit)
			.map(holdCount -> List.of(failedUm("PATRON_HOLD_LIMIT_REACHED",
				"%d holds reaches the limit of %d for agency \"%s\" on \"%s\""
					.formatted(holdCount, holdLimit, getValueOrNull(agency, DataAgency::getCode), hostLmsCode),
				intMessageService.getMessage("PATRON_HOLD_LIMIT_REACHED"))))
			// An unknown count cannot demonstrate the patron is over their limit
			.defaultIfEmpty(List.of());
	}

	// The agency is the one resolved from the patron's home library, never the
	// requestor agency code a caller sends: callers omit it, and a caller-chosen
	// agency would choose its own limit
	private Mono<List<CheckResult>> checkConsortialLoanLimit(String localPatronId,
		DataAgency agency, String hostLmsCode) {

		final var loanLimit = getValueOrNull(agency, DataAgency::getMaxConsortialLoans);

		if (loanLimit == null) {
			return Mono.just(List.of());
		}

		final var agencyCode = getValueOrNull(agency, DataAgency::getCode);

		return Mono.from(patronRequestRepository.getActiveRequestCountForPatron(hostLmsCode, localPatronId))
			.switchIfEmpty(Mono.error(() -> new IllegalStateException("No active request count returned")))
			.map(activeCount -> activeCount < loanLimit
				? List.<CheckResult>of()
				: List.of(failedUm("EXCEEDS_AGENCY_LIMIT",
					"%d active requests reaches the limit of %d for agency \"%s\" on \"%s\""
						.formatted(activeCount, loanLimit, agencyCode, hostLmsCode),
					intMessageService.getMessage("EXCEEDS_AGENCY_LIMIT"))))
			// A limit that could not be checked is not a limit that passed
			.onErrorResume(error -> Mono.just(List.of(failedUm("REQUEST_LIMITS_UNCHECKED",
				"The request limit for agency \"%s\" could not be checked: %s".formatted(agencyCode, error.getMessage()),
				intMessageService.getMessage("REQUEST_LIMITS_UNCHECKED")))));
	}

	private List<CheckResult> checkBarcode(Patron patron, String hostLmsCode) {

		final var eligibilityCheckResults = new ArrayList<CheckResult>();

		final var firstBarcode = getValueOrNull(patron, p -> p.getFirstBarcode(""));

		if (StringUtils.isEmpty(firstBarcode)) {
			eligibilityCheckResults.add(failedUm("INVALID_PATRON_BARCODE",
				"Patron from \"%s\" has an invalid barcode".formatted(hostLmsCode),
					intMessageService.getMessage("INVALID_PATRON_BARCODE")
				));
		}

		return eligibilityCheckResults;
	}

	private List<CheckResult> checkEligibility(Patron patron, String hostLmsCode) {
		final var eligibilityCheckResults = new ArrayList<CheckResult>();

		final var eligible = getValue(patron, Patron::isEligible, true);

		if (!eligible) {
			eligibilityCheckResults.add(failedUm("PATRON_INELIGIBLE",
				"Patron from \"%s\" is of type \"%s\" which is \"%s\" for consortial borrowing"
					.formatted(hostLmsCode,
						getValue(patron, Patron::getLocalPatronType, "Unknown local patron type"),
						getValue(patron, Patron::getCanonicalPatronType, "Unknown canonical patron type")),
          intMessageService.getMessage("PATRON_INELIGIBLE")));
		}

		final var blocked = getValue(patron, Patron::getIsBlocked, false);

		if (blocked) {
			eligibilityCheckResults.add(failedUm("PATRON_BLOCKED",
				"Patron from \"%s\" has a local account block".formatted(hostLmsCode),
					intMessageService.getMessage("PATRON_INELIGIBLE")
				));
		}

		final var active = getValue(patron, Patron::getIsActive, true);

		if (!active) {
			eligibilityCheckResults.add(failedUm("PATRON_INACTIVE",
				"Patron from \"%s\" is inactive".formatted(hostLmsCode),
				intMessageService.getMessage("PATRON_INACTIVE"))
			);
		}

		// Unfortunately it is possible in some LMS for a patron to have expired, but still pass all of the above checks
		final var expiryDate = getValue(patron, Patron::getExpiryDate, null);

		if (expiryDate != null && expiryDate.before(new Date()))
		{
			eligibilityCheckResults.add(failedUm("PATRON_EXPIRED",
				"This patron from \"%s\" has expired. Please see a librarian. Expiry date: \"%s\""
					.formatted(hostLmsCode, expiryDate),
				intMessageService.getMessage("PATRON_EXPIRED")));
		}

		final var deleted = getValue(patron, Patron::getIsDeleted, false);
		// It is also technically possible in Sierra at least
		// that a patron could be deleted, but Sierra will still return its record.
		if (deleted) {
			eligibilityCheckResults.add(failedUm("PATRON_DELETED",
				"Patron from \"%s\" appears to have been deleted in the local system. Please see a librarian."
					.formatted(hostLmsCode),
				intMessageService.getMessage("PATRON_DELETED"))
			);
		}

		return eligibilityCheckResults;
	}

	private ArrayList<CheckResult> checkAgency(DataAgency agency, String hostLmsCode) {

		final var agencyCheckResults = new ArrayList<CheckResult>();

		final var participatingInBorrowing = getValue(agency,
			DataAgency::getIsBorrowingAgency, false);

		if (!participatingInBorrowing) {
			agencyCheckResults.add(failedUm("PATRON_AGENCY_NOT_PARTICIPATING_IN_BORROWING",
				"Patron from \"%s\" is associated with agency \"%s\" which is not participating in borrowing"
					.formatted(hostLmsCode, getValueOrNull(agency, DataAgency::getCode)),
					intMessageService.getMessage("PATRON_AGENCY_NOT_PARTICIPATING_IN_BORROWING")
				));
		}

		return agencyCheckResults;
	}
}

package org.olf.dcb.core.api.discovery;

import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.api.serde.PatronLoan;
import org.olf.dcb.core.api.serde.PatronLoanProjection;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.security.discovery.DiscoveryServiceProperties;
import org.olf.dcb.security.discovery.PatronAssertionVerifier;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

/**
 * What the asserted patron currently has out through DCB.
 *
 * <p>Same rule as {@link DiscoveryPatronRequestsController}: the patron comes from the
 * verified assertion and never from the request. Kept as its own controller because it
 * costs a member library one item read per loan, which the requests list must never cost —
 * see docs/discovery-service-approach.md §6.4.
 */
@Controller("/discovery/loans")
@Secured(DISCOVERY_SERVICE)
@Tag(name = "Discovery API")
@Slf4j
// Assertion verification is synchronous and may refresh trusted JWKS over bounded HTTP.
@ExecuteOn(TaskExecutors.BLOCKING)
public class DiscoveryPatronLoansController {

	private final PatronRequestRepository patronRequestRepository;
	private final PatronAssertionVerifier assertionVerifier;
	private final HostLmsService hostLmsService;
	private final DiscoveryServiceProperties properties;

	public DiscoveryPatronLoansController(PatronRequestRepository patronRequestRepository,
		PatronAssertionVerifier assertionVerifier, HostLmsService hostLmsService,
		DiscoveryServiceProperties properties) {

		this.patronRequestRepository = patronRequestRepository;
		this.assertionVerifier = assertionVerifier;
		this.hostLmsService = hostLmsService;
		this.properties = properties;
	}

	@Operation(summary = "The asserted patron's current loans",
		description = "What the patron named in the X-OpenRS-Patron-Assertion header currently "
			+ "has out through DCB. DCB does not record a due date, so each loan costs one live "
			+ "read against the borrowing library: the list is capped, the reads are throttled "
			+ "and time-bounded, and a library that cannot answer yields dueDateKnown=false "
			+ "rather than failing the request. Loans a patron holds directly from their own "
			+ "library are NOT here - DCB cannot see them.",
		parameters = @Parameter(in = ParameterIn.HEADER,
			name = PatronAssertionVerifier.ASSERTION_HEADER, required = true,
			description = "Short-lived patron assertion signed by the discovery service."))
	@Get
	public Mono<List<PatronLoan>> listOwnLoans(HttpRequest<?> request) {
		final var patron = assertionVerifier.verify(request);
		// The whole page waits at most two lookups' worth, however many loans it lists
		final var deadline = Instant.now().plus(properties.getLoanLookupTimeout().multipliedBy(2));

		// Drained before any library is asked: the repository is @Transactional, and a Flux
		// read as each lookup finishes holds its connection open for every one of them
		return Flux.from(patronRequestRepository
				.findLoansForPatron(patron.systemCode(), patron.patronId(), properties.getMaxLoans()))
			.collectList()
			.flatMap(loans -> clientsFor(loans).map(clients -> Tuples.of(loans, clients)))
			.flatMapMany(found -> Flux.fromIterable(found.getT1())
				.flatMapSequential(loan -> Mono.defer(() -> withDueDate(loan,
						found.getT2().get(loan.patronHostlmsCode()), deadline)),
					properties.getLoanLookupConcurrency()))
			.collectList();
	}

	// One client per library system on the page, not one per loan: ≤ maxLoans codes
	private Mono<Map<String, HostLmsClient>> clientsFor(List<PatronLoanProjection> loans) {
		return Flux.fromIterable(loans)
			.mapNotNull(PatronLoanProjection::patronHostlmsCode)
			.distinct()
			.flatMap(code -> hostLmsService.getClientFor(code)
					.map(client -> Map.entry(code, client))
					.onErrorResume(error -> {
						log.warn("No client for {}: {}", code, error.getClass().getSimpleName());
						return Mono.empty();
					}),
				properties.getLoanLookupConcurrency())
			.collectMap(Map.Entry::getKey, Map.Entry::getValue);
	}

	/**
	 * One loan, enriched from the borrowing library if it will answer in time.
	 *
	 * <p>Every failure here degrades the row rather than the page. A member system that is
	 * down, slow, or does not report a due date all produce the same honest answer -
	 * {@code dueDateKnown = false} - because a patron shown a blank due date cannot tell
	 * "we could not ask" from "there isn't one", and would plan around the wrong one.
	 */
	private Mono<PatronLoan> withDueDate(PatronLoanProjection loan, @Nullable HostLmsClient client,
		Instant deadline) {

		final var remaining = Duration.between(Instant.now(), deadline);

		if (client == null || loan.localItemId() == null || !remaining.isPositive()) {
			return Mono.just(unknownDueDate(loan));
		}

		final var item = HostLmsItem.builder()
			.localId(loan.localItemId())
			.localRequestId(loan.localRequestId())
			.holdingId(loan.localHoldingId())
			.bibId(loan.localBibId())
			.build();

		final var timeout = remaining.compareTo(properties.getLoanLookupTimeout()) < 0
			? remaining
			: properties.getLoanLookupTimeout();

		return Mono.from(client.getItem(item))
			.timeout(timeout)
			.map(live -> new PatronLoan(loan.id(), loan.title(), loan.bibClusterId(),
				loan.pickupLocationCode(), loan.pickupLocationName(),
				live.getDueDate(), live.getDueDate() != null,
				// The live count where the library gave one, else what tracking last saw.
				// Preferring the live read matters on the day a patron renews and reloads.
				live.getRenewalCount() != null ? live.getRenewalCount() : loan.localRenewalCount(),
				live.getRenewable(), loan.renewalStatus(), loan.dateUpdated()))
			.onErrorResume(error -> {
				log.warn("Could not read the loan for request {} from {}: {}",
					loan.id(), loan.patronHostlmsCode(), error.toString());
				return Mono.just(unknownDueDate(loan));
			})
			.defaultIfEmpty(unknownDueDate(loan));
	}

	/**
	 * An @Error handler is per-controller in Micronaut, so this is not a duplicate of
	 * {@link DiscoveryPatronRequestsController}'s — without it a rejected assertion leaves
	 * here as a 500. Watched doing exactly that before it was written.
	 */
	@Error
	public HttpResponse<Map<String, String>> onInvalidAssertion(
		PatronAssertionVerifier.InvalidPatronAssertionException exception) {

		log.warn("Rejected discovery call: {}", exception.getMessage());

		return HttpResponse.<Map<String, String>>status(HttpStatus.UNAUTHORIZED)
			.body(Map.of("code", "INVALID_PATRON_ASSERTION",
				"message", "The patron assertion was not accepted."));
	}

	private static PatronLoan unknownDueDate(PatronLoanProjection loan) {
		return new PatronLoan(loan.id(), loan.title(), loan.bibClusterId(),
			loan.pickupLocationCode(), loan.pickupLocationName(),
			null, false, loan.localRenewalCount(), null, loan.renewalStatus(),
			loan.dateUpdated());
	}
}

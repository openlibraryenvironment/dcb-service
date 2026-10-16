package org.olf.dcb.core.api.discovery;

import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;

import java.util.List;
import java.util.Map;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.api.serde.PatronAccount;
import org.olf.dcb.core.interaction.LocalPatronAccount;
import org.olf.dcb.security.discovery.DiscoveryServiceProperties;
import org.olf.dcb.security.discovery.PatronAssertionVerifier;

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
import reactor.core.publisher.Mono;

/**
 * What the asserted patron holds at their OWN library — see
 * docs/discovery-service-approach.md section 6.4.
 *
 * <p>Separate from {@link DiscoveryPatronLoansController} because the two answer different
 * questions from different places: that one reads DCB's own requests, this one asks the
 * patron's home system about loans DCB never saw.
 */
@Controller("/discovery/account")
@Secured(DISCOVERY_SERVICE)
@Tag(name = "Discovery API")
@Slf4j
// Assertion verification is synchronous and may refresh trusted JWKS over bounded HTTP.
@ExecuteOn(TaskExecutors.BLOCKING)
public class DiscoveryPatronAccountController {

	private final PatronAssertionVerifier assertionVerifier;
	private final HostLmsService hostLmsService;
	private final DiscoveryServiceProperties properties;

	public DiscoveryPatronAccountController(PatronAssertionVerifier assertionVerifier,
		HostLmsService hostLmsService, DiscoveryServiceProperties properties) {

		this.assertionVerifier = assertionVerifier;
		this.hostLmsService = hostLmsService;
		this.properties = properties;
	}

	@Operation(summary = "The asserted patron's account at their own library",
		description = "Loans and charges the patron holds DIRECTLY at their home library, "
			+ "which DCB never saw and does not store. One live read against that library "
			+ "per call, capped and time-bounded. A library with no route to a patron "
			+ "account answers available=false rather than an empty account: \"we could not "
			+ "ask\" and \"you have nothing out\" are different sentences.",
		parameters = @Parameter(in = ParameterIn.HEADER,
			name = PatronAssertionVerifier.ASSERTION_HEADER, required = true,
			description = "Short-lived patron assertion signed by the discovery service."))
	@Get
	public Mono<PatronAccount> ownAccount(HttpRequest<?> request) {
		final var patron = assertionVerifier.verify(request);

		return hostLmsService.getClientFor(patron.systemCode())
			.flatMap(client -> client.getLocalPatronAccount(
				patron.patronId(), properties.getMaxAccountRows()))
			.timeout(properties.getAccountLookupTimeout())
			.map(DiscoveryPatronAccountController::toView)
			.onErrorResume(error -> {
				// Degrade to the honest answer rather than the page. A member system that
				// is down must not cost a patron the rest of their account screen.
				log.warn("Could not read the local account at {}: {}",
					patron.systemCode(), error.getClass().getSimpleName());

				return Mono.just(PatronAccount.unavailable());
			})
			.defaultIfEmpty(PatronAccount.unavailable());
	}

	private static PatronAccount toView(LocalPatronAccount account) {
		return new PatronAccount(true,
			loans(account), charges(account),
			account.getTotalLoans(), account.getTotalCharges(),
			account.getAmountOwed(), account.getCurrency());
	}

	private static List<PatronAccount.Loan> loans(LocalPatronAccount account) {
		if (account.getLoans() == null) {
			return List.of();
		}

		return account.getLoans().stream()
			.map(loan -> new PatronAccount.Loan(loan.getTitle(), loan.getAuthor(),
				loan.getIsbn(), loan.getLocalBibId(), loan.getLoanDate(), loan.getDueDate(),
				loan.isOverdue()))
			.toList();
	}

	private static List<PatronAccount.Charge> charges(LocalPatronAccount account) {
		if (account.getCharges() == null) {
			return List.of();
		}

		return account.getCharges().stream()
			.map(charge -> new PatronAccount.Charge(charge.getTitle(), charge.getDescription(),
				charge.getReason(), charge.getState(), charge.getAmount(), charge.getCurrency(),
				charge.getAccrualDate()))
			.toList();
	}

	/**
	 * An @Error handler is per-controller in Micronaut, so this is not a duplicate of
	 * {@link DiscoveryPatronLoansController}'s — without it a rejected assertion leaves
	 * here as a 500.
	 */
	@Error
	public HttpResponse<Map<String, String>> onInvalidAssertion(
		PatronAssertionVerifier.InvalidPatronAssertionException exception) {

		log.warn("Rejected discovery call: {}", exception.getMessage());

		return HttpResponse.<Map<String, String>>status(HttpStatus.UNAUTHORIZED)
			.body(Map.of("code", "INVALID_PATRON_ASSERTION",
				"message", "The patron assertion was not accepted."));
	}
}

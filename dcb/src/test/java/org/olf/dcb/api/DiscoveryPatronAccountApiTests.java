package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpResponse.response;
import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.api.serde.PatronAccount;
import org.olf.dcb.core.interaction.folio.MockFolioFixture;
import org.olf.dcb.core.interaction.folio.PatronAccountResponse;
import org.olf.dcb.core.interaction.folio.User;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.security.discovery.PatronAssertion;
import org.olf.dcb.security.discovery.PatronAssertionVerifier;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The patron's account at their OWN library, over the discovery API.
 *
 * <p>The case that matters is the library that cannot be asked. An empty account and an
 * unanswerable one are the same JSON but for one boolean, and a client that ignores it
 * tells a patron they owe nothing.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
// The discovery routes exist only with discovery on
@Property(name = "dcb.discovery.enabled", value = "true")
class DiscoveryPatronAccountApiTests {

	private static final String API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";
	private static final String ACCESS_TOKEN = "discovery-account-token";

	private static final String WITH_EDGE_PATRON = "discovery-account-tests";
	private static final String WITHOUT_EDGE_PATRON = "discovery-account-no-edge";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockFolioFixture mockFolioFixture;

	/**
	 * Which patron, at which library, the mocked assertion names. Set by every test before
	 * it calls, so nothing here depends on the order JUnit runs them in.
	 */
	private String hostLmsCode;
	private String localPatronId;

	/**
	 * The assertion is mocked rather than signed, as in {@link DiscoveryPatronLoansApiTests}
	 * — what is under test is what DCB does once it knows which patron is asking.
	 */
	@MockBean(PatronAssertionVerifier.class)
	PatronAssertionVerifier assertionVerifier() {
		final var verifier = mock(PatronAssertionVerifier.class);

		doAnswer(invocation ->
			new PatronAssertion(hostLmsCode, localPatronId, "a-discovery-service"))
			.when(verifier).verify(any());

		return verifier;
	}

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		TestStaticTokenValidator.add(ACCESS_TOKEN, "a-discovery-service",
			List.of(DISCOVERY_SERVICE));

		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLmsWithPatronAccount(WITH_EDGE_PATRON,
			"https://fake-folio", API_KEY);

		hostLmsFixture.createFolioHostLms(WITHOUT_EDGE_PATRON, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);
	}

	@Test
	void reportsWhatThePatronsOwnLibraryHolds() {
		final var externalSystemId = aPatronAt(WITH_EDGE_PATRON);

		mockFolioFixture.mockGetPatronAccount(externalSystemId,
			PatronAccountResponse.builder()
				.totalLoans(1)
				.totalChargesCount(1)
				.totalCharges(pounds("1.20"))
				.loans(List.of(PatronAccountResponse.Loan.builder()
					.item(PatronAccountResponse.Item.builder()
						.instanceId("instance-9")
						.title("The Go-Between")
						.build())
					.dueDate("2026-10-01T23:59:00.000+00:00")
					.overdue(false)
					.build()))
				.charges(List.of(PatronAccountResponse.Charge.builder()
					.chargeAmount(pounds("1.20"))
					.reason("Overdue fine")
					.build()))
				.build());

		final var account = myAccount();

		assertThat(account.available(), is(true));
		assertThat(account.loans(), hasSize(1));
		assertThat(account.loans().getFirst().title(), is("The Go-Between"));
		assertThat(account.loans().getFirst().localBibId(), is("instance-9"));
		assertThat(account.charges(), hasSize(1));
		// JSON drops the trailing zero, and BigDecimal.equals is scale-sensitive. Money
		// compares with compareTo or a passing test is one rounding away from lying.
		assertThat(account.amountOwed(), comparesEqualTo(new BigDecimal("1.20")));
		assertThat(account.currency(), is("GBP"));
	}

	@Test
	void saysSoWhenTheLibraryHasNoWayToAnswer() {
		aPatronAt(WITHOUT_EDGE_PATRON);

		final var account = myAccount();

		// 200 with available=false, not a 404 and not an empty account. A patron whose
		// library cannot be asked must be told that, not shown a clean slate.
		assertThat(account.available(), is(false));
		assertThat(account.loans(), is(empty()));
		assertThat(account.charges(), is(empty()));
		assertThat(account.amountOwed(), is(nullValue()));
	}

	@Test
	void saysSoWhenTheLibraryFails() {
		final var externalSystemId = aPatronAt(WITH_EDGE_PATRON);

		mockFolioFixture.mockGetPatronAccount(externalSystemId,
			response().withStatusCode(500));

		final var account = myAccount();

		assertThat(account.available(), is(false));
		assertThat(account.loans(), is(empty()));
	}

	/**
	 * A fresh patron per test, so each one owns its own mocked expectations rather than
	 * matching a previous test's.
	 */
	private String aPatronAt(String code) {
		hostLmsCode = code;
		localPatronId = randomUUID().toString();

		final var externalSystemId = "ext-" + localPatronId;

		mockFolioFixture.mockGetUsersWithQuery("id", localPatronId,
			User.builder().id(localPatronId).externalSystemId(externalSystemId).build());

		return externalSystemId;
	}

	private PatronAccount myAccount() {
		return client.toBlocking().retrieve(
			HttpRequest.GET("/discovery/account").bearerAuth(ACCESS_TOKEN),
			PatronAccount.class);
	}

	private static PatronAccountResponse.Money pounds(String amount) {
		return PatronAccountResponse.Money.builder()
			.amount(new BigDecimal(amount))
			.isoCurrencyCode("GBP")
			.build();
	}
}

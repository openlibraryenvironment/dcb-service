package org.olf.dcb.core.interaction.folio;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The patron's own account at a FOLIO library, through edge-patron.
 *
 * <p>Most of these assert the SILENCE. Empty means "we cannot tell you", and a patron shown
 * an empty account for a library that could not be asked reads it as "nothing is due".
 */
@MockServerMicronautTest
class ConsortialFolioHostLmsClientPatronAccountTests {
	private static final String API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";
	private static final String WITH_EDGE_PATRON = "folio-account-tests";
	private static final String WITHOUT_EDGE_PATRON = "folio-account-no-edge";

	private static final int MAX_ROWS = 25;

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockFolioFixture mockFolioFixture;

	@BeforeEach
	public void beforeEach(MockServerClient mockServerClient) {
		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLmsWithPatronAccount(WITH_EDGE_PATRON,
			"https://fake-folio", API_KEY);

		hostLmsFixture.createFolioHostLms(WITHOUT_EDGE_PATRON, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);
	}

	@Test
	void shouldReportLoansAndChargesTheLibraryHoldsDirectly() {
		final var localPatronId = randomUUID().toString();
		final var externalSystemId = "ext-" + localPatronId;

		mockFolioFixture.mockGetUsersWithQuery("id", localPatronId,
			User.builder().id(localPatronId).externalSystemId(externalSystemId).build());

		mockFolioFixture.mockGetPatronAccount(externalSystemId, PatronAccountResponse.builder()
			.totalLoans(2)
			.totalChargesCount(1)
			.totalCharges(money("3.50"))
			.loans(List.of(PatronAccountResponse.Loan.builder()
				.item(PatronAccountResponse.Item.builder()
					.instanceId("instance-1")
					.itemId("item-1")
					.title("Brideshead Revisited")
					.author("Waugh, Evelyn")
					.isbn("9780316216739")
					.build())
				.loanDate("2026-08-01T09:00:00.000+00:00")
				.dueDate("2026-09-01T09:00:00.000+00:00")
				.overdue(true)
				.build()))
			.charges(List.of(PatronAccountResponse.Charge.builder()
				.item(PatronAccountResponse.Item.builder().title("Brideshead Revisited").build())
				.chargeAmount(money("3.50"))
				.accrualDate("2026-09-02T09:00:00.000+00:00")
				.description("Overdue fine")
				.reason("Overdue fine")
				.state("Outstanding")
				.build()))
			.build());

		final var account = singleValueFrom(client(WITH_EDGE_PATRON)
			.getLocalPatronAccount(localPatronId, MAX_ROWS));

		assertThat(account.getTotalLoans(), is(2));
		assertThat(account.getTotalCharges(), is(1));
		assertThat(account.getAmountOwed(), comparesEqualTo(new BigDecimal("3.50")));
		assertThat(account.getCurrency(), is("GBP"));

		final var loan = account.getLoans().getFirst();

		assertThat(loan.getTitle(), is("Brideshead Revisited"));
		assertThat(loan.getAuthor(), is("Waugh, Evelyn"));
		assertThat(loan.getIsbn(), is("9780316216739"));
		// The handle a discovery service resolves to a cluster, so a local loan can link
		// to a catalogue record rather than sitting as dead text.
		assertThat(loan.getLocalBibId(), is("instance-1"));
		assertThat(loan.getLocalItemId(), is("item-1"));
		assertThat(loan.isOverdue(), is(true));
		assertThat(loan.getDueDate(), is(Instant.parse("2026-09-01T09:00:00Z")));

		final var charge = account.getCharges().getFirst();

		assertThat(charge.getAmount(), comparesEqualTo(new BigDecimal("3.50")));
		assertThat(charge.getCurrency(), is("GBP"));
		assertThat(charge.getReason(), is("Overdue fine"));
		assertThat(charge.getTitle(), is("Brideshead Revisited"));
	}

	@Test
	void shouldParseAFolioTimestampThatIsNotInstantParseable() {
		// FOLIO emits an offset rather than a Z. Instant.parse refuses it, and a due date
		// silently dropped to null renders as "we could not find out".
		final var localPatronId = randomUUID().toString();
		final var externalSystemId = "ext-" + localPatronId;

		mockFolioFixture.mockGetUsersWithQuery("id", localPatronId,
			User.builder().id(localPatronId).externalSystemId(externalSystemId).build());

		mockFolioFixture.mockGetPatronAccount(externalSystemId, PatronAccountResponse.builder()
			.loans(List.of(PatronAccountResponse.Loan.builder()
				.dueDate("2026-09-01T10:30:00.000+01:00")
				.build()))
			.build());

		final var account = singleValueFrom(client(WITH_EDGE_PATRON)
			.getLocalPatronAccount(localPatronId, MAX_ROWS));

		assertThat(account.getLoans().getFirst().getDueDate(),
			is(Instant.parse("2026-09-01T09:30:00Z")));
	}

	@Test
	void shouldAskForNoMoreRowsThanTheCallerAllows() {
		// The bound on what one account page costs. Mocked ONLY for limit=25, so a client
		// that forgets the parameter gets no response and this fails.
		final var localPatronId = randomUUID().toString();
		final var externalSystemId = "ext-" + localPatronId;

		mockFolioFixture.mockGetUsersWithQuery("id", localPatronId,
			User.builder().id(localPatronId).externalSystemId(externalSystemId).build());

		mockFolioFixture.mockGetPatronAccount(externalSystemId, MAX_ROWS,
			PatronAccountResponse.builder().totalLoans(0).build());

		final var account = singleValueFrom(client(WITH_EDGE_PATRON)
			.getLocalPatronAccount(localPatronId, MAX_ROWS));

		assertThat(account.getTotalLoans(), is(0));
	}

	@Test
	void shouldSayNothingWhenTheTenantHasNoEdgePatron() {
		final var localPatronId = randomUUID().toString();
		final var externalSystemId = "ext-" + localPatronId;

		mockFolioFixture.mockGetUsersWithQuery("id", localPatronId,
			User.builder().id(localPatronId).externalSystemId(externalSystemId).build());

		final var account = singleValueFrom(client(WITHOUT_EDGE_PATRON)
			.getLocalPatronAccount(localPatronId, MAX_ROWS));

		assertThat(account, is(nullValue()));
		// Not configured means not asked: a 404 is not how a client learns a capability.
		mockFolioFixture.verifyNoPatronAccountRead(externalSystemId);
	}

	@Test
	void shouldSayNothingWhenThePatronCarriesNoExternalSystemId() {
		// edge-patron matches on externalSystemId and has no fallback to a FOLIO user id.
		// A library that does not populate it cannot be asked about its own patrons.
		final var localPatronId = randomUUID().toString();

		mockFolioFixture.mockGetUsersWithQuery("id", localPatronId,
			User.builder().id(localPatronId).barcode("67890").build());

		final var account = singleValueFrom(client(WITH_EDGE_PATRON)
			.getLocalPatronAccount(localPatronId, MAX_ROWS));

		assertThat(account, is(nullValue()));
	}

	@Test
	void shouldSayNothingWhenThePatronIsNotInFolio() {
		final var localPatronId = randomUUID().toString();

		mockFolioFixture.mockGetUsersWithQuery("id", localPatronId);

		final var account = singleValueFrom(client(WITH_EDGE_PATRON)
			.getLocalPatronAccount(localPatronId, MAX_ROWS));

		assertThat(account, is(nullValue()));
	}

	@Test
	void shouldReportAnEmptyAccountAsEmptyRatherThanAsUnknown() {
		// The other half of the rule: a patron who genuinely has nothing out gets an
		// account, not a silence, so a client can say "nothing due" with confidence.
		final var localPatronId = randomUUID().toString();
		final var externalSystemId = "ext-" + localPatronId;

		mockFolioFixture.mockGetUsersWithQuery("id", localPatronId,
			User.builder().id(localPatronId).externalSystemId(externalSystemId).build());

		mockFolioFixture.mockGetPatronAccount(externalSystemId,
			PatronAccountResponse.builder().totalLoans(0).totalChargesCount(0).build());

		final var account = singleValueFrom(client(WITH_EDGE_PATRON)
			.getLocalPatronAccount(localPatronId, MAX_ROWS));

		assertTrue(account.getLoans().isEmpty());
		assertTrue(account.getCharges().isEmpty());
		assertThat(account.getTotalLoans(), is(0));
	}

	private HostLmsClient client(String hostLmsCode) {
		return hostLmsFixture.createClient(hostLmsCode);
	}

	private static PatronAccountResponse.Money money(String amount) {
		return PatronAccountResponse.Money.builder()
			.amount(new BigDecimal(amount))
			.isoCurrencyCode("GBP")
			.build();
	}
}

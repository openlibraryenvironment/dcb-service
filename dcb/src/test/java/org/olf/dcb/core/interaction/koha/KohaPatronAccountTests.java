package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.LocalPatronAccount;
import org.olf.dcb.core.interaction.koha.dto.KohaAccountLine;
import org.olf.dcb.core.interaction.koha.dto.KohaBiblio;
import org.olf.dcb.core.interaction.koha.dto.KohaCheckout;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronBalance;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The loans and charges a Koha patron sees on the discovery account page. The balance
 * needs a permission the library may withhold, in which case the loans still come and
 * the charges are unknown, not zero.
 */
@MockServerMicronautTest
class KohaPatronAccountTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-host-lms";
	private static final String KOHA_HOST = "fake-koha";
	private static final String PATRON_ID = "12";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture mockKohaFixture;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		mockKohaFixture = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	@Test
	void shouldListTheLoansWithTheirTitlesAndDueDates() {
		mockKohaFixture.mockPatronCheckouts(PATRON_ID,
			checkout(7L, 55L, "A title", "2099-01-31T23:59:00Z"),
			checkout(8L, 56L, "Overdue title", "2026-01-31T23:59:00Z"));
		mockKohaFixture.mockPatronBalance(PATRON_ID, balance(BigDecimal.ZERO, List.of()));

		final var account = getAccount();

		assertThat(account.getTotalLoans(), is(2));
		final var loan = account.getLoans().get(0);
		assertThat(loan.getTitle(), is("A title"));
		assertThat(loan.getAuthor(), is("An author"));
		assertThat(loan.getLocalBibId(), is("4001"));
		assertThat(loan.getLocalItemId(), is("55"));
		assertThat(loan.getDueDate(), is(Instant.parse("2099-01-31T23:59:00Z")));
		assertThat(loan.isOverdue(), is(false));
		assertThat(account.getLoans().get(1).isOverdue(), is(true));
	}

	@Test
	void shouldListTheOutstandingChargesAndTheBalance() {
		mockKohaFixture.mockPatronCheckouts(PATRON_ID);
		mockKohaFixture.mockPatronBalance(PATRON_ID, balance(new BigDecimal("3.50"), List.of(
			KohaAccountLine.builder()
				.amount(new BigDecimal("5.00"))
				.amountOutstanding(new BigDecimal("3.50"))
				.debitType("OVERDUE")
				.description("Overdue fine")
				.status("UNRETURNED")
				.date("2026-09-01T10:00:00Z")
				.build())));

		final var account = getAccount();

		assertThat(account.getAmountOwed(), comparesEqualTo(new BigDecimal("3.50")));
		assertThat(account.getTotalCharges(), is(1));
		final var charge = account.getCharges().get(0);
		assertThat(charge.getDescription(), is("Overdue fine"));
		assertThat(charge.getReason(), is("OVERDUE"));
		assertThat(charge.getAmount(), comparesEqualTo(new BigDecimal("3.50")));
		assertThat(charge.getAccrualDate(), is(Instant.parse("2026-09-01T10:00:00Z")));
	}

	@Test
	void shouldStillAnswerTheLoansWhenTheBalanceIsForbidden() {
		mockKohaFixture.mockPatronCheckouts(PATRON_ID, checkout(7L, 55L, "A title", "2099-01-31T23:59:00Z"));
		mockKohaFixture.mockPatronBalanceForbidden(PATRON_ID);

		final var account = getAccount();

		assertThat(account.getLoans(), hasSize(1));
		assertThat("Unknown, not zero", account.getCharges(), is(nullValue()));
		assertThat(account.getAmountOwed(), is(nullValue()));
	}

	private LocalPatronAccount getAccount() {
		return client.getLocalPatronAccount(PATRON_ID, 20).block();
	}

	private static KohaCheckout checkout(long checkoutId, long itemId, String title, String dueDate) {
		return KohaCheckout.builder()
			.checkoutId(checkoutId)
			.patronId(Long.valueOf(PATRON_ID))
			.itemId(itemId)
			.checkoutDate("2026-10-01T10:00:00Z")
			.dueDate(dueDate)
			.item(KohaItem.builder()
				.itemId(itemId)
				.biblioId(4001L)
				.biblio(KohaBiblio.builder()
					.biblioId(4001)
					.title(title)
					.author("An author")
					.build())
				.build())
			.build();
	}

	private static KohaPatronBalance balance(BigDecimal balance, List<KohaAccountLine> debits) {
		return KohaPatronBalance.builder()
			.balance(balance)
			.outstandingDebits(KohaPatronBalance.Outstanding.builder()
				.total(balance)
				.lines(debits)
				.build())
			.build();
	}
}

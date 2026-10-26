package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.koha.dto.KohaCheckout;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Koha lends without a question to the patron a copy is held for. It asks staff to confirm
 * lending a copy held for someone else, and asks nothing DCB answers yes to: a refusal is
 * named, and a copy already on loan to the patron is what was asked. The availability
 * answers below are the shapes Koha's staff API returns (C4::Circulation, Koha::REST::V1::Checkouts).
 */
@MockServerMicronautTest
class KohaCheckoutTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-checkout";
	private static final String KOHA_HOST = "fake-koha-checkout";
	private static final String PATRON_ID = "90";
	private static final String ITEM_ID = "71";
	private static final String TOKEN = "confirmation-jwt";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture koha;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	@Test
	void shouldCheckOutWhenKohaAsksNoQuestion() {
		koha.mockCheckout(checkout());

		assertThat(client.checkOutItemToPatron(command().build()).block(), is("OK"));

		koha.verifyCheckedOut("{\"patron_id\": 90, \"item_id\": 71}");
	}

	@Test
	void shouldRefuseToLendACopyHeldForAnotherPatronAndKeepThatPatronOutOfTheProblem() {
		koha.mockCheckoutNeedsConfirmation();
		koha.mockCheckoutAvailability(PATRON_ID, ITEM_ID,
			"{\"blockers\": {}, \"confirms\": {\"RESERVED\": 1, \"resfirstname\": \"Ann\", \"ressurname\": \"Other\","
				+ " \"rescardnumber\": \"23456789\", \"resborrowernumber\": 13, \"resbranchcode\": \"BRANCH-S\","
				+ " \"resreservedate\": \"2026-10-20\", \"reserve_id\": 602}, \"warnings\": {}, \"confirmation_token\": \"" + TOKEN + "\"}");

		final var problem = assertThrows(ThrowableProblem.class,
			() -> client.checkOutItemToPatron(command().build()).block());

		assertThat(problem.getTitle(), is("Koha wants a confirmation DCB does not give"));
		assertThat(problem.getParameters().get("confirmations"), is(java.util.List.of("RESERVED")));
		assertThat(problem.getDetail(), not(containsString("Ann")));
		assertThat(problem.getDetail(), not(containsString("23456789")));
		koha.verifyCheckoutNeverConfirmed();
	}

	@Test
	void aCopyAlreadyOnLoanToThePatronIsLent() {
		koha.mockCheckoutNeedsConfirmation();
		koha.mockCheckoutAvailability(PATRON_ID, ITEM_ID,
			"{\"blockers\": {}, \"confirms\": {\"RENEW_ISSUE\": 1}, \"warnings\": {}, \"confirmation_token\": \"" + TOKEN + "\"}");

		assertThat(client.checkOutItemToPatron(command().build()).block(), is("OK"));

		koha.verifyCheckoutNeverConfirmed();
	}

	@Test
	void aCopyOnLoanToThePatronWithNoRenewalsLeftIsLent() {
		koha.mockCheckoutNeedsConfirmation();
		koha.mockCheckoutAvailability(PATRON_ID, ITEM_ID,
			"{\"blockers\": {\"NO_MORE_RENEWALS\": 1}, \"confirms\": {}, \"warnings\": {}}");

		assertThat(client.checkOutItemToPatron(command().build()).block(), is("OK"));
	}

	@Test
	void shouldRefuseWhenKohaBlocksTheCheckout() {
		koha.mockCheckoutNeedsConfirmation();
		koha.mockCheckoutAvailability(PATRON_ID, ITEM_ID,
			"{\"blockers\": {\"ITEM_LOST\": 1}, \"confirms\": {\"TRANSFERRED\": 1}, \"warnings\": {}, \"confirmation_token\": \"" + TOKEN + "\"}");

		final var problem = assertThrows(ThrowableProblem.class,
			() -> client.checkOutItemToPatron(command().build()).block());

		assertThat(problem.getTitle(), is("Koha refused the checkout"));
		assertThat(problem.getParameters().get("blocked"), is(java.util.List.of("ITEM_LOST")));
		koha.verifyCheckoutNeverConfirmed();
	}

	@Test
	void shouldNotConfirmWhatAConsortiumLoanDoesNotAnswerFor() {
		// A debt is the library's decision, not DCB's to wave through
		koha.mockCheckoutNeedsConfirmation();
		koha.mockCheckoutAvailability(PATRON_ID, ITEM_ID,
			"{\"blockers\": {}, \"confirms\": {\"TRANSFERRED\": 1, \"DEBT\": 12.5}, \"warnings\": {}, \"confirmation_token\": \"" + TOKEN + "\"}");

		final var problem = assertThrows(ThrowableProblem.class,
			() -> client.checkOutItemToPatron(command().build()).block());

		assertThat(problem.getTitle(), is("Koha wants a confirmation DCB does not give"));
		assertThat(problem.getParameters().get("confirmations"), is(java.util.List.of("DEBT", "TRANSFERRED")));
		koha.verifyCheckoutNeverConfirmed();
	}

	@Test
	void shouldSendTheBarcodeWhenTheItemIdIsUnknown() {
		koha.mockCheckout(checkout());

		client.checkOutItemToPatron(CheckoutItemCommand.builder()
			.patronId(PATRON_ID)
			.itemBarcode("KOHA-0071")
			.build()).block();

		koha.verifyCheckedOut("{\"patron_id\": 90, \"external_id\": \"KOHA-0071\"}");
	}

	private static CheckoutItemCommand.CheckoutItemCommandBuilder command() {
		return CheckoutItemCommand.builder()
			.patronId(PATRON_ID)
			.itemId(ITEM_ID)
			.itemBarcode("KOHA-0071")
			.localRequestId("601");
	}

	private static KohaCheckout checkout() {
		return KohaCheckout.builder()
			.checkoutId(7L)
			.patronId(Long.valueOf(PATRON_ID))
			.itemId(Long.valueOf(ITEM_ID))
			.dueDate("2026-11-19T23:59:00Z")
			.build();
	}
}

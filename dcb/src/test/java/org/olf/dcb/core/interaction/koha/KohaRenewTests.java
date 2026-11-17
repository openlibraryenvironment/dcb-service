package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsRenewal;
import org.olf.dcb.core.interaction.koha.dto.KohaCheckout;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * A borrower's renewal reflected on the supplying Koha. The workflow identifies the
 * supplier request by its hold id; Koha renews a checkout, which only the item knows.
 */
@MockServerMicronautTest
class KohaRenewTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-renew";
	private static final String KOHA_HOST = "fake-koha-renew";
	private static final String HOLD_ID = "601";
	private static final String ITEM_ID = "71";
	private static final String CHECKOUT_ID = "7";

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
	void shouldRenewTheItemsCurrentLoanAndNotTheHold() {
		koha.mockCheckoutsForItem(ITEM_ID, KohaCheckout.builder()
			.checkoutId(Long.valueOf(CHECKOUT_ID))
			.itemId(Long.valueOf(ITEM_ID))
			.build());
		koha.mockRenewal(CHECKOUT_ID, KohaCheckout.builder()
			.checkoutId(Long.valueOf(CHECKOUT_ID))
			.dueDate("2026-12-10T23:59:00Z")
			.renewalsCount(1)
			.build());

		final var renewed = client.renew(renewal()).block();

		assertThat(renewed.getLocalRequestId(), is(HOLD_ID));
		koha.verifyRenewed(CHECKOUT_ID);
		koha.verifyNeverRenewed(HOLD_ID);
	}

	@Test
	void shouldFailWhenTheItemIsNotOnLoan() {
		koha.mockCheckoutsForItem(ITEM_ID);

		final var problem = assertThrows(ThrowableProblem.class, () -> client.renew(renewal()).block());

		assertThat(problem.getTitle(), is("Koha has no loan to renew"));
	}

	@Test
	void shouldReportKohaRefusingTheRenewal() {
		koha.mockCheckoutsForItem(ITEM_ID, KohaCheckout.builder()
			.checkoutId(Long.valueOf(CHECKOUT_ID))
			.itemId(Long.valueOf(ITEM_ID))
			.build());
		koha.mockRenewalRefused(CHECKOUT_ID);

		final var problem = assertThrows(ThrowableProblem.class, () -> client.renew(renewal()).block());

		assertThat(problem.getTitle(), is("Koha API Error"));
		assertThat(problem.getParameters().get("Koha Error Message"), is("Renewal not allowed"));
	}

	private static HostLmsRenewal renewal() {
		return HostLmsRenewal.builder()
			.localRequestId(HOLD_ID)
			.localItemId(ITEM_ID)
			.localItemBarcode("KOHA-0071")
			.localPatronId("90")
			.build();
	}
}

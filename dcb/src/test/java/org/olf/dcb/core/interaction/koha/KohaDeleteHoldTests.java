package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.interaction.DeleteCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The cleanup deletes the supplier hold and a cancellation cancels it. On a Koha that has
 * lent the item the hold was filled long before, and Koha has nothing to delete: that is
 * the outcome either wanted, not a failure.
 */
@MockServerMicronautTest
class KohaDeleteHoldTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-delete-hold";
	private static final String KOHA_HOST = "fake-koha-delete-hold";
	private static final String HOLD_ID = "601";

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
	void shouldDeleteALiveHold() {
		koha.mockDeleteHold(HOLD_ID);

		assertThat(client.deleteHold(DeleteCommand.builder().requestId(HOLD_ID).build()).block(), is("OK"));

		koha.verifyHoldDeleted(HOLD_ID);
	}

	@Test
	void shouldCountAHoldKohaHasAlreadyFilledAsDeleted() {
		koha.mockDeleteHoldGone(HOLD_ID);

		assertThat(client.deleteHold(DeleteCommand.builder().requestId(HOLD_ID).build()).block(), is("OK"));
	}

	@Test
	void shouldCountCancellingAHoldKohaHasAlreadyFilledAsCancelled() {
		// A borrower cancelling after the lender filled the hold, or a re-resolution after
		// staff cancelled it
		koha.mockDeleteHoldGone(HOLD_ID);

		assertThat(client.cancelHoldRequest(CancelHoldRequestParameters.builder()
			.localRequestId(HOLD_ID)
			.build()).block(), is("OK"));
	}
}

package org.olf.dcb.core.interaction.folio;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpResponse.response;
import static org.olf.dcb.core.interaction.folio.ConsortialFolioClientConstants.RESULT_OK;
import static org.olf.dcb.core.interaction.folio.ConsortialFolioClientConstants.RESULT_OK_CANCELLED;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.DeleteCommand;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientDeleteHoldTests {
	private static final String HOST_LMS_CODE = "folio-delete-hold-tests";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockFolioFixture mockFolioFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);
	}

	@Test
	void shouldCancelTransactionWhenItCannotBeClosedBecauseItemIsStillOut() {
		// A request cancelled while the item is out leaves the mod-dcb transaction OPEN. mod-dcb refuses to
		// CLOSE it (the item is not back yet), so deleteHold must fall back to CANCELLING it - otherwise the
		// supplier re-captures the returning item on check-in and ships it out to the borrower again.
		final var transactionId = randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(transactionId, "OPEN");
		// deleteHold first attempts CLOSE (mod-dcb rejects it while the item is out), then falls back to
		// CANCEL. Match each PUT by its target status: CLOSE is rejected, CANCEL is accepted. Getting
		// RESULT_OK_CANCELLED back proves the CANCEL PUT was made and accepted after CLOSE failed.
		mockFolioFixture.mockUpdateTransactionStatus(transactionId, "CLOSED", response().withStatusCode(422));
		mockFolioFixture.mockUpdateTransactionStatus(transactionId, "CANCELLED",
			okJson(TransactionStatus.builder().status("CANCELLED").build()));

		final var result = singleValueFrom(hostLmsFixture.createClient(HOST_LMS_CODE)
			.deleteHold(DeleteCommand.builder()
				.requestId(transactionId)
				.patronId(randomUUID().toString())
				.build()));

		assertThat(result, is(RESULT_OK_CANCELLED));
	}

	@Test
	void shouldReportOkWithoutMutatingWhenTransactionAlreadyClosed() {
		final var transactionId = randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(transactionId, "CLOSED");

		final var result = singleValueFrom(hostLmsFixture.createClient(HOST_LMS_CODE)
			.deleteHold(DeleteCommand.builder()
				.requestId(transactionId)
				.patronId(randomUUID().toString())
				.build()));

		assertThat(result, is(RESULT_OK));
		mockFolioFixture.verifyNoUpdateTransaction(transactionId);
	}

	@ParameterizedTest
	@ValueSource(strings = {"CANCELLED", "ERROR"})
	void shouldReportOkWithoutMutatingWhenTransactionAlreadyTerminal(String terminalStatus) {
		// deleteHold re-runs during finalisation cleanup after we already cancelled the hold at park time.
		// An already-terminal transaction is a no-op - it must not try (and fail) to CLOSE or CANCEL again.
		final var transactionId = randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(transactionId, terminalStatus);

		final var result = singleValueFrom(hostLmsFixture.createClient(HOST_LMS_CODE)
			.deleteHold(DeleteCommand.builder()
				.requestId(transactionId)
				.patronId(randomUUID().toString())
				.build()));

		assertThat(result, is(RESULT_OK));
		mockFolioFixture.verifyNoUpdateTransaction(transactionId);
	}
}

package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasNoRequestedItemBarcode;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasNoRequestedItemId;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient.ValidationError;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientGetRequestTests {
	private static final String HOST_LMS_CODE = "folio-get-request-tests";

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
	void shouldDetectRequestHasBeenConfirmed() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, "CREATED");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		final var localRequest = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus("CONFIRMED"),
			hasNoRequestedItemId(),
			hasNoRequestedItemBarcode()
		));
	}

	@Test
	void shouldDetectRequestHasBeenDispatched() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, "OPEN");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		final var localRequest = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus("OPEN"),
			hasNoRequestedItemId(),
			hasNoRequestedItemBarcode()
		));
	}

	@Test
	void shouldDetectRequestHasBeenCancelled() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, "CANCELLED");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		final var localRequest = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus("CANCELLED"),
			hasNoRequestedItemId(),
			hasNoRequestedItemBarcode()
		));
	}

	@Test
	void shouldDetectRequestIsMissing() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId,
			response()
				.withStatusCode(404)
				.withBody(json(ValidationError.builder()
					.errors(List.of(
						ValidationError.Error.builder()
							.message("DCB Transaction was not found by id= " + localRequestId)
							.type("-1")
							.code("NOT_FOUND_ERROR")
							.build()))
					.build())));

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		final var localRequest = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus("MISSING"),
			hasNoRequestedItemId(),
			hasNoRequestedItemBarcode()
		));
	}

	@Test
	void shouldNotRequestTransactionStatusForNullId() {
		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var exception = assertThrows(NullPointerException.class,
			() -> singleValueFrom(client.getRequest(null)));

		// Assert
		assertThat(exception, hasMessage("Cannot use transaction id: null to fetch transaction status."));
	}

	@ParameterizedTest
	@ValueSource(strings = {"AWAITING_PICKUP", "ITEM_CHECKED_OUT", "ITEM_CHECKED_IN", "CLOSED", "ERROR"})
	void shouldReturnUnmappedTransactionStatusForAnyOtherStatus(String transactionStatus) {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, transactionStatus);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		final var localRequest = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus(transactionStatus),
			hasNoRequestedItemId(),
			hasNoRequestedItemBarcode()
		));
	}

	@Test
	void shouldRaiseErrorForUnexpectedStatus() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, "UNEXPECTED_STATUS");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		final var exception = assertThrows(RuntimeException.class,
			() -> singleValueFrom(client.getRequest(hostLmsRequest)));

		// Assert
		assertThat(exception, hasMessage(
			"Unrecognised transaction status: \"%s\" for transaction ID: \"%s\""
				.formatted("UNEXPECTED_STATUS", localRequestId)));
	}
}

package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
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
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasMessageForHostLms;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestMethod;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestUrl;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasTextResponseBody;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem;
import org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient.ValidationError;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
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
		final var localRequest = getRequest(localRequestId);

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
		final var localRequest = getRequest(localRequestId);

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
		final var localRequest = getRequest(localRequestId);

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
		final var localRequest = getRequest(localRequestId);

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
		final var exception = assertThrows(NullPointerException.class,
			() -> getRequest(null));

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
		final var localRequest = getRequest(localRequestId);

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
		final var exception = assertThrows(RuntimeException.class,
			() -> getRequest(localRequestId));

		// Assert
		assertThat(exception, hasMessage(
			"Unrecognised transaction status: \"%s\" for transaction ID: \"%s\""
				.formatted("UNEXPECTED_STATUS", localRequestId)));
	}

	@Test
	void shouldThrowUnexpectedHttpResponseProblemWhenTransactionStatusReturns500() {
		// Arrange
		final var transactionId = UUID.randomUUID().toString();

		final var expectedResponseBody = """
			HTTP 500 Internal Server Error.
			If the issue persists, please report it to EBSCO Connect.
			""";

		mockFolioFixture.mockGetTransactionStatus(transactionId,
			response()
				.withStatusCode(500)
				.withBody(expectedResponseBody)
		);

		// Act
		final var thrown = assertThrows(UnexpectedHttpResponseProblem.class,
			() -> getRequest(transactionId));

		// Assert
		final var expectedUrl = "https://fake-folio/dcbService/transactions/" + transactionId + "/status";

		assertThat("Should indicate GET method was used in the request",
			thrown, hasRequestMethod("GET"));

		assertThat("Should contain the exact URL used for the failed request",
			thrown, hasRequestUrl(expectedUrl));

		assertThat("Should indicate that the response status was 500 (Internal Server Error)",
			thrown, hasResponseStatusCode(500));

		assertThat("Should associate the error with the correct host LMS code",
			thrown, hasMessageForHostLms(HOST_LMS_CODE));

		assertThat("Should include the raw text response body for diagnostic purposes",
			thrown, hasTextResponseBody(expectedResponseBody));
	}

	private HostLmsRequest getRequest(String localRequestId) {
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		return singleValueFrom(client.getRequest(hostLmsRequest));
	}
}

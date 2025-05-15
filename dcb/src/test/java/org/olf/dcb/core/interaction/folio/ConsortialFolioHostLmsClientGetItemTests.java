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
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient.ValidationError;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientGetItemTests {
	private static final String HOST_LMS_CODE = "folio-get-item-tests";

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
	void shouldDetectItemHasBeenReceivedAtThePickupLocation() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();
		final var localItemId = UUID.randomUUID().toString();

		// When the item is checked in at the pickup agency
		mockFolioFixture.mockGetTransactionStatus(localRequestId, "AWAITING_PICKUP");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).localRequestId(localRequestId).build();

		final var localItem = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("HOLDSHELF")
		));
	}

	@Test
	void shouldDetectItemHasBeenBorrowedByPatron() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();
		final var localItemId = UUID.randomUUID().toString();

		// When the item is checked in at the pickup agency
		mockFolioFixture.mockGetTransactionStatus(localRequestId, "ITEM_CHECKED_OUT");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).localRequestId(localRequestId).build();

		final var localItem = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("LOANED"),
			hasRenewalCount(0)
		));
	}

	@Test
	void shouldDetectItemHasBeenReturnedByPatron() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();
		final var localItemId = UUID.randomUUID().toString();

		// When the item is checked in at the pickup agency
		mockFolioFixture.mockGetTransactionStatus(localRequestId, "ITEM_CHECKED_IN");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).localRequestId(localRequestId).build();

		final var localItem = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("TRANSIT")
		));
	}

	@Test
	void shouldDetectItemHasBeenReturnedToSupplier() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();
		final var localItemId = UUID.randomUUID().toString();

		// When the item is checked in at the supplying agency, the DCB transaction is closed
		mockFolioFixture.mockGetTransactionStatus(localRequestId, "CLOSED");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).localRequestId(localRequestId).build();

		final var localItem = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("AVAILABLE")
		));
	}

	@Test
	void shouldDetectRequestIsMissing() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();
		final var localItemId = UUID.randomUUID().toString();

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

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).localRequestId(localRequestId).build();

		final var localItem = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("MISSING")
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
	@ValueSource(strings = {"CREATED", "CANCELLED", "ERROR"})
	void shouldReturnUnmappedTransactionStatusForAnyOtherStatus(String transactionStatus) {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();
		final var localItemId = UUID.randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, transactionStatus);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).localRequestId(localRequestId).build();

		final var localRequest = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus(transactionStatus)
		));
	}

	@Test
	void shouldRaiseErrorForUnexpectedStatus() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();
		final var localItemId = UUID.randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, "UNEXPECTED_STATUS");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).localRequestId(localRequestId).build();

		final var exception = assertThrows(RuntimeException.class,
			() -> singleValueFrom(client.getItem(hostLmsItem)));

		// Assert
		assertThat(exception, hasMessage(
			"Unrecognised transaction status: \"%s\" for transaction ID: \"%s\""
				.formatted("UNEXPECTED_STATUS", localRequestId)));
	}
}

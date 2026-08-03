package org.olf.dcb.core.interaction.folio;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasRawStatus;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasRenewalCount;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasStatus;

import java.util.List;

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
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		// When the item is checked in at the pickup library
		mockFolioFixture.mockGetTransactionStatus(localRequestId, "AWAITING_PICKUP");

		// Act
		final var localItem = getItem(localItemId, localRequestId);

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("HOLDSHELF"),
			hasRawStatus("AWAITING_PICKUP"),
			hasRenewalCount(0)
		));
	}

	@Test
	void shouldDetectItemHasBeenBorrowedByPatron() {
		// Arrange
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		// When the item is checked out by the patron
		mockFolioFixture.mockGetTransactionStatus(localRequestId, "ITEM_CHECKED_OUT");

		// Act
		final var localItem = getItem(localItemId, localRequestId);

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("LOANED"),
			hasRawStatus("ITEM_CHECKED_OUT"),
			hasRenewalCount(0)
		));
	}

	@Test
	void shouldDetectItemHasBeenRenewedByPatron() {
		// Arrange
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, response()
			.withStatusCode(200)
			.withBody(json(TransactionStatus.builder()
				.status("ITEM_CHECKED_OUT")
				.item(TransactionStatus.Item.builder()
					.renewalInfo(TransactionStatus.RenewalInformation.builder()
						.renewalCount(2)
						.build())
					.build())
				.build())));

		// Act
		final var localItem = getItem(localItemId, localRequestId);

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("LOANED"),
			hasRawStatus("ITEM_CHECKED_OUT"),
			hasRenewalCount(2)
		));
	}

	@Test
	void shouldDetectItemHasBeenReturnedByPatron() {
		// Arrange
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		// When the item is checked in at the pickup agency
		mockFolioFixture.mockGetTransactionStatus(localRequestId, "ITEM_CHECKED_IN");

		// Act
		final var localItem = getItem(localItemId, localRequestId);

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("TRANSIT"),
			hasRawStatus("ITEM_CHECKED_IN"),
			hasRenewalCount(0)
		));
	}

	@Test
	void shouldDetectItemHasBeenReturnedToSupplier() {
		// Arrange
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		// When the item is checked in at the supplying agency, the DCB transaction is closed
		mockFolioFixture.mockGetTransactionStatus(localRequestId, "CLOSED");

		// Act
		final var localItem = getItem(localItemId, localRequestId);

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("AVAILABLE"),
			hasRawStatus("CLOSED"),
			hasRenewalCount(0)
		));
	}

	@Test
	void shouldDetectRequestIsMissing() {
		// Arrange
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

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
		final var localItem = getItem(localItemId, localRequestId);

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
		final var exception = assertThrows(NullPointerException.class,
			() -> getItem(null, null));

		// Assert
		assertThat(exception, hasMessage("Cannot use transaction id: null to fetch transaction status."));
	}

	@ParameterizedTest
	@ValueSource(strings = {"CREATED", "CANCELLED", "ERROR"})
	void shouldReturnUnmappedTransactionStatusForAnyOtherStatus(String transactionStatus) {
		// Arrange
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, transactionStatus);

		// Act
		final var localRequest = getItem(localItemId, localRequestId);

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus(transactionStatus),
			hasRawStatus(transactionStatus)
		));
	}

	@Test
	void shouldReturnUnmappedTransactionStatusForUnexpectedStatus() {
		// Arrange
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		final var unexpectedStatus = "UNEXPECTED_STATUS";

		mockFolioFixture.mockGetTransactionStatus(localRequestId, unexpectedStatus);

		// Act
		final var localRequest = getItem(localItemId, localRequestId);

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus(unexpectedStatus),
			hasRawStatus(unexpectedStatus)
		));
	}

	@Test
	void shouldReportItemStillOutFromInventoryWhenTransactionHasBeenCancelled() {
		// DCB cancels the lender transaction so the returning item is not re-captured, which also makes
		// the transaction terminal and useless for tracking. Inventory survives that: FOLIO leaves the
		// item "In transit" (reason "DCB cancelled") until it is physically checked in at the owning
		// library. Report that, so a request parked in AWAITING_RETURN_TO_SUPPLIER keeps waiting.
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, "CANCELLED");
		mockFolioFixture.mockQueryItemsById(localItemId, "In transit");

		final var localItem = getItem(localItemId, localRequestId);

		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("TRANSIT"),
			hasRawStatus("In transit")
		));
	}

	@Test
	void shouldReportItemHomeFromInventoryWhenTransactionHasBeenCancelled() {
		// The signal the parked request is waiting for. Once the item is checked in at the owning
		// library FOLIO makes it Available, and the request can cancel and finalise - which is when the
		// borrowing library's virtual records are safe to delete.
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, "CANCELLED");
		mockFolioFixture.mockQueryItemsById(localItemId, "Available");

		final var localItem = getItem(localItemId, localRequestId);

		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasStatus("AVAILABLE"),
			hasRawStatus("Available")
		));
	}

	@Test
	void shouldKeepTheTransactionAnswerWhenInventoryDoesNotKnowTheItem() {
		// A borrowing library's record lives in mod-circulation-item, not inventory, so the lookup finds
		// nothing. Fall back to the transaction rather than inventing a status.
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, "CANCELLED");
		mockFolioFixture.mockQueryItemsByIdNotFound(localItemId);

		final var localItem = getItem(localItemId, localRequestId);

		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasRawStatus("CANCELLED")
		));
	}

	@Test
	void shouldKeepTheTransactionAnswerWhenInventoryIsUnavailable() {
		// Tracking must not break because inventory is down.
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, "CANCELLED");
		mockFolioFixture.mockQueryItemsById(localItemId, response().withStatusCode(500));

		final var localItem = getItem(localItemId, localRequestId);

		assertThat(localItem, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasRawStatus("CANCELLED")
		));
	}

	@Test
	void shouldNotConsultInventoryWhileTheTransactionIsStillLive() {
		// Only a CANCELLED transaction is mute. Everything else is still authoritative, and CLOSED
		// already means the item is home - no inventory mock is registered, so consulting it would fail.
		final var localRequestId = randomUUID().toString();
		final var localItemId = randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, "CLOSED");

		final var localItem = getItem(localItemId, localRequestId);

		assertThat(localItem, allOf(
			notNullValue(),
			hasStatus("AVAILABLE"),
			hasRawStatus("CLOSED")
		));
	}

	private HostLmsItem getItem(String localItemId, String localRequestId) {
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder()
			.localId(localItemId)
			.localRequestId(localRequestId)
			.build();

		return singleValueFrom(client.getItem(hostLmsItem));
	}
}

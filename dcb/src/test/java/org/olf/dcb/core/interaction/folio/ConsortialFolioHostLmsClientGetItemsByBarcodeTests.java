package org.olf.dcb.core.interaction.folio;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;
import services.k_int.test.mockserver.MockServerMicronautTest;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasBibId;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasRawStatus;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasStatus;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientGetItemsByBarcodeTests {
	private static final String CATALOGUING_HOST_LMS_CODE = "folio-cataloguing-host-lms";

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockFolioFixture mockFolioFixture;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(CATALOGUING_HOST_LMS_CODE, "https://fake-cataloguing-folio",
			API_KEY, "", "");

		mockServerClient.reset();
		mockFolioFixture = new MockFolioFixture(mockServerClient, testResourceLoaderProvider, "fake-cataloguing-folio", API_KEY);

		client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);
	}

	@Test
	void shouldFindAvailableItemByBarcode() {
		// Arrange
		final var barcode = "2222222";
		final var expectedItemId = "9a326225-6530-41cc-9399-a61987bfab3c";
		final var expectedHoldingsRecordId = "16f40c4e-235d-4912-a683-2ad919cc8b07";
		final var expectedInstanceId = "7fbd5d84-62d1-44c6-9c45-6cb173998c26";

		mockFolioFixture.mockQueryItemsByBarcode(barcode, "query-items-response.json");
		mockFolioFixture.mockQueryInstancesByHoldingsRecordId(expectedHoldingsRecordId, "query-instances-response.json");

		// Act
		final var item = getLmsItem(barcode);

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(expectedItemId),
			hasBarcode(barcode),
			hasStatus("AVAILABLE"),
			hasRawStatus("Available"),
			hasBibId(expectedInstanceId)
		));
	}

	@Test
	void shouldReturnNullWhenNoItemFound() {
		// Arrange
		final var barcode = "nonexistent";

		mockFolioFixture.mockQueryItemsByBarcode(barcode, response()
			.withStatusCode(200)
			.withBody(json(InventoryItemCollection.builder().items(List.of()).build())));

		// Act
		final var item = getLmsItem(barcode);

		// Assert
		assertThat(item, nullValue());
	}

	@Test
	void shouldReturnItemWithoutBibIdWhenHoldingsRecordIdIsNull() {
		// Arrange
		final var barcode = "3333333";
		final var itemId = "a1234567-89ab-cdef-0123-456789abcdef";

		mockFolioFixture.mockQueryItemsByBarcode(barcode, response()
			.withStatusCode(200)
			.withBody(json(InventoryItemCollection.builder()
				.items(List.of(InventoryItem.builder()
					.id(itemId)
					.barcode(barcode)
					.holdingsRecordId(null)
					.status(InventoryItemStatus.builder().name("Available").build())
					.build()))
				.build())));

		// Act
		final var item = getLmsItem(barcode);

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(itemId),
			hasBarcode(barcode),
			hasStatus("AVAILABLE"),
			hasRawStatus("Available"),
			hasBibId(null)
		));
	}

	@Test
	void shouldSetBarcodeFromInputWhenItemHasNullBarcode() {
		// Arrange
		final var inputBarcode = "123";
		final var itemId = "9a326225-6530-41cc-9399-a61987bfab3c";

		mockFolioFixture.mockQueryItemsByBarcode(inputBarcode, response()
			.withStatusCode(200)
			.withBody(json(InventoryItemCollection.builder()
				.items(List.of(InventoryItem.builder()
					.id(itemId)
					.barcode(null) // item has no barcode in response
					.holdingsRecordId(null)
					.status(InventoryItemStatus.builder().name("Available").build())
					.build()))
				.build())));

		// Act
		final var item = getLmsItem(inputBarcode);

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(itemId),
			hasBarcode(inputBarcode), // barcode should be set from input
			hasStatus("AVAILABLE")
		));
	}

	@Test
	void shouldMapNullStatusToUnknown() {
		// Arrange
		final var barcode = "123";
		final var itemId = "9a326225-6530-41cc-9399-a61987bfab3c";

		mockFolioFixture.mockQueryItemsByBarcode(barcode, response()
			.withStatusCode(200)
			.withBody(json(InventoryItemCollection.builder()
				.items(List.of(InventoryItem.builder()
					.id(itemId)
					.barcode(barcode)
					.holdingsRecordId(null)
					.status(null) // null status
					.build()))
				.build())));

		// Act
		final var item = getLmsItem(barcode);

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(itemId),
			hasBarcode(barcode),
			hasStatus("Unknown"),
			hasRawStatus("Unknown")
		));
	}

	@Test
	void shouldReturnItemWithoutBibIdWhenInstanceNotFoundForHoldingsRecordId() {
		// Arrange
		final var barcode = "123";
		final var itemId = "9a326225-6530-41cc-9399-a61987bfab3c";
		final var holdingsRecordId = "16f40c4e-235d-4912-a683-2ad919cc8b07";

		mockFolioFixture.mockQueryItemsByBarcode(barcode, response()
			.withStatusCode(200)
			.withBody(json(InventoryItemCollection.builder()
				.items(List.of(InventoryItem.builder()
					.id(itemId)
					.barcode(barcode)
					.holdingsRecordId(holdingsRecordId)
					.status(InventoryItemStatus.builder().name("Available").build())
					.build()))
				.build())));

		// Mock empty instance response for holdingsRecordId
		mockFolioFixture.mockQueryInstancesByHoldingsRecordId(holdingsRecordId, response()
			.withStatusCode(200)
			.withBody(json(InventoryInstanceCollection.builder()
				.instances(List.of())
				.build())));

		// Act
		final var item = getLmsItem(barcode);

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(itemId),
			hasBarcode(barcode),
			hasStatus("AVAILABLE"),
			hasBibId(null) // bibId should be null when instance not found
		));
	}

	@Test
	void shouldReturnItemWithoutBibIdWhenInstanceFetchFails() {
		// Arrange
		final var barcode = "123";
		final var itemId = "9a326225-6530-41cc-9399-a61987bfab3c";
		final var holdingsRecordId = "16f40c4e-235d-4912-a683-2ad919cc8b07";

		mockFolioFixture.mockQueryItemsByBarcode(barcode, response()
			.withStatusCode(200)
			.withBody(json(InventoryItemCollection.builder()
				.items(List.of(InventoryItem.builder()
					.id(itemId)
					.barcode(barcode)
					.holdingsRecordId(holdingsRecordId)
					.status(InventoryItemStatus.builder().name("Available").build())
					.build()))
				.build())));

		// Mock error response for instance fetch
		mockFolioFixture.mockQueryInstancesByHoldingsRecordId(holdingsRecordId, response()
			.withStatusCode(500)
			.withBody("Internal Server Error"));

		// Act
		final var item = getLmsItem(barcode);

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(itemId),
			hasBarcode(barcode),
			hasStatus("AVAILABLE"),
			hasBibId(null) // bibId should be null when instance fetch fails
		));
	}

	@ParameterizedTest(name = "should map \"{0}\" status to \"{1}\"")
	@MethodSource("statusMappingProvider")
	void shouldMapStatusCorrectly(String rawStatus, String expectedMappedStatus) {
		// Arrange
		final var barcode = "123";
		final var itemId = "9a326225-6530-41cc-9399-a61987bfab3c";

		mockFolioFixture.mockQueryItemsByBarcode(barcode, response()
			.withStatusCode(200)
			.withBody(json(InventoryItemCollection.builder()
				.items(List.of(InventoryItem.builder()
					.id(itemId)
					.barcode(barcode)
					.holdingsRecordId(null)
					.status(InventoryItemStatus.builder().name(rawStatus).build())
					.build()))
				.build())));

		// Act
		final var item = getLmsItem(barcode);

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(itemId),
			hasBarcode(barcode),
			hasStatus(expectedMappedStatus),
			hasRawStatus(rawStatus)
		));
	}

	private static Stream<Arguments> statusMappingProvider() {
		return Stream.of(
			Arguments.of("Checked out", "LOANED"),
			Arguments.of("In transit", "TRANSIT"),
			Arguments.of("Awaiting pickup", "HOLDSHELF"),
			Arguments.of("Missing", "MISSING"),
			Arguments.of("Declared lost", "MISSING"),
			Arguments.of("Some unknown status", "Some unknown status")
		);
	}

	@Nullable
	private HostLmsItem getLmsItem(String barcode) {
		return singleValueFrom(client.getItemByBarcode(barcode));
	}
}

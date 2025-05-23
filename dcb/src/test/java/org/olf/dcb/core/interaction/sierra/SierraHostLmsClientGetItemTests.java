package org.olf.dcb.core.interaction.sierra;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_LOANED;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_MISSING;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.*;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraHostLmsClientGetItemTests {
	private static final String HOST_LMS_CODE = "sierra-get-item";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://sierra-get-item.com";
		final String KEY = "key";
		final String SECRET = "secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		final var sierraLoginFixture = sierraApiFixtureProvider.loginFixtureFor(mockServerClient);

		sierraLoginFixture.failLoginsForAnyOtherCredentials(KEY, SECRET);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL);
	}

	@Test
	@SneakyThrows
	void shouldFindItemByLocalId() {
		// Arrange
		final var localItemId = sierraItemsAPIFixture.generateLocalItemId();
		final var barcode = "23646535";

		sierraItemsAPIFixture.mockGetItemById(localItemId,
			SierraItem.builder()
				.id(localItemId)
				.barcode(barcode)
				.statusCode("-")
				.fixedFields(Map.of(71, FixedField.builder().value(1).build()))
				.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).build();

		final var item = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasBarcode(barcode),
			hasStatus(ITEM_AVAILABLE),
			hasRawStatus("-"),
			hasRenewalCount(1)
		));
	}

	@Test
	@SneakyThrows
	void shouldDefaultRenewalCount() {
		// Arrange
		final var localItemId = sierraItemsAPIFixture.generateLocalItemId();

		sierraItemsAPIFixture.mockGetItemById(localItemId, SierraItem.builder().build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).build();

		final var item = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(item, allOf(
			hasRenewalCount(0)
		));
	}

	@Test
	@SneakyThrows
	void shouldMapAvailableWithDueDateToLoaned() {
		// Arrange
		final var localItemId = sierraItemsAPIFixture.generateLocalItemId();
		final var barcode = "028476477";

		sierraItemsAPIFixture.mockGetItemById(localItemId,
			SierraItem.builder()
				.id(localItemId)
				.barcode(barcode)
				.statusCode("-")
				.dueDate(Instant.now().plus(5, DAYS))
				.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).build();

		final var item = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasBarcode(barcode),
			hasStatus(ITEM_LOANED),
			hasRawStatus("-")
		));
	}

	@ParameterizedTest
	@SneakyThrows
	@CsvSource({"t,TRANSIT", "@,OFFSITE", "#,RECEIVED", "!,HOLDSHELF", "o,LIBRARY_USE_ONLY", "%,RETURNED", "m,MISSING", "&,REQUESTED"})
	void shouldIgnoreDueDateWhenAnyStatusOtherThanAvailable(String statusCode, String expectedStatus) {
		// Arrange
		final var localItemId = sierraItemsAPIFixture.generateLocalItemId();
		final var localItemBarcode = "0184573765";

		sierraItemsAPIFixture.mockGetItemById(localItemId,
			SierraItem.builder()
				.id(localItemId)
				.barcode(localItemBarcode)
				.statusCode(statusCode)
				.dueDate(Instant.now().plus(5, DAYS))
				.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).build();

		final var item = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasBarcode(localItemBarcode),
			hasStatus(expectedStatus),
			hasRawStatus(statusCode)
		));
	}

	@Test
	@SneakyThrows
	void shouldTolerateNullStatusForDeletedItem() {
		// Arrange
		final var localItemId = sierraItemsAPIFixture.generateLocalItemId();
		final var barcode = "108573653";

		sierraItemsAPIFixture.mockGetItemById(localItemId,
			SierraItem.builder()
				.id(localItemId)
				.barcode(barcode)
				.deleted(true)
				.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).build();

		final var item = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasBarcode(barcode),
			hasStatus(ITEM_MISSING),
			hasRawStatus(null)
		));
	}
}

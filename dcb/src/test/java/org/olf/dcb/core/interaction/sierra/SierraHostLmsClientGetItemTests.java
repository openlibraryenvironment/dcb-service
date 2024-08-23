package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_MISSING;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasRawStatus;
import static org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers.hasStatus;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
		final var localItemId = "37636433";
		final var barcode = "23646535";

		sierraItemsAPIFixture.mockGetItemById(localItemId,
			SierraItem.builder()
				.id(localItemId)
				.barcode(barcode)
				.statusCode("-")
				.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var item = singleValueFrom(client.getItem(localItemId, null));

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasLocalId(localItemId),
			hasBarcode(barcode),
			hasStatus(ITEM_AVAILABLE),
			hasRawStatus("-")
		));
	}

	@Test
	@SneakyThrows
	void shouldTolerateNullStatusForDeletedItem() {
		// Arrange
		final var localItemId = "6736342";
		final var barcode = "108573653";

		sierraItemsAPIFixture.mockGetItemById(localItemId,
			SierraItem.builder()
				.id(localItemId)
				.barcode(barcode)
				.deleted(true)
				.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var item = singleValueFrom(client.getItem(localItemId, null));

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

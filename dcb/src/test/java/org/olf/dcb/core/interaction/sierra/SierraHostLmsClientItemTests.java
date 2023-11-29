package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraHostLmsClientItemTests {
	private static final String HOST_LMS_CODE = "sierra-item-api-tests";

	@Inject
	private ResourceLoader loader;
	@Inject
	private HostLmsFixture hostLmsFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://item-api-tests.com";
		final String KEY = "item-key";
		final String SECRET = "item-secret";

		SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
	}

	@Test
	@SneakyThrows
	void sierraCanRespondWithMultipleItems() {
		// Arrange
		sierraItemsAPIFixture.threeItemsResponseForBibId("65423515");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var items = singleValueFrom(client.getItems("65423515"));

		assertThat(items, containsInAnyOrder(
			allOf(
				hasLocalId("f2010365-e1b1-4a5d-b431-a3c65b5f23fb"),
				hasBarcode("9849123490"),
				hasCallNumber("BL221 .C48"),
				hasStatus(CHECKED_OUT),
				hasLocation("King 5th Floor", "ab5")
			),
			allOf(
				hasLocalId("c5bc9cd0-fc23-48be-9d52-647cea8c63ca"),
				hasBarcode("30800005315459"),
				hasCallNumber("HX157 .H8"),
				hasStatus(AVAILABLE),
				hasLocation("King 7th Floor", "ab7")
			),
			allOf(
				hasLocalId("69415d0a-ace5-49e4-96fd-f63855235bf0"),
				hasBarcode("30800005208449"),
				hasCallNumber("HC336.2 .S74 1969"),
				hasStatus(AVAILABLE),
				hasLocation("King 7th Floor", "ab7")
			)
		));
	}

	@Test
	void shouldProvideNoItemsWhenSierraRespondsWithNoRecordsFoundError() {
		sierraItemsAPIFixture.zeroItemsResponseForBibId("87878325");

		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var items = singleValueFrom(client.getItems("87878325"));

		assertThat("Should have no items", items, is(empty()));
	}

	private static Matcher<Item> hasLocalId(String expectedId) {
		return hasProperty("localId", is(expectedId));
	}

	private static Matcher<Item> hasBarcode(String expectedBarcode) {
		return hasProperty("barcode", is(expectedBarcode));
	}

	private static Matcher<Item> hasCallNumber(String expectedCallNumber) {
		return hasProperty("callNumber", is(expectedCallNumber));
	}

	private static Matcher<Item> hasStatus(ItemStatusCode expectedStatus) {
		return hasProperty("status", hasProperty("code", is(expectedStatus)));
	}

	private static Matcher<Item> hasLocation(String expectedName, String expectedCode) {
		return hasProperty("location", allOf(
			hasProperty("name", is(expectedName)),
			hasProperty("code", is(expectedCode))
		));
	}
}

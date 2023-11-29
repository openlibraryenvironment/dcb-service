package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
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

		assertThat(items, is(notNullValue()));
		assertThat(items, hasSize(3));

		final var firstItem = items.get(0);

		assertThat(firstItem, allOf(
			hasProperty("localId", is("f2010365-e1b1-4a5d-b431-a3c65b5f23fb")),
			hasProperty("barcode", is("9849123490")),
			hasProperty("callNumber", is("BL221 .C48")),
			hasProperty("status", hasProperty("code", is(CHECKED_OUT))),
			hasProperty("location", allOf(
				hasProperty("name", is("King 5th Floor")),
				hasProperty("code", is("ab5"))
			))
		));

		final var secondItem = items.get(1);

		assertThat(secondItem, allOf(
			hasProperty("localId", is("c5bc9cd0-fc23-48be-9d52-647cea8c63ca")),
			hasProperty("barcode", is("30800005315459")),
			hasProperty("callNumber", is("HX157 .H8")),
			hasProperty("status", hasProperty("code", is(AVAILABLE))),
			hasProperty("location", allOf(
				hasProperty("name", is("King 7th Floor")),
				hasProperty("code", is("ab7"))
			))
		));

		final var thirdItem = items.get(2);

		assertThat(thirdItem, allOf(
			hasProperty("localId", is("69415d0a-ace5-49e4-96fd-f63855235bf0")),
			hasProperty("barcode", is("30800005208449")),
			hasProperty("callNumber", is("HC336.2 .S74 1969")),
			hasProperty("status", hasProperty("code", is(AVAILABLE))),
			hasProperty("location", allOf(
				hasProperty("name", is("King 7th Floor")),
				hasProperty("code", is("ab7"))
			))
		));
	}

	@Test
	void shouldProvideNoItemsWhenSierraRespondsWithNoRecordsFoundError() {
		sierraItemsAPIFixture.zeroItemsResponseForBibId("87878325");

		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var items = singleValueFrom(client.getItems("87878325"));

		assertThat("Should have no items", items, is(empty()));
	}
}

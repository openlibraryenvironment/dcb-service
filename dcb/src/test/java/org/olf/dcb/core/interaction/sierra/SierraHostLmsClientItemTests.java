package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;

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

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem.getLocalId(), is("f2010365-e1b1-4a5d-b431-a3c65b5f23fb"));
		assertThat(firstItem.getBarcode(), is("9849123490"));
		assertThat(firstItem.getCallNumber(), is("BL221 .C48"));

		assertThat(firstItem.getStatus(), is(notNullValue()));
		assertThat(firstItem.getStatus().getCode(), is(CHECKED_OUT));
		assertThat(firstItem.getDueDate(), is(Instant.parse("2023-04-22T15:55:13Z")));

		assertThat(firstItem.getLocation(), is(notNullValue()));
		assertThat(firstItem.getLocation().getName(), is("King 5th Floor"));
		assertThat(firstItem.getLocation().getCode(), is("ab5"));

		final var secondItem = items.get(1);

		assertThat(secondItem, is(notNullValue()));
		assertThat(secondItem.getLocalId(), is("c5bc9cd0-fc23-48be-9d52-647cea8c63ca"));
		assertThat(secondItem.getBarcode(), is("30800005315459"));
		assertThat(secondItem.getCallNumber(), is("HX157 .H8"));

		assertThat(secondItem.getStatus(), is(notNullValue()));
		assertThat(secondItem.getStatus().getCode(), is(AVAILABLE));
		assertThat(secondItem.getDueDate(), is(nullValue()));

		assertThat(secondItem.getLocation(), is(notNullValue()));
		assertThat(secondItem.getLocation().getName(), is("King 7th Floor"));
		assertThat(secondItem.getLocation().getCode(), is("ab7"));

		final var thirdItem = items.get(2);

		assertThat(thirdItem, is(notNullValue()));
		assertThat(thirdItem.getLocalId(), is("69415d0a-ace5-49e4-96fd-f63855235bf0"));
		assertThat(thirdItem.getBarcode(), is("30800005208449"));
		assertThat(thirdItem.getCallNumber(), is("HC336.2 .S74 1969"));

		assertThat(thirdItem.getStatus(), is(notNullValue()));
		assertThat(thirdItem.getStatus().getCode(), is(AVAILABLE));
		assertThat(thirdItem.getDueDate(), is(nullValue()));

		assertThat(thirdItem.getLocation(), is(notNullValue()));
		assertThat(thirdItem.getLocation().getName(), is("King 7th Floor"));
		assertThat(thirdItem.getLocation().getCode(), is("ab7"));
	}

	@Test
	void shouldProvideNoItemsWhenSierraRespondsWithNoRecordsFoundError() {
		sierraItemsAPIFixture.zeroItemsResponseForBibId("87878325");

		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var items = singleValueFrom(client.getItems("87878325"));

		assertThat("Should have no items", items, is(empty()));
	}
}

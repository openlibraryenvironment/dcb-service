package org.olf.reshare.dcb.core.interaction.sierra;

import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.CHECKED_OUT;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.test.HostLmsFixture;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(Lifecycle.PER_CLASS)
class SierraLmsClientTests {
	private static final String HOST_LMS_CODE = "sierra-lms-client-tests";

	@Inject
	private ResourceLoader loader;
	@Inject
	private HostLmsFixture hostLmsFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://lms-client-tests.com";
		final String KEY = "lms-client-key";
		final String SECRET = "lms-client-secret";

		SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);

		hostLmsFixture.deleteAllHostLMS();

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);
	}

	@Test
	void shouldBeAbleToCreateAnItem() {
		// Arrange
		sierraItemsAPIFixture
			.successResponseForCreateItem(3743965, "hg6732", "56756785");

		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		// Act
		final var createdItem = client.createItem("3743965", "hg6732", "56756785").block();

		// Assert
		assertThat("Created item should not be null", createdItem, is(notNullValue()));

		assertThat("Local ID should come from response link",
			createdItem.getLocalId(), is("7916922"));
	}

	@Test
	void shouldProvideMultipleItemsWhenSierraRespondsWithMultipleItems() {
		sierraItemsAPIFixture.threeItemsResponseForBibId("4564554664");

		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var items = getItemsByBibId(client, "4564554664", "hostLmsCode");

		assertThat(items, is(notNullValue()));
		assertThat(items, hasSize(3));

		final var firstItem = items.get(0);

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem.getId(), is("f2010365-e1b1-4a5d-b431-a3c65b5f23fb"));
		assertThat(firstItem.getHostLmsCode(), is("hostLmsCode"));
		assertThat(firstItem.getBarcode(), is("9849123490"));
		assertThat(firstItem.getCallNumber(), is("BL221 .C48"));
		assertThat(firstItem.getDueDate(), is(ZonedDateTime.parse("2023-04-22T15:55:13Z")));

		assertThat(firstItem.getStatus(), is(notNullValue()));
		assertThat(firstItem.getStatus().getCode(), is(CHECKED_OUT));

		assertThat(firstItem.getLocation(), is(notNullValue()));
		assertThat(firstItem.getLocation().getName(), is("King 5th Floor"));
		assertThat(firstItem.getLocation().getCode(), is("ab5"));

		final var secondItem = items.get(1);

		assertThat(secondItem, is(notNullValue()));
		assertThat(secondItem.getId(), is("c5bc9cd0-fc23-48be-9d52-647cea8c63ca"));
		assertThat(secondItem.getHostLmsCode(), is("hostLmsCode"));
		assertThat(secondItem.getBarcode(), is("30800005315459"));
		assertThat(secondItem.getCallNumber(), is("HX157 .H8"));
		assertThat(secondItem.getDueDate(), is(nullValue()));

		assertThat(secondItem.getStatus(), is(notNullValue()));
		assertThat(secondItem.getStatus().getCode(), is(AVAILABLE));

		assertThat(secondItem.getLocation(), is(notNullValue()));
		assertThat(secondItem.getLocation().getName(), is("King 7th Floor"));
		assertThat(secondItem.getLocation().getCode(), is("ab7"));

		final var thirdItem = items.get(2);

		assertThat(thirdItem, is(notNullValue()));
		assertThat(thirdItem.getId(), is("69415d0a-ace5-49e4-96fd-f63855235bf0"));
		assertThat(thirdItem.getHostLmsCode(), is("hostLmsCode"));
		assertThat(thirdItem.getBarcode(), is("30800005208449"));
		assertThat(thirdItem.getCallNumber(), is("HC336.2 .S74 1969"));
		assertThat(thirdItem.getDueDate(), is(nullValue()));

		assertThat(thirdItem.getStatus(), is(notNullValue()));
		assertThat(thirdItem.getStatus().getCode(), is(AVAILABLE));

		assertThat(thirdItem.getLocation(), is(notNullValue()));
		assertThat(thirdItem.getLocation().getName(), is("King 7th Floor"));
		assertThat(thirdItem.getLocation().getCode(), is("ab7"));
	}

	@Test
	void shouldProvideNoItemsWhenSierraRespondsWithNoRecordsFoundError() {
		sierraItemsAPIFixture.zeroItemsResponseForBibId("65423515");

		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var items = getItemsByBibId(client, "65423515", "HostLmsCode");

		assertThat(items, hasSize(0));
	}

	@Test
	void shouldReportErrorWhenSierraRespondsWithInternalServerError() {
		sierraItemsAPIFixture.serverErrorResponseForBibId("565496");

		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> getItemsByBibId(client, "565496", "hostLmsCode"));

		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(INTERNAL_SERVER_ERROR));
		assertThat(response.code(), is(500));

		final var optionalBody = response.getBody(String.class);

		assertThat(optionalBody.isPresent(), is(true));

		final var body = optionalBody.get();

		assertThat(body, is("Broken"));
	}

	private static List<Item> getItemsByBibId(HostLmsClient client, String bibId,
		String hostLmsCode) {

		return client.getItemsByBibId(bibId, hostLmsCode).block();
	}
}

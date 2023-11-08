package services.k_int.interaction.sierra;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.SierraErrorMatchers.isBadJsonError;

import java.util.List;

import io.micronaut.context.annotation.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.matchers.SierraErrorMatchers;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.items.Params;
import services.k_int.interaction.sierra.patrons.ItemPatch;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SierraApiItemTests {
	private static final String HOST_LMS_CODE = "sierra-item-api-tests";

	@Inject
	private HttpClient client;
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

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE, "item");
	}

	@Test
	void shouldBeAbleToCreateAnItem() {
		// Arrange
		sierraItemsAPIFixture
			.successResponseForCreateItem(4641865, "ab1234", "68439643");

		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		// Act
		final var itemPatch = ItemPatch.builder()
			.bibIds(List.of(4641865))
			.location("ab1234")
			.barcodes(List.of("68439643"))
			.build();

		final var result = singleValueFrom(sierraApiClient.createItem(itemPatch));

		// Assert
		assertThat("Result should not be null", result, is(notNullValue()));

		assertThat("Result should contain a link", result.getLink(),
			is("https://sandbox.iii.com/iii/sierra-api/v6/items/7916922"));
	}

	@Test
	void createItemShouldHandleJsonErrorResponse() {
		// Arrange
		sierraItemsAPIFixture.jsonErrorResponseForCreateItem();

		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		// Act
		final var itemPatch = ItemPatch.builder()
			.bibIds(List.of(7655654))
			.build();

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> singleValueFrom(sierraApiClient.createItem(itemPatch)));

		// Assert
		final var body = SierraErrorMatchers.getResponseBody(exception);

		assertThat(body, isBadJsonError());
	}

	@Test
	@SneakyThrows
	void sierraCanRespondWithMultipleItems() {
		sierraItemsAPIFixture.threeItemsResponseForBibId("65423515");

		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		var response = Mono.from(sierraApiClient.items(
			Params.builder()
				.bibId("65423515")
				.deleted(false)
				.build()))
			.block();

		assertThat(response, is(notNullValue()));
		assertThat(response.getTotal(), is(3));

		final var items = response.getEntries();

		assertThat(items, is(notNullValue()));
		assertThat(items, hasSize(3));

		final var firstItem = items.get(0);

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem.getId(), is("f2010365-e1b1-4a5d-b431-a3c65b5f23fb"));
		assertThat(firstItem.getBarcode(), is("9849123490"));
		assertThat(firstItem.getCallNumber(), is("BL221 .C48"));

		assertThat(firstItem.getStatus(), is(notNullValue()));
		assertThat(firstItem.getStatus().getCode(), is("-"));
		assertThat(firstItem.getStatus().getDuedate(), is("2023-04-22T15:55:13Z"));

		assertThat(firstItem.getLocation(), is(notNullValue()));
		assertThat(firstItem.getLocation().getName(), is("King 5th Floor"));
		assertThat(firstItem.getLocation().getCode(), is("ab5"));

		final var secondItem = items.get(1);

		assertThat(secondItem, is(notNullValue()));
		assertThat(secondItem.getId(), is("c5bc9cd0-fc23-48be-9d52-647cea8c63ca"));
		assertThat(secondItem.getBarcode(), is("30800005315459"));
		assertThat(secondItem.getCallNumber(), is("HX157 .H8"));

		assertThat(secondItem.getStatus(), is(notNullValue()));
		assertThat(secondItem.getStatus().getCode(), is("-"));
		assertThat(secondItem.getStatus().getDuedate(), is(nullValue()));

		assertThat(secondItem.getLocation(), is(notNullValue()));
		assertThat(secondItem.getLocation().getName(), is("King 7th Floor"));
		assertThat(secondItem.getLocation().getCode(), is("ab7"));

		final var thirdItem = items.get(2);

		assertThat(thirdItem, is(notNullValue()));
		assertThat(thirdItem.getId(), is("69415d0a-ace5-49e4-96fd-f63855235bf0"));
		assertThat(thirdItem.getBarcode(), is("30800005208449"));
		assertThat(thirdItem.getCallNumber(), is("HC336.2 .S74 1969"));

		assertThat(thirdItem.getStatus(), is(notNullValue()));
		assertThat(thirdItem.getStatus().getCode(), is("-"));
		assertThat(thirdItem.getStatus().getDuedate(), is(nullValue()));

		assertThat(thirdItem.getLocation(), is(notNullValue()));
		assertThat(thirdItem.getLocation().getName(), is("King 7th Floor"));
		assertThat(thirdItem.getLocation().getCode(), is("ab7"));
	}

	@Test
	void shouldProvideNoItemsWhenSierraRespondsWithNoRecordsFoundError() {
		sierraItemsAPIFixture.zeroItemsResponseForBibId("87878325");

		// Need to create a new client for this test
		// because it fails when re-using the client
		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		var response = Mono.from(sierraApiClient.items(
				Params.builder()
					.bibId("87878325")
					.deleted(false)
					.build()))
			.block();

		assertThat("Response should be empty", response, is(nullValue()));
	}
}

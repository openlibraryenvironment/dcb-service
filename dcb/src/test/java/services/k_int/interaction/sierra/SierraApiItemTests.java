package services.k_int.interaction.sierra;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.sierra.HostLmsSierraApiClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.items.Params;
import services.k_int.interaction.sierra.patrons.ItemPatch;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.List;

import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.reshare.dcb.test.PublisherUtils.singleValueFrom;

@MockServerMicronautTest
@MicronautTest(transactional = false, propertySources = { "classpath:configs/SierraApiItemTests.yml" }, rebuildContext = true)
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SierraApiItemTests {
	private static final String SIERRA_TOKEN = "test-token-for-user";

	// Properties should line up with included property source for the spec.
	@Property(name = "hosts.sierra-items-api-tests.client.base-url")
	private String sierraHost;

	@Property(name = "hosts.sierra-items-api-tests.client.key")
	private String sierraUser;

	@Property(name = "hosts.sierra-items-api-tests.client.secret")
	private String sierraPass;

	@Inject
	private HttpClient client;

	@Inject
	private ResourceLoader loader;

	@Inject
	private HostLmsService hostLmsService;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		SierraTestUtils.mockFor(mock, sierraHost)
			.setValidCredentials(sierraUser, sierraPass, SIERRA_TOKEN, 60);

		sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);
	}

	@Test
	void shouldBeAbleToCreateAnItem() {
		// Arrange
		sierraItemsAPIFixture
			.successResponseForCreateItem(4641865, "ab1234", "68439643");

		final var sierraApiClient = createClient();

		// Act
		final var itemPatch = ItemPatch.builder()
			.bibIds(List.of(4641865))
			//.itemType(574574)
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

		final var sierraApiClient = createClient();

		// Act
		final var itemPatch = ItemPatch.builder()
			.bibIds(List.of(7655654))
			.build();

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> singleValueFrom(sierraApiClient.createItem(itemPatch)));

		// Assert
		final var response = exception.getResponse();

		assertThat("Should return a bad request status",
			response.getStatus(), is(INTERNAL_SERVER_ERROR));

		final var optionalBody = response.getBody(SierraError.class);

		assertThat("Response should have a body",
			optionalBody.isPresent(), is(true));

		final var body = optionalBody.get();

		assertThat("Error should have a name",
			body.getName(), is("Bad JSON/XML Syntax"));

		assertThat("Error should have a description",
			body.getDescription(), is("Please check that the JSON fields/values are of the expected JSON data types"));

		assertThat("Error should have a code",
			body.getCode(), is(130));

		assertThat("Error should have a specific code",
			body.getSpecificCode(), is(0));
	}

	@Test
	@SneakyThrows
	void sierraCanRespondWithMultipleItems() {
		sierraItemsAPIFixture.threeItemsResponseForBibId("65423515");

		final var sierraApiClient = createClient();

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

		final var secondItem = items.get(1);

		assertThat(secondItem, is(notNullValue()));
		assertThat(secondItem.getId(), is("c5bc9cd0-fc23-48be-9d52-647cea8c63ca"));

		final var thirdItem = items.get(2);

		assertThat(thirdItem, is(notNullValue()));
		assertThat(thirdItem.getId(), is("69415d0a-ace5-49e4-96fd-f63855235bf0"));
	}

	@Test
	void shouldProvideNoItemsWhenSierraRespondsWithNoRecordsFoundError() {
		sierraItemsAPIFixture.zeroItemsResponseForBibId("87878325");

		// Need to create a new client for this test
		// because it fails when re-using the client
		final var sierraApiClient = createClient();

		var response = Mono.from(sierraApiClient.items(
				Params.builder()
					.bibId("87878325")
					.deleted(false)
					.build()))
			.block();

		assertThat(response, is(notNullValue()));
		assertThat(response.getEntries(), hasSize(0));
	}
	
	private HostLmsSierraApiClient createClient() {
		final var testHostLms = hostLmsService.findByCode("sierra-items-api-tests").block();

		// Need to create a client directly
		// because injecting gives incorrectly configured client
		return new HostLmsSierraApiClient(testHostLms, client);
	}
}

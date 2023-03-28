package org.olf.reshare.dcb.api;

import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(transactional = false, propertySources = { "classpath:configs/LiveAvailabilityApiTests.yml" }, rebuildContext = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveAvailabilityApiTests {
	private final String ITEMS_MOCK_ROOT = "classpath:mock-responses/sierra/items";
	private static final String SIERRA_TOKEN = "test-token-for-user";
	@Inject
	ResourceLoader loader;
	@Inject
	@Client("/")
	HttpClient client;
	// Properties should line up with included property source for the spec.
	@Property(name = "hosts.test1.client.base-url")
	private String sierraHost;
	@Property(name = "hosts.test1.client.key")
	private String sierraUser;
	@Property(name = "hosts.test1.client.secret")
	private String sierraPass;


	private String getResourceAsString(String resourceName) throws IOException {
		return new String(loader.getResourceAsStream(ITEMS_MOCK_ROOT + resourceName)
			.get().readAllBytes());
	}

	@BeforeAll
	public void addFakeSierraApis(MockServerClient mock) throws IOException {

		var mockSierra = SierraTestUtils
			.mockFor(mock, sierraHost)
			.setValidCredentials(sierraUser, sierraPass, SIERRA_TOKEN, 60);

		// Mock items returned by the sierra system for ingest.
		mockSierra.whenRequest(req -> req.withMethod("GET")
				.withPath("/iii/sierra-api/v6/items")
				.withQueryStringParameter("deleted", "false")
				.withQueryStringParameter("bibIds", "807f604a-37c2-4c76-86f3-220082ada83f"))
			.respond(response()
				.withStatusCode(200)
				.withContentType(APPLICATION_JSON)
				.withBody(json(new String(loader
					.getResourceAsStream(ITEMS_MOCK_ROOT + "/sierra-api-items-bibIds.json")
					.orElseThrow()
					.readAllBytes()))));

		mockSierra.whenRequest(req -> req.withMethod("GET")
			.withPath("/iii/sierra-api/v6/items")
				.withQueryStringParameter("deleted", "false")
				.withQueryStringParameter("bibIds", "test"))
			.respond(response()
				.withStatusCode(404)
				.withContentType(APPLICATION_JSON)
				.withBody(json(new String(loader
					.getResourceAsStream(ITEMS_MOCK_ROOT + "/sierra-api-items-0.json")
					.orElseThrow()
					.readAllBytes()))));
	}

	@Test
	@SneakyThrows
	void canProvideAListOfAvailableItemsViaLiveAvailabilityApi() {
		final var uri = UriBuilder.of("/items/availability")
			.queryParam("bibRecordId", "807f604a-37c2-4c76-86f3-220082ada83f")
			.queryParam("hostLmsCode", "test1")
			.build();

		final var availabilityResponse = client.toBlocking()
			.retrieve(HttpRequest.GET(uri), AvailabilityResponse.class);

		assertThat(availabilityResponse, is(notNullValue()));

		final var items = availabilityResponse.getItemList();

		assertThat(items, is(notNullValue()));
		assertThat(items.size(), is(2));
		assertThat(availabilityResponse.getBibRecordId(), is("807f604a-37c2-4c76-86f3-220082ada83f"));
		assertThat(availabilityResponse.getHostLmsCode(), is("test1"));

		final var firstItem = items.get(0);

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem.getId(), is("67e91c1c-ada2-40cc-92dc-75db59d776a5"));

		final var firstItemStatus = firstItem.getStatus();

		assertThat(firstItemStatus, is(notNullValue()));
		assertThat(firstItemStatus.getCode(), is("-"));
		assertThat(firstItemStatus.getDisplayText(), is("AVAILABLE"));
		assertThat(firstItemStatus.getDueDate(), is("2021-02-25T12:00:00Z"));

		final var firstItemLocation = firstItem.getLocation();

		assertThat(firstItemLocation, is(notNullValue()));
		assertThat(firstItemLocation.getCode(), is("ab6"));
		assertThat(firstItemLocation.getName(), is("King 6th Floor"));

		final var secondItem = items.get(1);

		assertThat(secondItem, is(notNullValue()));
		assertThat(secondItem.getId(), is("5e9a80f9-c105-4984-a267-f9160caafd3b"));

		final var secondItemStatus = secondItem.getStatus();

		assertThat(secondItemStatus, is(notNullValue()));
		assertThat(secondItemStatus.getCode(), is("-"));
		assertThat(secondItemStatus.getDisplayText(), is("AVAILABLE"));
		assertThat(secondItemStatus.getDueDate(), is(nullValue()));

		final var secondItemLocation = secondItem.getLocation();

		assertThat(secondItemLocation, is(notNullValue()));
		assertThat(secondItemLocation.getCode(), is("ab6"));
		assertThat(secondItemLocation.getName(), is("King 6th Floor"));
	}

	@Test
	@SneakyThrows
	void failsWhenNoItemsFound() {

		final var uri = UriBuilder.of("/items/availability")
			.queryParam("bibRecordId", "test")
			.queryParam("hostLmsCode", "test1")
			.build();

		// These are separate variables to only have single invocation in assertThrows
		final var blockingClient = client.toBlocking();
		final var request = HttpRequest.GET(uri);

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> blockingClient.exchange(request));

		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(INTERNAL_SERVER_ERROR));
		assertThat(response.code(), is(500));
	}
}

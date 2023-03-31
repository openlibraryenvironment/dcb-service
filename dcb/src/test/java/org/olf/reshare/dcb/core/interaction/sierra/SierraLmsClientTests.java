package org.olf.reshare.dcb.core.interaction.sierra;

import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.mockserver.model.MediaType.TEXT_PLAIN;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockserver.client.ForwardChainExpectation;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.Item;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(transactional = false, propertySources = { "classpath:configs/SierraLmsClientTests.yml" }, rebuildContext = true)
@TestInstance(Lifecycle.PER_CLASS)
class SierraLmsClientTests {
	private static final String SIERRA_TOKEN = "test-token-for-user";

	@Inject
	ResourceLoader loader;

	@Inject
	HostLmsService hostLmsService;

	// Properties should line up with included property source for the spec.
	@Property(name = "hosts.test1.client.base-url")
	private String sierraHost;

	@Property(name = "hosts.test1.client.key")
	private String sierraUser;

	@Property(name = "hosts.test1.client.secret")
	private String sierraPass;

	private static final String CP_RESOURCES = "classpath:mock-responses/sierra/";

	@SneakyThrows
	private String getResourceAsString(String resourceName) {
		return new String(loader.getResourceAsStream(CP_RESOURCES + resourceName).get().readAllBytes());
	}

	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		SierraTestUtils.mockFor(mock, sierraHost)
			.setValidCredentials(sierraUser, sierraPass, SIERRA_TOKEN, 60);
	}

	@Test
	void shouldProvideMultipleItemsWhenSierraRespondsWithMultipleItems(MockServerClient mock) {
		mockGetItemsForBibId(mock, "4564554664")
			.respond(
				withSierraResponse(response(), 200, "items/sierra-api-items-N.json"));

		final var client = hostLmsService.getClientFor("test1").block();

		final var items = getItemsByBibId(client, "4564554664", "hostLmsCode");

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
	void shouldProvideNoItemsWhenSierraRespondsWithNoRecordsFoundError(MockServerClient mock) {
		mockGetItemsForBibId(mock, "0")
		.respond(
			withSierraResponse(notFoundResponse(), 404, "items/sierra-api-items-0.json"));

		final var client = hostLmsService.getClientFor("test1").block();

		final var items = getItemsByBibId(client, "0", "HostLmsCode");

		assertThat(items, hasSize(0));
	}

	@Test
	void shouldReportErrorWhenSierraRespondsWithInternalServerError(MockServerClient mock) {
		// This is a made up response (rather than captured from the sandbox)
		// in order to demonstrate that general failures of the API are propagated
		mockGetItemsForBibId(mock, "565496")
			.respond(notFoundResponse()
				.withStatusCode(500)
				.withContentType(TEXT_PLAIN)
				.withBody("Broken"));

		final var client = hostLmsService.getClientFor("test1").block();

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

	private static ForwardChainExpectation mockGetItemsForBibId(
		MockServerClient mock, String bibId) {

		return mock.when(
			request()
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/items")
				.withQueryStringParameter("bibIds", bibId)
				.withQueryStringParameter("deleted", "false")
				.withHeader("Accept", "application/json"));
	}

	private HttpResponse withSierraResponse(HttpResponse response, int statusCode,
		String resourceName) {

		return response
		 .withStatusCode(statusCode)
		 .withContentType(APPLICATION_JSON)
		 .withBody(json(getResourceAsString(resourceName)));
	}

	private static List<Item> getItemsByBibId(HostLmsClient client, String bibId, String hostLmsCode) {
		return client.getItemsByBibId(bibId, hostLmsCode).block();
	}
}

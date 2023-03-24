package services.k_int.interaction.sierra;

import static io.micronaut.http.HttpStatus.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import java.io.IOException;
import java.util.List;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.items.Params;
import services.k_int.interaction.sierra.items.ResultSet;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class SierraApiItemTests {
	private final String MOCK_ROOT = "classpath:mock-responses/sierra/items";

	@Inject
	private SierraApiClient client;

	@Inject
	private ResourceLoader loader;

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/items/?limit=3&offset=1&fields=id%2CupdatedDate%2CcreatedDate%2Cdeleted%2CbibIds%2Clocation%2Cstatus%2Cvolumes%2Cbarcode%2CcallNumber&deleted=false&status=-&suppressed=false&locations=*
	*/
	@Test
	@SneakyThrows
	void sierraCanRespondWithMultipleItems(MockServerClient mock) {

		// Mock the response from Sierra
		mock.when(
			request()
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/items/")
				.withQueryStringParameter("limit", "3")
				.withQueryStringParameter("suppressed", "false")
				.withQueryStringParameter("offset", "1")
				.withQueryStringParameter("deleted", "false")
				.withQueryStringParameter("fields", "id,updatedDate,createdDate,deleted,bibIds,location,status,volumes,barcode,callNumber")
				.withQueryStringParameter("status", "-"))
			.respond(response()
				.withStatusCode(200)
				.withContentType(APPLICATION_JSON)
				.withBody(json(new String(loader
					.getResourceAsStream(MOCK_ROOT + "/sierra-api-items-N.json")
					.orElseThrow()
					.readAllBytes()))));

		// Fetch from sierra and block
		var response = Mono.from(client.items(
			Params.builder()
				.limit(3)
				.suppressed(false)
				.offset(1)
				.deleted(false)
				.fields(List.of("id","updatedDate","createdDate","deleted","bibIds","location","status","volumes","barcode","callNumber"))
				.status("-")
				.build()))
			.block();

		assertNotNull(response);
		assertEquals(response.getClass(), ResultSet.class);
		assertEquals(response.getTotal(), 3);

		final var items = response.getEntries();

		assertEquals(items.get(0).getId(), "f2010365-e1b1-4a5d-b431-a3c65b5f23fb");
		assertEquals(items.get(1).getId(), "c5bc9cd0-fc23-48be-9d52-647cea8c63ca");
		assertEquals(items.get(2).getId(), "69415d0a-ace5-49e4-96fd-f63855235bf0");
	}

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/items/?id=0
	*/
	@Test
	@Disabled // Matching expectation but 404 error is not being thrown?!
	void sierraRespondsWithErrorWhenNoItemsAreFound(MockServerClient mock) throws IOException {

		mock.when(
				request()
					.withHeader("Accept", "application/json")
					.withMethod("GET")
					.withPath("/iii/sierra-api/v6/items/")
					.withQueryStringParameter("bibIds", "0")
					.withQueryStringParameter("deleted", "false"))
			.respond(notFoundResponse()
				.withStatusCode(404)
				.withContentType(APPLICATION_JSON)
				.withBody(json(new String(loader
					.getResourceAsStream(MOCK_ROOT + "/sierra-api-items-0.json")
					.orElseThrow()
					.readAllBytes()))));

		// These are separate variables to only have single invocation in assertThrows
		final var exception = assertThrows(HttpClientResponseException.class,
			() ->  Mono.from(client.items(Params.builder()
					.bibIds(List.of("0"))
					.deleted(false)
					.build()))
				.block());

		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(NOT_FOUND));
		assertThat(response.code(), is(404));
	}
}

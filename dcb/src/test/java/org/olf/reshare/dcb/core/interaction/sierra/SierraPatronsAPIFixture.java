package org.olf.reshare.dcb.core.interaction.sierra;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import io.micronaut.core.io.ResourceLoader;
import lombok.SneakyThrows;

public class SierraPatronsAPIFixture {
	private static final String RESPONSE_RESOURCES_DIRECTORY = "classpath:mock-responses/sierra/";

	private final MockServerClient mock;
	private final ResourceLoader loader;

	public SierraPatronsAPIFixture(MockServerClient mock, ResourceLoader loader) {
		this.mock = mock;
		this.loader = loader;
	}

	public void postPatronResponse(String uniqueId) {
		mock.when(postPatronRequest(uniqueId)).respond( patronPlacedResponse() );
	}

	public void patronResponseForUniqueId(String uniqueId) {
		mock.when(getPatronFindRequest(uniqueId)).respond( patronFoundResponse() );
	}

	public void patronNotFoundResponseForUniqueId(String uniqueId) {
		mock.when(getPatronFindRequest(uniqueId)).respond( patronNotFoundResponse() );
	}

	private HttpResponse patronNotFoundResponse() {
		return withSierraResponse(notFoundResponse(), 404, "patrons/sierra-api-patron-not-found.json");
	}

	private HttpResponse patronFoundResponse() {
		return withSierraResponse(response(), 200, "patrons/sierra-api-patron-found.json");
	}

	private HttpResponse patronPlacedResponse() {
		return withSierraResponse(response(), 200, "patrons/sierra-api-post-patron.json");
	}

	@SneakyThrows
	private String getResourceAsString(String resourceName) {
		final var resourcePath = RESPONSE_RESOURCES_DIRECTORY + resourceName;
		final var optionalResource = loader.getResourceAsStream(resourcePath);
		if (optionalResource.isEmpty()) {
			throw new RuntimeException("Resource could not be found: " + resourcePath);
		}
		final var resource = optionalResource.get();
		return new String(resource.readAllBytes());
	}

	private HttpResponse withSierraResponse(HttpResponse response, int statusCode, String resourceName) {
		return response
			.withStatusCode(statusCode)
			.withContentType(APPLICATION_JSON)
			.withBody(json(getResourceAsString(resourceName)));
	}

	private HttpRequest postPatronRequest(String uniqueId) {
		return request()
			.withMethod("POST")
			.withPath("/iii/sierra-api/v6/patrons")
			.withHeader("Content-Type", "application/json")
			.withBody(json("{\"uniqueIds\": [\"" + uniqueId + "\"]}"));
	}

	private HttpRequest getPatronFindRequest(String uniqueId) {
		return request()
			.withMethod("GET")
			.withPath("/iii/sierra-api/v6/patrons/find")
			.withQueryStringParameter("varFieldTag", "u")
			.withQueryStringParameter("varFieldContent", uniqueId)
			.withHeader("Accept", "application/json");
	}
}


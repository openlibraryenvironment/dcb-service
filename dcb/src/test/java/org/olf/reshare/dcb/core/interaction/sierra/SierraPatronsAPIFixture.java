package org.olf.reshare.dcb.core.interaction.sierra;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.mockserver.model.MediaType.TEXT_PLAIN;

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

	public void postPatronResponse(String uniqueId, int returnId) {
		mock.when(postPatronRequest(uniqueId)).respond( patronPlacedResponse(returnId) );
	}

	public void postPatronErrorResponse(String uniqueId) {
		mock.when(postPatronRequest(uniqueId)).respond( patronErrorResponse() );
	}

	public void patronResponseForUniqueId(String uniqueId) {
		mock.when(getPatronFindRequest(uniqueId)).respond( patronFoundResponse() );
	}

	public void patronNotFoundResponseForUniqueId(String uniqueId) {
		mock.when(getPatronFindRequest(uniqueId)).respond( patronNotFoundResponse() );
	}

	public void patronHoldRequestResponse(String id, Integer recordNumber, String pickupLocation) {
		mock.when(getPatronHoldRequest(id, "i", recordNumber, pickupLocation)).respond( sierraResponseNoContent() );
	}

	public void patronHoldRequestErrorResponse(String id, Integer recordNumber, String pickupLocation) {
		mock.when(getPatronHoldRequest(id, "i", recordNumber, pickupLocation)).respond( patronErrorResponse() );
	}

	public void patronHoldResponse(String id) {
		mock.when(getPatronHoldRequest(id)).respond( patronHoldFoundResponse() );
	}

	public void patronHoldErrorResponse(String id) {
		mock.when(getPatronHoldRequest(id)).respond( patronErrorResponse() );
	}

	private HttpResponse patronNotFoundResponse() {
		return withSierraResponse(notFoundResponse(), 404, "patrons/sierra-api-patron-not-found.json");
	}

	private HttpResponse patronFoundResponse() {
		return withSierraResponse(response(), 200, "patrons/sierra-api-patron-found.json");
	}

	private HttpResponse patronErrorResponse() {
		return serverErrorResponse(response(), 500);
	}

	private HttpResponse patronPlacedResponse(int returnId) {
		return withSierraResponse(response(), 200, returnId);
	}

	private HttpResponse patronHoldFoundResponse() {
		return withSierraResponse(response(), 200, "patrons/sierra-api-patron-hold.json");
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

	private HttpResponse withSierraResponse(HttpResponse response, int statusCode, int returnId) {
		return response
			.withStatusCode(statusCode)
			.withContentType(APPLICATION_JSON)
			.withBody(json("{\"link\": \"https://sandbox.iii.com/iii/sierra-api/v6/patrons/" + returnId + "\"}"));
	}

	private HttpResponse withSierraResponse(HttpResponse response, int statusCode, String resourceName) {
		return response
			.withStatusCode(statusCode)
			.withContentType(APPLICATION_JSON)
			.withBody(json(getResourceAsString(resourceName)));
	}

	private HttpResponse sierraResponseNoContent() {
		return response()
			.withStatusCode(204);
	}

	public HttpResponse serverErrorResponse(HttpResponse response, int statusCode) {
		// This is a made up response (rather than captured from the sandbox)
		// in order to demonstrate that general failures of the API are propagated
		return response
				.withStatusCode(statusCode)
				.withContentType(TEXT_PLAIN)
				.withBody("Broken");
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

	private HttpRequest getPatronHoldRequest(
		String id,
		String recordType,
		Integer recordNumber,
		String pickupLocation) {
		return request()
			.withMethod("POST")
			.withPath("/iii/sierra-api/v6/patrons/" + id + "/holds/requests")
			.withHeader("Accept", "application/json")
			.withBody(json("{" +
				"\"recordType\": \"" + recordType + "\"," +
				"\"recordNumber\": " + recordNumber + "," +
				"\"pickupLocation\": \"" + pickupLocation + "\"" +
				"}"));
	}

	private HttpRequest getPatronHoldRequest(String id) {
		return request()
			.withMethod("GET")
			.withPath("/iii/sierra-api/v6/patrons/" + id + "/holds");
	}
}


package org.olf.reshare.dcb.core.interaction.sierra;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.mockserver.model.MediaType.TEXT_PLAIN;

import java.util.List;

import org.mockserver.client.ForwardChainExpectation;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;

public class SierraItemsAPIFixture {
	private static final String RESPONSE_RESOURCES_DIRECTORY = "classpath:mock-responses/sierra/";
	private static final String ITEMS_PATH = "/iii/sierra-api/v6/items";

	private final MockServerClient mock;
	private final ResourceLoader loader;

	public SierraItemsAPIFixture(MockServerClient mock, ResourceLoader loader) {
		this.mock = mock;
		this.loader = loader;
	}

	public void threeItemsResponseForBibId(String bibId) {
		mockGetItemsForBibId(bibId)
			.respond(threeItemsResponse());
	}

	public void twoItemsResponseForBibId(String bibId) {
		mockGetItemsForBibId(bibId)
			.respond(twoItemsResponse());
	}

	public void zeroItemsResponseForBibId(String bibId) {
		mockGetItemsForBibId(bibId)
			.respond(zeroItemsResponse());
	}

	public HttpResponse threeItemsResponse() {
		return withSierraResponse(response(), 200,
			"items/sierra-api-three-items.json");
	}

	private HttpResponse twoItemsResponse() {
		return withSierraResponse(response(), 200,
			"items/sierra-api-two-items.json");
	}

	private HttpResponse zeroItemsResponse() {
		return withSierraResponse(notFoundResponse(), 404,
			"items/sierra-api-zero-items.json");
	}

	public void jsonErrorResponseForCreateItem() {
		mock.clear(createItemsRequest());

		mock.when(createItemsRequest())
			.respond(response()
				.withStatusCode(500)
				.withContentType(APPLICATION_JSON)
				.withBody(getResourceAsString("json-error.json")));
	}

	public void successResponseForCreateItem(Integer bibId, Integer itemType,
		String locationCode, String barcode) {

		mock.clear(createItemsRequest());

		final var body = ItemPatch.builder()
			.bibIds(List.of(bibId))
			.itemType(itemType)
			.location(locationCode)
			.barcodes(List.of(barcode))
			.build();

		mock.when(createItemsRequest()
				.withBody(json(body)))
			.respond(response()
				.withStatusCode(200)
				.withContentType(APPLICATION_JSON)
				.withBody(getResourceAsString("items/sierra-api-post-item-success-response.json")));
	}

	public void serverErrorResponseForBibId(String bibId) {
		// This is a made up response (rather than captured from the sandbox)
		// in order to demonstrate that general failures of the API are propagated
		mockGetItemsForBibId(bibId)
			.respond(response()
				.withStatusCode(500)
				.withContentType(TEXT_PLAIN)
				.withBody("Broken"));
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

	private HttpResponse withSierraResponse(HttpResponse response,
		int statusCode, String resourceName) {

		return response
			.withStatusCode(statusCode)
			.withContentType(APPLICATION_JSON)
			.withBody(json(getResourceAsString(resourceName)));
	}

	private static HttpRequest getItemsRequest() {
		return request()
			.withMethod("GET")
			.withPath(ITEMS_PATH);
	}

	private static HttpRequest createItemsRequest() {
		return request()
			.withMethod("POST")
			.withPath(ITEMS_PATH)
			.withHeader("Accept", "application/json");
	}

	private ForwardChainExpectation mockGetItemsForBibId(String bibId) {
		return mock.when(
			getItemsRequest()
				.withQueryStringParameter("bibIds", bibId)
				.withQueryStringParameter("deleted", "false")
				.withHeader("Accept", "application/json"));
	}

	@Serdeable
	@Data
	@Builder
	static class ItemPatch {
		List<Integer> bibIds;
		Integer itemType;
		String location;
		List<String> barcodes;
	}
}

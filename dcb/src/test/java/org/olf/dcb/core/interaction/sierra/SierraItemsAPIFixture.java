package org.olf.dcb.core.interaction.sierra;

import static org.mockserver.model.JsonBody.json;

import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

public class SierraItemsAPIFixture {
	private final MockServerClient mockServer;
	private final SierraMockServerRequests sierraMockServerRequests;
	private final SierraMockServerResponses sierraMockServerResponses;

	public SierraItemsAPIFixture(MockServerClient mockServer, ResourceLoader loader) {
		this.mockServer = mockServer;
		this.sierraMockServerRequests = new SierraMockServerRequests(
			"/iii/sierra-api/v6/items");

		this.sierraMockServerResponses = new SierraMockServerResponses(
			"classpath:mock-responses/sierra/", loader);
	}

	public void threeItemsResponseForBibId(String bibId) {
		mockServer.when(getItemsForBib(bibId))
			.respond(threeItemsResponse());
	}

	public void twoItemsResponseForBibId(String bibId) {
		mockServer.when(getItemsForBib(bibId))
			.respond(twoItemsResponse());
	}

	public void zeroItemsResponseForBibId(String bibId) {
		mockServer.when(getItemsForBib(bibId))
			.respond(zeroItemsResponse());
	}

	public void errorResponseForBibId(String bibId) {
		mockServer.when(getItemsForBib(bibId))
			.respond(sierraMockServerResponses.jsonError("json-error.json"));
	}

	public HttpResponse threeItemsResponse() {
		return sierraMockServerResponses.jsonSuccess(
			"items/sierra-api-three-items.json");
	}

	private HttpResponse twoItemsResponse() {
		return sierraMockServerResponses.jsonSuccess(
			"items/sierra-api-two-items.json");
	}

	private HttpResponse zeroItemsResponse() {
		return sierraMockServerResponses.notFound(
			"items/sierra-api-zero-items.json");
	}

	public void jsonErrorResponseForCreateItem() {
		mockServer.clear(sierraMockServerRequests.post());

		mockServer.when(sierraMockServerRequests.post())
			.respond(sierraMockServerResponses.jsonError("json-error.json"));
	}

	public void successResponseForCreateItem(Integer bibId, String locationCode, String barcode) {

		//mockServer.clear(sierraMockServerRequests.post());

		final var body = ItemPatch.builder()
			.bibIds(List.of(bibId))
			.location(locationCode)
			.barcodes(List.of(barcode))
			.build();

		mockServer
			.when(sierraMockServerRequests.post()
				.withBody(json(body)))
			.respond(sierraMockServerResponses.jsonSuccess(
				"items/sierra-api-post-item-success-response.json"));
	}

	public void serverErrorResponseForBibId(String bibId) {
		// This is a made up response (rather than captured from the sandbox)
		// in order to demonstrate that general failures of the API are propagated
		mockServer
			.when(getItemsForBib(bibId))
			.respond(sierraMockServerResponses.textError());
	}

	private HttpRequest getItemsForBib(String bibId) {
		return sierraMockServerRequests.get()
			.withQueryStringParameter("bibIds", bibId)
			.withQueryStringParameter("deleted", "false");
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

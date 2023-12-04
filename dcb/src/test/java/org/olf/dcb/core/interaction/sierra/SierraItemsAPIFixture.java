package org.olf.dcb.core.interaction.sierra;

import static org.mockserver.model.JsonBody.json;

import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.olf.dcb.test.TestResourceLoaderProvider;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import services.k_int.interaction.sierra.items.SierraItem;

@AllArgsConstructor
public class SierraItemsAPIFixture {
	private final MockServerClient mockServer;
	private final SierraMockServerRequests sierraMockServerRequests;
	private final SierraMockServerResponses sierraMockServerResponses;

	public SierraItemsAPIFixture(MockServerClient mockServer,
		TestResourceLoaderProvider testResourceLoaderProvider) {

		this(mockServer, new SierraMockServerRequests("/iii/sierra-api/v6/items"),
			new SierraMockServerResponses(
				testResourceLoaderProvider.forBasePath("classpath:mock-responses/sierra/")));
	}

	public void getItemById(String itemId) {
		mockServer
			.when(sierraMockServerRequests.get("/"+itemId))
			.respond(sierraMockServerResponses.jsonSuccess("items/1088431.json"));
	}

	public void itemsForBibId(String bibId, List<SierraItem> items) {
		mockServer.when(getItemsForBib(bibId))
			.respond(sierraMockServerResponses.jsonSuccess(json(
				ItemResultSet.builder()
					.entries(items)
					.build())));
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
			.respond(sierraMockServerResponses.noRecordsFound());
	}

	public void errorResponseForBibId(String bibId) {
		mockServer.when(getItemsForBib(bibId))
			.respond(sierraMockServerResponses.badRequestError());
	}

	public HttpResponse threeItemsResponse() {
		return sierraMockServerResponses.jsonSuccess(
			"items/sierra-api-three-items.json");
	}

	private HttpResponse twoItemsResponse() {
		return sierraMockServerResponses.jsonSuccess(
			"items/sierra-api-two-items.json");
	}

	public void jsonErrorResponseForCreateItem() {
		mockServer.clear(sierraMockServerRequests.post());

		mockServer.when(sierraMockServerRequests.post())
			.respond(sierraMockServerResponses.badRequestError());
	}

	public void successResponseForCreateItem(Integer bibId, String locationCode, String barcode) {
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

	public void unauthorisedResponseForCreateItem(Integer bibId, String locationCode, String barcode) {
		final var body = ItemPatch.builder()
			.bibIds(List.of(bibId))
			.location(locationCode)
			.barcodes(List.of(barcode))
			.build();

		mockServer
			.when(sierraMockServerRequests.post()
				.withBody(json(body)))
			.respond(sierraMockServerResponses.unauthorised());
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

	@Serdeable
	@Data
	@Builder
	static class ItemResultSet {
		int total;
		int start;
		@NotNull List<SierraItem> entries;
	}
}

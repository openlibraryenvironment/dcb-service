package org.olf.dcb.core.interaction.sierra;

import static org.mockserver.model.JsonBody.json;

import java.util.List;
import java.util.Map;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.olf.dcb.test.TestResourceLoaderProvider;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.items.Location;
import services.k_int.interaction.sierra.items.SierraItem;
import services.k_int.interaction.sierra.items.Status;

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

	public void mockGetItemById(String id, org.olf.dcb.core.interaction.sierra.SierraItem item) {
		mockGetItemById(id, sierraMockServerResponses.jsonSuccess(json(mapItem(item))));
	}

	public void mockGetItemById(String id, HttpResponse response) {
		mockServer.when(getItemById(id))
			.respond(response);
	}

	public void mockGetItemByIdReturnNoRecordsFound(String id) {
		mockServer.when(getItemById(id))
			.respond(sierraMockServerResponses.noRecordsFound());
	}

	private HttpRequest getItemById(String id) {
		return sierraMockServerRequests.get("/" + id);
	}

	public void itemsForBibId(String bibId, List<org.olf.dcb.core.interaction.sierra.SierraItem> items) {
		mockServer.when(getItemsForBib(bibId))
			.respond(sierraMockServerResponses.jsonSuccess(json(
				ItemResultSet.builder()
					.entries(items.stream()
						.map(SierraItemsAPIFixture::mapItem)
						.toList())
					.build())));
	}

	public void threeItemsResponseForBibId(String bibId) {
		mockServer.when(getItemsForBib(bibId))
			.respond(threeItemsResponse());
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

	private static SierraItem mapItem(org.olf.dcb.core.interaction.sierra.SierraItem item) {
		final var formattedDueDate = item.getDueDate() != null
			? item.getDueDate().toString()
			: null;

		final var itemType = item.getItemType();

		final Map<Integer, FixedField> fixedFields;

		if (itemType != null) {
			fixedFields = Map.of(61, FixedField.builder().value(itemType).build());
		}
		else {
			fixedFields = Map.of();
		}

		return SierraItem.builder()
			.id(item.getId())
			.barcode(item.getBarcode())
			.callNumber(item.getCallNumber())
			.status(Status.builder()
				.code(item.getStatusCode())
				.duedate(formattedDueDate)
				.build())
			.location(Location.builder()
				.name(item.getLocationName())
				.code(item.getLocationCode())
				.build())
			.itemType(itemType)
			.fixedFields(fixedFields)
			.holdCount(item.getHoldCount())
			.deleted(item.getDeleted())
			.suppressed(item.getSuppressed())
			.build();
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

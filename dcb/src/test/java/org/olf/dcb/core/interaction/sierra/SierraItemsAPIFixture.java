package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.core.interaction.sierra.SierraMockServerResponses.badRequestError;
import static org.olf.dcb.core.interaction.sierra.SierraMockServerResponses.jsonLink;
import static org.olf.dcb.core.interaction.sierra.SierraMockServerResponses.noRecordsFound;
import static org.olf.dcb.test.MockServerCommonResponses.noContent;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;
import static org.olf.dcb.test.MockServerCommonResponses.unauthorised;
import static org.olf.dcb.utils.CollectionUtils.mapList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.olf.dcb.test.MockServer;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.CheckoutResultSet;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.items.Location;
import services.k_int.interaction.sierra.items.SierraItem;
import services.k_int.interaction.sierra.items.Status;

@Slf4j
@AllArgsConstructor
public class SierraItemsAPIFixture {
	private final MockServer mockServer;
	private final SierraMockServerRequests sierraMockServerRequests;

	public SierraItemsAPIFixture(MockServer mockServer) {
		this(mockServer, new SierraMockServerRequests("/iii/sierra-api/v6/items"));
	}

	public void mockUpdateItem(String itemId) {
		mockServer.mock(putItem(itemId), noContent());
	}

	public void mockGetItemById(String id, org.olf.dcb.core.interaction.sierra.SierraItem item) {
		mockGetItemById(id, okJson(json(mapItem(item))));
	}

	public void mockGetItemById(String id, HttpResponse response) {
		mockServer.mock(getItemById(id), response);
	}

	public void mockGetItemByIdReturnNoRecordsFound(String id) {
		mockServer.mock(getItemById(id), noRecordsFound());
	}

	private HttpRequest getItemById(String id) {
		return sierraMockServerRequests.get("/" + id);
	}

	public void itemsForBibId(String bibId, List<org.olf.dcb.core.interaction.sierra.SierraItem> items) {
		itemsForBibId(bibId, items, 0);
	}

	public void itemsForBibId(String bibId, List<org.olf.dcb.core.interaction.sierra.SierraItem> items, int millisecondDelay) {
		mockServer.replaceMock(getItemsForBib(bibId), okJson(
			ItemResultSet.builder()
				.start(0)
				.total(items.size())
				.entries(mapList(items, SierraItemsAPIFixture::mapItem))
				.build())
			.withDelay(MILLISECONDS, millisecondDelay));
	}

	public String checkoutsForItem(String itemId) {
		mockServer.replaceMock(getItemCheckouts(itemId), "items/sierra-get-item-checkouts-success.json");

		// return needs to align with id of the json response
		// E.g.,		"id": "https://catalog-test.wustl.edu/iii/sierra-api/v6/patrons/checkouts/1811242",
		return "1811242";
	}

	public void checkoutsForItemWithMultiplePatronEntries(String itemId) {
		mockServer.replaceMock(getItemCheckouts(itemId),
			"items/sierra-get-item-checkouts-multiple-patron-match.json");
	}

	public void checkoutsForItemWithNoPatronEntries(String itemId) {
		mockServer.replaceMock(getItemCheckouts(itemId), okJson(
			CheckoutResultSet.builder()
				.entries(List.of())
				.build()
			));
	}

	public void checkoutsForItemWithNoRecordsFound(String itemId) {
		mockServer.replaceMock(getItemCheckouts(itemId), noRecordsFound());
	}

	public void zeroItemsResponseForBibId(String bibId) {
		mockServer.mock(getItemsForBib(bibId), noRecordsFound());
	}

	public void errorResponseForBibId(String bibId) {
		mockServer.mock(getItemsForBib(bibId), badRequestError());
	}

	public void jsonErrorResponseForCreateItem() {
		mockServer.replaceMock(sierraMockServerRequests.post(), badRequestError());
	}

	public void successResponseForCreateItem(Integer bibId, String locationCode,
		String barcode, String itemId) {

		final var body = ItemPatch.builder()
			.bibIds(List.of(bibId))
			.location(locationCode)
			.barcodes(List.of(barcode))
			.build();

		mockServer.mock(sierraMockServerRequests.post().withBody(json(body)),
			jsonLink("https://sandbox.iii.com/iii/sierra-api/v6/items/" + itemId));
	}

	public void unauthorisedResponseForCreateItem(Integer bibId, String locationCode, String barcode) {
		final var body = ItemPatch.builder()
			.bibIds(List.of(bibId))
			.location(locationCode)
			.barcodes(List.of(barcode))
			.build();

		mockServer.mock(sierraMockServerRequests.post().withBody(json(body)), unauthorised());
	}

	public void mockDeleteItem(String itemId) {
		mockServer.replaceMock(deleteItemRecord(itemId), noContent());
	}

	private HttpRequest deleteItemRecord(String itemId) {
		return sierraMockServerRequests.delete("/" + itemId);
	}

	private HttpRequest putItem(String itemId) {
		return sierraMockServerRequests.put("/" + itemId);
	}

	private HttpRequest getItemsForBib(String bibId) {
		return sierraMockServerRequests.get()
			.withQueryStringParameter("bibIds", bibId)
			.withQueryStringParameter("deleted", "false");
	}

	private HttpRequest getItemCheckouts(String itemId) {
		return sierraMockServerRequests.get("/" + itemId + "/checkouts");
	}

	private static SierraItem mapItem(org.olf.dcb.core.interaction.sierra.SierraItem item) {
		final var formattedDueDate = item.getDueDate() != null
			? item.getDueDate().toString()
			: null;

		final var itemType = item.getItemType();

		final Map<Integer, FixedField> fixedFields = new HashMap<>(item.getFixedFields() != null ? item.getFixedFields() : Map.of());

		if (itemType != null) {
			fixedFields.put(61, FixedField.builder().value(itemType).build());
		}

		final var status = isEmpty(item.getStatusCode())
			? null
			: Status.builder()
				.code(item.getStatusCode())
				.duedate(formattedDueDate)
				.build();

		return SierraItem.builder()
			.id(item.getId())
			.barcode(item.getBarcode())
			.callNumber(item.getCallNumber())
			.status(status)
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

	public String generateLocalItemId() {
		final int lowerBound = 1000000;
		final int upperBound = 8000000;

		return Integer.toString((int) (Math.random() * (upperBound - lowerBound)) + lowerBound);
	}

	public void verifyUpdateItemRequestMade(String expectedItemId) {
		mockServer.verify(putItem(expectedItemId));
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

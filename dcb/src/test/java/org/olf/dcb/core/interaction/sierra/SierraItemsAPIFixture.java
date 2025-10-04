package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockserver.model.Delay.delay;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.verify.VerificationTimes.once;
import static org.olf.dcb.utils.CollectionUtils.mapList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.VerificationTimes;
import org.olf.dcb.test.TestResourceLoaderProvider;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.CheckoutResultSet;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.LinkResult;
import services.k_int.interaction.sierra.items.Location;
import services.k_int.interaction.sierra.items.SierraItem;
import services.k_int.interaction.sierra.items.Status;

@Slf4j
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

	public void mockUpdateItem(String itemId) {
		mockServer
			.when(putItem(itemId))
			.respond(sierraMockServerResponses.noContent());
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
		itemsForBibId(bibId, items, 0);
	}

	public void itemsForBibId(String bibId, List<org.olf.dcb.core.interaction.sierra.SierraItem> items, int millisecondDelay) {
		mockServer.clear(getItemsForBib(bibId));

		mockServer.when(getItemsForBib(bibId))
			.respond(sierraMockServerResponses.jsonSuccess(json(
				ItemResultSet.builder()
					.start(0)
					.total(items.size())
					.entries(mapList(items, SierraItemsAPIFixture::mapItem))
					.build()), delay(MILLISECONDS, millisecondDelay)));
	}

	public String checkoutsForItem(String itemId) {
		final var request = getItemCheckouts(itemId);

		mockServer.clear(request);

		mockServer.when(request)
			.respond(sierraMockServerResponses
				.jsonSuccess("items/sierra-get-item-checkouts-success.json"));

		// return needs to align with id of the json response
		// E.g.,		"id": "https://catalog-test.wustl.edu/iii/sierra-api/v6/patrons/checkouts/1811242",
		return "1811242";
	}

	public void checkoutsForItemWithMultiplePatronEntries(String itemId) {
		final var request = getItemCheckouts(itemId);

		mockServer.clear(request);

		mockServer.when(request)
			.respond(sierraMockServerResponses
				.jsonSuccess("items/sierra-get-item-checkouts-multiple-patron-match.json"));
	}

	public void checkoutsForItemWithNoPatronEntries(String itemId) {
		final var request = getItemCheckouts(itemId);

		mockServer.clear(request);

		mockServer.when(request)
			.respond(sierraMockServerResponses.jsonSuccess(json(
				CheckoutResultSet.builder()
					.entries(List.of())
					.build()
			)));
	}

	public void checkoutsForItemWithNoRecordsFound(String itemId) {
		final var request = getItemCheckouts(itemId);

		mockServer.clear(request);

		mockServer.when(request)
			.respond(sierraMockServerResponses.noRecordsFound());
	}

	public void zeroItemsResponseForBibId(String bibId) {
		mockServer.when(getItemsForBib(bibId))
			.respond(sierraMockServerResponses.noRecordsFound());
	}

	public void errorResponseForBibId(String bibId) {
		mockServer.when(getItemsForBib(bibId))
			.respond(sierraMockServerResponses.badRequestError());
	}

	public void jsonErrorResponseForCreateItem() {
		mockServer.clear(sierraMockServerRequests.post());

		mockServer.when(sierraMockServerRequests.post())
			.respond(sierraMockServerResponses.badRequestError());
	}

	public void successResponseForCreateItem(Integer bibId, String locationCode,
		String barcode, String itemId) {

		final var body = ItemPatch.builder()
			.bibIds(List.of(bibId))
			.location(locationCode)
			.barcodes(List.of(barcode))
			.build();

		mockServer
			.when(sierraMockServerRequests.post()
				.withBody(json(body)))
			.respond(sierraMockServerResponses.jsonSuccess(json(
				LinkResult.builder()
					.link("https://sandbox.iii.com/iii/sierra-api/v6/items/" + itemId)
					.build()
			)));
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

	public void mockDeleteItem(String itemId) {
		deleteItem(itemId, sierraMockServerResponses.noContent());
	}

	private void deleteItem(String itemId, HttpResponse response) {
		mockServer.clear(deleteItemRecord(itemId));

		mockServer.when(deleteItemRecord(itemId))
			.respond(response);
	}

	private HttpRequest deleteItemRecord(String itemId) {
		return sierraMockServerRequests.delete("/" + itemId);
	}

	private RequestDefinition putItem(String itemId) {
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
		verifyUpdateItemRequest(expectedItemId, once());
	}

	private void verifyUpdateItemRequest(String expectedItemId, VerificationTimes times) {
		final var updateRequest = putItem(expectedItemId);

		log.info("Update item requests recorded: {}",
			asList(mockServer.retrieveRecordedRequests(updateRequest)));

		mockServer.verify(updateRequest, times);
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

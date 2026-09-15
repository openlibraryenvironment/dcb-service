package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;
import org.mockserver.verify.VerificationTimes;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.interaction.CheckInItemCommand;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The exact requests Alma receives for each operation DCB performs, checked at the HTTP boundary.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class AlmaHostLmsClientContractTests {
	private static final String HOST_LMS_CODE = "alma-contract";
	private static final String BASE_URL = "https://alma-contract-tests.com";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockServerClient mockServerClient;
	private HostLmsClient client;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		hostLmsFixture.deleteAll();
		hostLmsFixture.createAlmaHostLms(HOST_LMS_CODE, BASE_URL);
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();
		client = hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	@Test
	void shouldPlaceASupplierHoldForCollectionAtTheSharingLibrary() {
		mockServerClient.when(request()
				.withMethod("GET")
				.withPath("/almaws/v1/users/P1/requests")
				.withQueryStringParameter("request_type", "HOLD"))
			.respond(okJson(Map.of("total_record_count", 0)));

		mockServerClient.when(request()
				.withMethod("POST")
				.withPath("/almaws/v1/users/P1/requests")
				.withQueryStringParameter("item_pid", "I1"))
			.respond(okJson(Map.of("request_id", "R1", "request_status", "NOT_STARTED")));

		final var localRequest = singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId("P1")
				.localItemId("I1")
				.pickupLocation(Location.builder().localId("PICKUP-LIB").build())
				.activeWorkflow("RET-STD")
				.build()));

		assertThat(localRequest.getLocalId(), is("R1"));
		assertThat(localRequest.getLocalStatus(), is("CONFIRMED"));

		mockServerClient.verify(request()
			.withMethod("POST")
			.withPath("/almaws/v1/users/P1/requests")
			.withQueryStringParameter("item_pid", "I1")
			.withBody(json("""
				{"request_type": "HOLD", "pickup_location_type": "LIBRARY", "pickup_location_library": "DCB-SHARING"}
				""", MatchType.ONLY_MATCHING_FIELDS)));
	}

	@Test
	void shouldAdoptAHoldThePatronAlreadyHasOnTheItemInsteadOfPlacingAnother() {
		mockServerClient.when(request()
				.withMethod("GET")
				.withPath("/almaws/v1/users/P2/requests")
				.withQueryStringParameter("request_type", "HOLD")
				.withQueryStringParameter("limit", "100")
				.withQueryStringParameter("offset", "0"))
			.respond(okJson(Map.of(
				"total_record_count", 2,
				"user_request", List.of(
					Map.of("request_id", "R-OTHER", "request_status", "NOT_STARTED", "item_id", "I9"),
					Map.of("request_id", "R-EXISTING", "request_status", "ON_HOLD_SHELF", "item_id", "I2")))));

		final var localRequest = singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId("P2")
				.localItemId("I2")
				.pickupLocation(Location.builder().localId("PICKUP-LIB").build())
				.activeWorkflow("RET-STD")
				.build()));

		assertThat(localRequest.getLocalId(), is("R-EXISTING"));
		assertThat(localRequest.getLocalStatus(), is("READY"));

		mockServerClient.verify(request()
			.withMethod("POST")
			.withPath("/almaws/v1/users/P2/requests"), VerificationTimes.never());
	}

	@Test
	void shouldTrackARequestOnTheHoldShelfAsReady() {
		mockServerClient.when(request()
				.withMethod("GET")
				.withPath("/almaws/v1/users/P1/requests/R1"))
			.respond(okJson(Map.of("request_id", "R1", "request_status", "ON_HOLD_SHELF")));

		final var request = singleValueFrom(client.getRequest(HostLmsRequest.builder()
			.localId("R1")
			.localPatronId("P1")
			.build()));

		assertThat(request.getStatus(), is("READY"));
	}

	@Test
	void shouldCancelARequestWithoutNotifyingThePatron() {
		mockServerClient.when(request()
				.withMethod("DELETE")
				.withPath("/almaws/v1/users/P1/requests/R1"))
			.respond(response().withStatusCode(204));

		singleValueFrom(client.cancelHoldRequest(CancelHoldRequestParameters.builder()
			.patronId("P1")
			.localRequestId("R1")
			.build()));

		mockServerClient.verify(request()
			.withMethod("DELETE")
			.withPath("/almaws/v1/users/P1/requests/R1")
			.withQueryStringParameter("notify_user", "false"));
	}

	@Test
	void shouldCheckOutAtTheItemsOwnLibraryAndTheDefaultDesk() {
		mockServerClient.when(request()
				.withMethod("GET")
				.withPath("/almaws/v1/items")
				.withQueryStringParameter("item_barcode", "BC1"))
			.respond(okJson(Map.of("item_data", Map.of("pid", "I1", "library", Map.of("value", "MAIN")))));

		mockServerClient.when(request()
				.withMethod("POST")
				.withPath("/almaws/v1/users/P1/loans")
				.withQueryStringParameter("item_pid", "I1"))
			.respond(okJson(Map.of("loan_id", "L1")));

		singleValueFrom(client.checkOutItemToPatron(CheckoutItemCommand.builder()
			.patronId("P1")
			.itemId("I1")
			.itemBarcode("BC1")
			.localRequestId("R1")
			.build()));

		mockServerClient.verify(request()
			.withMethod("POST")
			.withPath("/almaws/v1/users/P1/loans")
			.withQueryStringParameter("item_pid", "I1")
			.withBody(json("""
				{"library": {"value": "MAIN"}, "circ_desk": {"value": "DEFAULT_CIRC_DESK"}}
				""", MatchType.ONLY_MATCHING_FIELDS)));
	}

	@Test
	void shouldCheckInAVirtualItemAtTheVirtualItemLibrary() {
		mockServerClient.when(request()
				.withMethod("POST")
				.withPath("/almaws/v1/bibs/B1/holdings/H1/items/I1"))
			.respond(okJson(Map.of("item_data", Map.of("pid", "I1"))));

		singleValueFrom(client.checkInItem(CheckInItemCommand.builder()
			.bibId("B1")
			.holdingId("H1")
			.itemId("I1")
			.build()));

		mockServerClient.verify(request()
			.withMethod("POST")
			.withPath("/almaws/v1/bibs/B1/holdings/H1/items/I1")
			.withQueryStringParameter("op", "scan")
			.withQueryStringParameter("library", "DCB-VIRTUAL")
			.withQueryStringParameter("circ_desk", "DEFAULT_CIRC_DESK"));
	}

	@Test
	void shouldListEveryItemOnABibInOnePagedRequest() {
		mockServerClient.when(request()
				.withMethod("GET")
				.withPath("/almaws/v1/bibs/B1/holdings/ALL/items"))
			.respond(okJson(Map.of(
				"total_record_count", 2,
				"item", List.of(almaItem("I1"), almaItem("I2")))));

		mockServerClient.when(request()
				.withMethod("GET")
				.withPath("/almaws/v1/bibs/B1/holdings/H1/items/.*/requests"))
			.respond(okJson(Map.of("total_record_count", 0)));

		final var items = singleValueFrom(client.getItems(BibRecord.builder()
			.sourceRecordId("B1")
			.build()));

		assertThat(items.stream().map(item -> item.getLocalId()).toList(), contains("I1", "I2"));

		mockServerClient.verify(request()
			.withMethod("GET")
			.withPath("/almaws/v1/bibs/B1/holdings/ALL/items")
			.withQueryStringParameter("limit", "100")
			.withQueryStringParameter("offset", "0")
			.withQueryStringParameter("expand", "due_date"));
	}

	private static Map<String, Object> almaItem(String pid) {
		return Map.of(
			"bib_data", Map.of("mms_id", "B1"),
			"holding_data", Map.of("holding_id", "H1"),
			"item_data", Map.of(
				"pid", pid,
				"barcode", "BC-" + pid,
				"base_status", Map.of("value", "1"),
				"physical_material_type", Map.of("value", "BOOK"),
				"library", Map.of("value", "MAIN"),
				"location", Map.of("value", "STACKS")));
	}
}

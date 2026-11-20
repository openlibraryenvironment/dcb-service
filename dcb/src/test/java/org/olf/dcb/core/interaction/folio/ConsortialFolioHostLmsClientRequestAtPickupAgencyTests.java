package org.olf.dcb.core.interaction.folio;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.CannotPlaceRequestProblem;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.SupplierRefusedRequestProblem;
import org.olf.dcb.core.model.Location;

import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_PLACED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;
import static org.olf.dcb.test.matchers.interaction.ProblemMatchers.hasDetail;
import static services.k_int.utils.UUIDUtils.dnsUUID;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientRequestAtPickupAgencyTests {
	private static final String HOST_LMS_CODE = "folio-pickup-request-tests";
	private static final String PICKUP_HOST_LMS_CODE = "pickup-host-lms";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private MockFolioFixture mockFolioFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciIsInQiOiJzdHJpbmctY29ud";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		hostLmsFixture.createFolioHostLms(PICKUP_HOST_LMS_CODE,
			"https://fake-pickup-folio", "", "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);
	}

	@Test
	void shouldNameAPickupRequestPolicyRefusalWithoutCallingItASupplierRefusal() {
		// Arrange: the pickup tenant's own request policy refuses the virtual item's Hold
		mockFolioFixture.mockCreateTransaction(response()
			.withStatusCode(422)
			.withBody(json(ConsortialFolioHostLmsClient.ValidationError.builder()
				.errors(List.of(ConsortialFolioHostLmsClient.ValidationError.Error.builder()
					.message("[422 Unprocessable Entity] during [POST] to [http://circulation/requests] "
						+ "[CirculationClient#createRequest(CirculationRequest)]: [{\n  \"errors\" : [ {\n"
						+ "    \"message\" : \"Hold requests are not allowed for this patron and item combination\",\n"
						+ "    \"parameters\" : [ {\n      \"key\" : \"requestType\",\n      \"value\" : \"Hold\"\n    } ],\n"
						+ "    \"code\" : \"REQUEST_NOT_ALLOWED_FOR_PATRON_ITEM_COMBINATION\"\n  } ]\n}]")
					.type("-1")
					.code("VALIDATION_ERROR")
					.build()))
				.build())));

		referenceValueMappingFixture.defineMapping("DCB", "ItemType", "canonical", HOST_LMS_CODE, "ItemType", "book");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var problem = assertThrows(CannotPlaceRequestProblem.class,
			() -> singleValueFrom(client.placeHoldRequestAtPickupAgency(
				PlaceHoldRequestParameters.builder()
					.title("title")
					.supplyingLocalItemBarcode("supplying-item-barcode")
					.canonicalItemType("canonical")
					.supplyingLocalItemId("supplying-item-id")
					.supplyingAgencyCode("supplying-agency")
					.localPatronId(UUID.randomUUID().toString())
					.localPatronBarcode("[67129553]")
					.localPatronType("undergrad")
					.pickupLocation(Location.builder().localId(UUID.randomUUID().toString()).build())
					.build())));

		// Assert
		assertThat(problem, not(instanceOf(SupplierRefusedRequestProblem.class)));
		assertThat(problem, not(instanceOf(SupplierRefusedRequestProblem.class)));
		assertThat(problem, hasDetail(
			"%s's request policy does not allow a Hold request for patron group \"undergrad\" on this item"
				.formatted(HOST_LMS_CODE)));
		assertThat(problem, hasResponseStatusCode(422));
	}

	@Test
	void shouldPlacePickupRequestSuccessfully() {
		// Arrange
		final var patronId = UUID.randomUUID().toString();
		// we expect the barcode to be a toString list
		final var patronBarcode =  "[67129553]";
		final var supplyingAgencyCode = "supplying-agency";
		final var supplyingLocalItemId = "supplying-item-id";
		final var itemId = dnsUUID(supplyingAgencyCode + ":" + supplyingLocalItemId).toString();
		final var pickupLocation = Location.builder().localId(UUID.randomUUID().toString()).build();

		mockFolioFixture.mockCreateTransaction(CreateTransactionResponse.builder()
			.status("CREATED")
			.build());

		referenceValueMappingFixture.defineMapping("DCB", "ItemType", "canonical", HOST_LMS_CODE, "ItemType", "book");

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeMapping(HOST_LMS_CODE, "book", "canonical");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var placedRequest = singleValueFrom(client.placeHoldRequestAtPickupAgency(
			PlaceHoldRequestParameters.builder()
				.title("title")
				.supplyingLocalItemBarcode("supplying-item-barcode")
				.canonicalItemType("canonical")
				.supplyingLocalItemId(supplyingLocalItemId)
				.supplyingAgencyCode(supplyingAgencyCode)
				.localPatronId(patronId)
				.localPatronBarcode(patronBarcode)
				.localPatronType("undergrad")
				.pickupLocation(pickupLocation)
				.build()));

		// Assert
		assertThat("Placed request is not null", placedRequest, is(notNullValue()));
		assertThat("Should be transaction ID but cannot be explicit",
			placedRequest, hasLocalId());

		assertThat(placedRequest, hasLocalStatus(HOLD_PLACED));

		mockFolioFixture.verifyCreateTransaction(CreateTransactionRequest.builder()
			.role("PICKUP")
			.item(CreateTransactionRequest.Item.builder()
				.id(itemId)
				.title("title")
				.barcode("supplying-item-barcode")
				.materialType("book")
				.build())
			.patron(CreateTransactionRequest.Patron.builder()
				.id(patronId)
				.barcode("67129553")
				.group("undergrad")
				.build())
			.pickup(CreateTransactionRequest.Pickup.builder()
				.servicePointId(pickupLocation.getLocalId())
				.build())
			.build());
	}
}

package org.olf.dcb.core.interaction.folio;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_PLACED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
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

package org.olf.dcb.core.interaction.folio;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.shared.MissingParameterException;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_PLACED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static services.k_int.utils.UUIDUtils.dnsUUID;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientRequestAtBorrowingAgencyTests {
	private static final String HOST_LMS_CODE = "folio-borrowing-request-tests";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private MockFolioFixture mockFolioFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);
	}

	
	@Test
	void shouldPlaceRequestSuccessfully() {
		// Arrange
		final var patronId = UUID.randomUUID().toString();
		// we expect the barcode to be a toString list
		final var patronBarcode =  "[67129553]";
		final var pickupLocationCode = UUID.randomUUID().toString();
		final var supplyingAgencyCode = "supplying-agency";
		final var supplyingLocalItemId = "supplying-item-id";
		final var itemId = dnsUUID(supplyingAgencyCode + ":" + supplyingLocalItemId).toString();

		mockFolioFixture.mockCreateTransaction(CreateTransactionResponse.builder()
			.status("CREATED")
			.build());

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeMapping(HOST_LMS_CODE,
			"book", "canonical");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var placedRequest = singleValueFrom(client.placeHoldRequestAtBorrowingAgency(
				PlaceHoldRequestParameters.builder()
					.title("title")
					.supplyingLocalItemBarcode("supplying-item-barcode")
					.canonicalItemType("canonical")
					.supplyingLocalItemId(supplyingLocalItemId)
					.supplyingAgencyCode(supplyingAgencyCode)
					.localPatronId(patronId)
					.localPatronBarcode(patronBarcode)
					.pickupLocation(pickupLocationCode)
					.build()));

		// Assert
		assertThat("Placed request is not null", placedRequest, is(notNullValue()));
		assertThat("Should be transaction ID but cannot be explicit",
			placedRequest, hasLocalId());

		assertThat(placedRequest, hasLocalStatus(HOLD_PLACED));

		mockFolioFixture.verifyCreateTransaction(CreateTransactionRequest.builder()
			.role("BORROWING-PICKUP")
			.item(CreateTransactionRequest.Item.builder()
				.id(itemId)
				.title("title")
				.barcode("supplying-item-barcode")
				.materialType("book")
				.lendingLibraryCode("supplying-agency")
				.build())
			.patron(CreateTransactionRequest.Patron.builder()
				.id(patronId)
				.barcode("67129553")
				.build())
			.pickup(CreateTransactionRequest.Pickup.builder()
				.servicePointId(pickupLocationCode)
				.build())
			.build());
	}

	@Test
	void shouldFailWhenAnyOfTheExtendedPlaceHoldRequestParametersAreNull() {
		// Arrange
		final var patronId = UUID.randomUUID().toString();
		final var patronBarcode = "67129553";
		final var pickupLocationCode = UUID.randomUUID().toString();
		final var supplyingAgencyCode = "supplying-agency";
		final var supplyingLocalItemId = "supplying-item-id";

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var exception = assertThrows(MissingParameterException.class,
			() -> singleValueFrom(client.placeHoldRequestAtBorrowingAgency(
				PlaceHoldRequestParameters.builder()
					.title("title")
					.canonicalItemType("canonical")
					.supplyingLocalItemId(supplyingLocalItemId)
					.supplyingLocalItemBarcode(null)
					.supplyingAgencyCode(supplyingAgencyCode)
					.localPatronId(patronId)
					.localPatronBarcode(patronBarcode)
					.pickupLocation(pickupLocationCode)
					.build())));

		// Assert
		assertThat(exception, hasMessage("Supplying local item barcode is missing."));
	}
}

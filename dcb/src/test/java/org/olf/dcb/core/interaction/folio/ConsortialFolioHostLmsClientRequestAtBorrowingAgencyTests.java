package org.olf.dcb.core.interaction.folio;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_PLACED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasRawLocalStatus;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static services.k_int.utils.UUIDUtils.dnsUUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.shared.MissingParameterException;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientRequestAtBorrowingAgencyTests {
	private static final String HOST_LMS_CODE = "folio-borrowing-request-tests";
	private static final String PICKUP_HOST_LMS_CODE = "pickup-host-lms";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockFolioFixture mockFolioFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		hostLmsFixture.createFolioHostLms(PICKUP_HOST_LMS_CODE,
			"https://fake-pickup-folio", "", "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);
	}

	
	@Test
	void shouldPlaceBorrowingPickupRequestSuccessfully() {
		// Arrange
		final var patronId = randomUUID().toString();
		// we expect the barcode to be a toString list
		final var patronBarcode =  "[67129553]";
		final var pickupLocation = Location.builder().localId(randomUUID().toString()).build();
		final var supplyingAgencyCode = "supplying-agency";
		final var supplyingLocalItemId = "supplying-item-id";
		final var itemId = dnsUUID(supplyingAgencyCode + ":" + supplyingLocalItemId).toString();

		mockFolioFixture.mockCreateTransaction(CreateTransactionResponse.builder()
			.status("CREATED")
			.build());

    referenceValueMappingFixture.defineMapping("DCB", "ItemType", "canonical", HOST_LMS_CODE, "ItemType", "book");

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeMapping(HOST_LMS_CODE, "book", "canonical");

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
					.pickupLocation(pickupLocation)
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
				.servicePointId(pickupLocation.getLocalId())
				.build())
			.build());
	}

	@Test
	void shouldPlaceBorrowingRequestSuccessfully() {
		// Arrange
		final var patronId = randomUUID().toString();
		// we expect the barcode to be a toString list
		final var patronBarcode =  "[67129553]";
		final var supplyingAgencyCode = "supplying-agency";
		final var supplyingLocalItemId = "supplying-item-id";
		final var itemId = dnsUUID(supplyingAgencyCode + ":" + supplyingLocalItemId).toString();

		mockFolioFixture.mockCreateTransaction(CreateTransactionResponse.builder()
			.status("CREATED")
			.build());

		referenceValueMappingFixture.defineMapping("DCB", "ItemType", "canonical", HOST_LMS_CODE, "ItemType", "book");

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeMapping(HOST_LMS_CODE, "book", "canonical");

		final var pickupAgency = definePickupAgency();
		final var pickupLibrary = definePickupLibrary();
		final var pickupLocation = definePickupLocation();

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
				.pickupAgency(pickupAgency)
				.pickupLocation(pickupLocation)
				.pickupLibrary(pickupLibrary)
				.activeWorkflow("RET-PUA")
				.build()));

		// Assert
		assertThat("Placed request is not null", placedRequest, is(notNullValue()));
		assertThat("Should be transaction ID but cannot be explicit",
			placedRequest, hasLocalId());

		assertThat(placedRequest, hasLocalStatus(HOLD_PLACED));

		mockFolioFixture.verifyCreateTransaction(CreateTransactionRequest.builder()
			.role("BORROWER")
			.item(CreateTransactionRequest.Item.builder()
				.id(itemId)
				.title("title")
				.barcode("supplying-item-barcode")
				.materialType("book")
				.build())
			.patron(CreateTransactionRequest.Patron.builder()
				.id(patronId)
				.barcode("67129553")
				.build())
			.pickup(CreateTransactionRequest.Pickup.builder()
				.servicePointId(dnsUUID("FolioServicePoint:" + pickupAgency.getCode()).toString())
				.servicePointName("PrintLabel")
				.libraryCode("LibAbbrName")
				.build())
			.build());
	}

	@Test
	void shouldPlaceLocalAgencyRequestSuccessfully() {
		// Arrange
		final var patronId = randomUUID().toString();

		final var patronBarcode =  "83726524";
		final var pickupLocation = Location.builder().localId(randomUUID().toString()).build();
		final var itemId = randomUUID().toString();
		final var itemBarcode = "72646456";

		mockFolioFixture.mockCreateTransaction(CreateTransactionResponse.builder()
			.status("CREATED")
			.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var placedRequest = singleValueFrom(client.placeHoldRequestAtLocalAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId(patronId)
				// we expect the barcode to be a toString list
				.localPatronBarcode("[%s]".formatted(patronBarcode))
				.supplyingLocalItemId(itemId)
				.supplyingLocalItemBarcode(itemBarcode)
				.pickupLocation(pickupLocation)
				.build()));

		// Assert
		assertThat("Placed request is not null", placedRequest, is(notNullValue()));

		assertThat("Should be transaction ID but cannot check as generated internally",
			placedRequest, hasLocalId());

		assertThat(placedRequest, allOf(
			hasLocalStatus(HOLD_PLACED),
			hasRawLocalStatus("CREATED")
		));

		mockFolioFixture.verifyCreateTransaction(CreateTransactionRequest.builder()
			.role("BORROWING-PICKUP")
			.selfBorrowing(true)
			.item(CreateTransactionRequest.Item.builder()
				.id(itemId)
				.barcode(itemBarcode)
				.build())
			.patron(CreateTransactionRequest.Patron.builder()
				.id(patronId)
				.barcode(patronBarcode)
				.build())
			.pickup(CreateTransactionRequest.Pickup.builder()
				.servicePointId(pickupLocation.getLocalId())
				.build())
			.build());
	}

	@ParameterizedTest
	@NullAndEmptySource
	void shouldFailWhenAnyOfTheExtendedPlaceHoldRequestParametersAreNullOrEmpty(String parameterValue) {
		// Arrange
		final var patronId = randomUUID().toString();
		final var patronBarcode = "67129553";
		final var pickupLocationCode = randomUUID().toString();
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
					.supplyingLocalItemBarcode(parameterValue)
					.supplyingAgencyCode(supplyingAgencyCode)
					.localPatronId(patronId)
					.localPatronBarcode(patronBarcode)
					.pickupLocationCode(pickupLocationCode)
					.build())));

		// Assert
		assertThat(exception, hasMessage("Supplying local item barcode is missing."));
	}

	private DataAgency definePickupAgency() {
		return agencyFixture.defineAgency("pickup-agency",
			"Pickup Agency", hostLmsFixture.findByCode(PICKUP_HOST_LMS_CODE));
	}

	private Library definePickupLibrary() {
		return Library.builder()
			.abbreviatedName("LibAbbrName")
			.build();
	}

	private Location definePickupLocation() {
		return Location.builder()
			.printLabel("PrintLabel")
			.build();

	}
}

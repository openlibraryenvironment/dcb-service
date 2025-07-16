package org.olf.dcb.core.interaction.polaris;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.LibraryHold;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronCirculationBlocksResult;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronRegistrationCreateResult;
import org.olf.dcb.core.interaction.polaris.exceptions.FindVirtualPatronException;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;
import org.olf.dcb.test.matchers.HostLmsRequestMatchers;
import org.olf.dcb.test.matchers.ItemMatchers;
import org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers;
import org.zalando.problem.ThrowableProblem;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.List;
import java.util.UUID;

import static java.lang.Long.parseLong;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState.AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsRequest.*;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.ERR0210;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.InformationMessage;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasRawStatus;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ItemMatchers.*;
import static org.olf.dcb.test.matchers.ItemMatchers.isNotDeleted;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.*;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.ThrowableMatchers.messageContains;
import static org.olf.dcb.test.matchers.ThrowableProblemMatchers.hasParameters;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.*;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.*;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PolarisLmsClientTests {
	private static final String CATALOGUING_HOST_LMS_CODE = "polaris-cataloguing";
	private static final String CIRCULATING_HOST_LMS_CODE = "polaris-circulating";

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockServerClient mockServerClient;
	private MockPolarisFixture mockPolarisFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		final String BASE_URL = "https://polaris-hostlms-tests.com";
		final String KEY = "polaris-hostlms-test-key";
		final String SECRET = "polaris-hostlms-test-secret";
		final String DOMAIN = "TEST";

		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createPolarisHostLms(CATALOGUING_HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, DOMAIN, KEY, SECRET, "default-agency-code");

		hostLmsFixture.createPolarisHostLms(CIRCULATING_HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, DOMAIN, KEY, SECRET);

		mockPolarisFixture = new MockPolarisFixture("polaris-hostlms-tests.com",
			mockServerClient, testResourceLoaderProvider);
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();

		mockPolarisFixture.mockPapiStaffAuthentication();
		mockPolarisFixture.mockAppServicesStaffAuthentication();

		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldBeAbleToGetItemsByBibIdWithDefaultAgency() {
		// Arrange
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			CIRCULATING_HOST_LMS_CODE, 3, 3, "loanable-item");

		// mock will return null shelf location so a fall back to the default agency will be used
		agencyFixture.defineAgency("default-agency-code", "Default Agency",
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		final var bibId = "643425";

		mockPolarisFixture.mockGetItemsForBib(bibId);
		mockPolarisFixture.mockGetMaterialTypes();
		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var itemsList = singleValueFrom(client
			.getItems(BibRecord.builder()
				.sourceRecordId(bibId)
				.build()));

		// Assert
		assertThat(itemsList, hasSize(3));

		final var firstItem = itemsList.stream()
			.filter(item -> "3512742".equals(item.getLocalId()))
			.findFirst()
			.orElse(null);

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem, ItemMatchers.hasLocalId("3512742"));
		assertThat(firstItem, ItemMatchers.hasStatus(CHECKED_OUT));
		assertThat(firstItem, hasDueDate("2023-10-14T23:59:00Z"));
		assertThat(firstItem, hasNoLocation());
		assertThat(firstItem, hasBarcode("3430470102"));
		assertThat(firstItem, hasCallNumber("E Bellini Mario"));
		assertThat(firstItem, hasLocalBibId(bibId));
		assertThat(firstItem, hasLocalItemType("Book"));
		assertThat(firstItem, hasLocalItemTypeCode("3"));
		assertThat(firstItem, hasCanonicalItemType("loanable-item"));
		assertThat(firstItem, hasHoldCountOfZero());
		assertThat(firstItem, isNotSuppressed());
		assertThat(firstItem, isNotDeleted());
		assertThat(firstItem, hasAgencyCode("default-agency-code"));
		assertThat(firstItem, hasAgencyName("Default Agency"));
		assertThat(firstItem, hasHostLmsCode(CIRCULATING_HOST_LMS_CODE));
		assertThat(firstItem, hasOwningContext(CIRCULATING_HOST_LMS_CODE));
	}

	@Test
	void shouldBeAbleToGetItemsByBibIdWithShelfLocationMapping() {
		// Arrange
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			CIRCULATING_HOST_LMS_CODE, 3, 3, "loanable-item");

		agencyFixture.defineAgency("mapped-agency-code", "Mapped Agency",
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		// Note: 'Bestseller' is the returned shelf location from mock mockGetItemsForBibWithShelfLocations
		// referenceValueMappingFixture.defineLocationToAgencyMapping(
		// 	CIRCULATING_HOST_LMS_CODE, "Bestseller", "mapped-agency-code");

		// Note - (II 25th Feb 2025) We should be mapping locationID to agency code and NOT shelving location.
		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CIRCULATING_HOST_LMS_CODE, "15", "mapped-agency-code");

		final var bibId = "643425";

		mockPolarisFixture.mockGetItemsForBibWithShelfLocations(bibId);
		mockPolarisFixture.mockGetMaterialTypes();
		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var client = hostLmsFixture.createClient(CIRCULATING_HOST_LMS_CODE);

		final var itemsList = singleValueFrom(client
			.getItems(BibRecord.builder()
				.sourceRecordId(bibId)
				.build()));

		// Assert
		assertThat(itemsList, hasSize(3));

		final var firstItem = itemsList.stream()
			.filter(item -> "3512742".equals(item.getLocalId()))
			.findFirst()
			.orElse(null);

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem, ItemMatchers.hasLocalId("3512742"));
		assertThat(firstItem, ItemMatchers.hasStatus(CHECKED_OUT));
		assertThat(firstItem, hasDueDate("2023-10-14T23:59:00Z"));
		assertThat(firstItem, hasLocation("Bestseller", "15"));
		assertThat(firstItem, hasBarcode("3430470102"));
		assertThat(firstItem, hasCallNumber("E Bellini Mario"));
		assertThat(firstItem, hasLocalBibId(bibId));
		assertThat(firstItem, hasLocalItemType("Book"));
		assertThat(firstItem, hasLocalItemTypeCode("3"));
		assertThat(firstItem, hasCanonicalItemType("loanable-item"));
		assertThat(firstItem, hasHoldCountOfZero());
		assertThat(firstItem, isNotSuppressed());
		assertThat(firstItem, isNotDeleted());
		assertThat(firstItem, hasAgencyCode("mapped-agency-code"));
		assertThat(firstItem, hasAgencyName("Mapped Agency"));
		assertThat(firstItem, hasHostLmsCode(CIRCULATING_HOST_LMS_CODE));
		assertThat(firstItem, hasOwningContext(CIRCULATING_HOST_LMS_CODE));
	}

	@Test
	void shouldBeAbleToGetItemsByBibIdWithNoAgency() {
		// Arrange
		final var bibId = "643425";

		mockPolarisFixture.mockGetItemsForBib(bibId);
		mockPolarisFixture.mockGetMaterialTypes();
		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var client = hostLmsFixture.createClient(CIRCULATING_HOST_LMS_CODE);

		final var itemsList = singleValueFrom(client
			.getItems(BibRecord.builder()
				.sourceRecordId(bibId)
				.build()));

		// Assert
		assertThat(itemsList, hasSize(3));

		final var firstItem = itemsList.stream()
			.filter(item -> "3512742".equals(item.getLocalId()))
			.findFirst()
			.orElse(null);

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem, ItemMatchers.hasLocalId("3512742"));
		assertThat(firstItem, ItemMatchers.hasStatus(CHECKED_OUT));
		assertThat(firstItem, hasDueDate("2023-10-14T23:59:00Z"));
		assertThat(firstItem, hasNoLocation());
		assertThat(firstItem, hasBarcode("3430470102"));
		assertThat(firstItem, hasCallNumber("E Bellini Mario"));
		assertThat(firstItem, hasLocalBibId(bibId));
		assertThat(firstItem, hasLocalItemType("Book"));
		assertThat(firstItem, hasLocalItemTypeCode("3"));
		// Note: if there is no agency we cannot use the owning context to get the canonical item type
		assertThat(firstItem, hasCanonicalItemType("UNKNOWN - No mapping found"));
		assertThat(firstItem, hasHoldCountOfZero());
		assertThat(firstItem, isNotSuppressed());
		assertThat(firstItem, isNotDeleted());
		assertThat(firstItem, hasNoAgency());
		assertThat(firstItem, hasNoHostLmsCode());
	}

	@Test
	void shouldBeAbleToAuthenticatePatron() {
		// Arrange
		mockPolarisFixture.mockPatronAuthentication();
		mockPolarisFixture.mockGetPatronByBarcode("3100222227777");

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var patron = singleValueFrom(client.patronAuth(
			"BASIC/BARCODE+PASSWORD", "3100222227777", "password123"));

		// Assert
		assertThat(patron, is(notNullValue()));
		assertThat(patron.getLocalId(), is(List.of("1255192")));
		assertThat(patron.getLocalPatronType(), is("5"));
		assertThat(patron.getLocalBarcodes(), is(List.of("0088888888")));
		assertThat(patron.getLocalHomeLibraryCode(), is("39"));
		// not returned
		assertThat(patron.getLocalNames(), is(nullValue()));
		assertThat(patron.getUniqueIds(), is(nullValue()));
	}

	@Test
	void shouldBeAbleToFindVirtualPatron() {
		// Arrange
		final var localId = "1255217";
		final var barcode = "0077777777";
		final var organisationId = "39";
		final var patronCodeId = "3";

		mockPolarisFixture.mockPatronSearch(barcode);

		mockPolarisFixture.mockGetPatron(localId,
			ApplicationServicesClient.PatronData.builder()
				.patronID((Integer.parseInt(localId)))
				.patronCodeID(Integer.parseInt(patronCodeId))
				.barcode(barcode)
				.organizationID(Integer.parseInt(organisationId))
				.build());

		mockPolarisFixture.mockGetPatronCirculationBlocks(barcode,
			PatronCirculationBlocksResult.builder()
				.canPatronCirculate(true)
				.build());

		mockPolarisFixture.mockGetPatronBlocksSummary(localId);

		final var canonicalPatronType = "UNDERGRADUATE";

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(CATALOGUING_HOST_LMS_CODE,
			parseLong(patronCodeId), parseLong(patronCodeId), "DCB", canonicalPatronType);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var patron = org.olf.dcb.core.model.Patron.builder()
			.id(randomUUID())
			.patronIdentities(List.of(
				PatronIdentity.builder()
					.localId(localId)
					.localBarcode(barcodeAsSerialisedList(barcode))
					.resolvedAgency(DataAgency.builder()
						.code("known-agency")
						.build())
					.homeIdentity(true)
					.build()
			))
			.build();

		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, allOf(
			notNullValue(),
			hasLocalIds(localId),
			hasLocalPatronType(patronCodeId),
			hasCanonicalPatronType(canonicalPatronType),
			hasLocalBarcodes(barcode),
			hasHomeLibraryCode(organisationId),
			isNotBlocked()
		));

		// DCB appends a prefix to the barcode used in the generated field for the virtual patron
		mockPolarisFixture.verifyPatronSearch(barcode);
	}

	@Test
	void shouldTolerateNotFoundResponseFromPatronBlocksWhenFindingVirtualPatron() {
		// Arrange
		final var localId = "1255217";
		final var barcode = "0077777777";
		final var organisationId = "39";
		final var patronCodeId = "3";

		mockPolarisFixture.mockPatronSearch(barcode);

		mockPolarisFixture.mockGetPatron(localId,
			ApplicationServicesClient.PatronData.builder()
				.patronID((Integer.parseInt(localId)))
				.patronCodeID(Integer.parseInt(patronCodeId))
				.barcode(barcode)
				.organizationID(Integer.parseInt(organisationId))
				.build());

		mockPolarisFixture.mockGetPatronBlocksSummaryNotFoundResponse(localId);

		mockPolarisFixture.mockGetPatronCirculationBlocks(barcode,
			PatronCirculationBlocksResult.builder()
				.canPatronCirculate(true)
				.build());

		final var canonicalPatronType = "UNDERGRADUATE";

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(CATALOGUING_HOST_LMS_CODE,
			parseLong(patronCodeId), parseLong(patronCodeId), "DCB", canonicalPatronType);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var patron = org.olf.dcb.core.model.Patron.builder()
			.id(randomUUID())
			.patronIdentities(List.of(
				PatronIdentity.builder()
					.localId(localId)
					.localBarcode(barcodeAsSerialisedList(barcode))
					.resolvedAgency(DataAgency.builder()
						.code("known-agency")
						.build())
					.homeIdentity(true)
					.build()
			))
			.build();

		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, allOf(
			notNullValue(),
			hasLocalIds(localId),
			hasLocalPatronType(patronCodeId),
			hasCanonicalPatronType(canonicalPatronType),
			hasLocalBarcodes(barcode),
			hasHomeLibraryCode(organisationId),
			isNotBlocked()
		));
	}

	@Test
	void shouldFailToFindVirtualPatronWhenPatronBlocksReturnServerError() {
		// Arrange
		final var localId = "1255217";
		final var barcode = "0077777777";

		mockPolarisFixture.mockPatronSearch(barcode);

		mockPolarisFixture.mockGetPatron(localId,
			ApplicationServicesClient.PatronData.builder()
				.patronID((Integer.parseInt(localId)))
				.patronCodeID(7)
				.barcode(barcode)
				.organizationID(25)
				.build());

		mockPolarisFixture.mockGetPatronBlocksSummaryServerErrorResponse(localId);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var patron = org.olf.dcb.core.model.Patron.builder()
			.id(randomUUID())
			.patronIdentities(List.of(
				PatronIdentity.builder()
					.localId(localId)
					.localBarcode(barcodeAsSerialisedList(barcode))
					.resolvedAgency(DataAgency.builder()
						.code("known-agency")
						.build())
					.homeIdentity(true)
					.build()
			))
			.build();

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.findVirtualPatron(patron)));

		// Assert
		assertThat(problem, allOf(
			notNullValue(),
			hasMessage("Unable to retrieve patron blocks from polaris: Internal Server Error"),
			hasProperty("type", is(ERR0210))
		));
	}

	@Test
	void shouldFailToFindVirtualPatronWhenFindPatronReturnsPapiErrorCode() {
		// Arrange
		final var localBarcode = "0077777777";

		final int errorCode = -5;
		final var errorMessage = "Something went wrong";

		mockPolarisFixture.mockPatronSearchPapiError(localBarcode, errorCode, errorMessage);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var patron = org.olf.dcb.core.model.Patron.builder()
			.id(randomUUID())
			.patronIdentities(List.of(
				PatronIdentity.builder()
					.localId("1255193")
					.localBarcode("[0077777777]")
					.resolvedAgency(DataAgency.builder()
						.code("known-agency")
						.build())
					.homeIdentity(true)
					.build()
			))
			.build();

		final var exception = assertThrows(FindVirtualPatronException.class,
			() -> singleValueFrom(client.findVirtualPatron(patron)));

		// Assert
		assertThat(exception, allOf(
			notNullValue(),
			hasMessage("PAPIService returned [%d], with message: %s".formatted(errorCode, errorMessage))
		));
	}

	@Test
	void shouldBeAbleToPlaceRequestAtSupplyingAgency() {
		// Arrange
		final var itemId = "6737455";

		mockPolarisFixture.mockGetItem(itemId);
		mockPolarisFixture.mockGetBib("1106339");
		mockPolarisFixture.mockPlaceHold();
		mockPolarisFixture.mockListPatronLocalHolds();
		mockPolarisFixture.mockGetHold("3773060",
			LibraryHold.builder()
				.sysHoldStatus("In Processing")
				.itemRecordID(6737455)
				.itemBarcode("785574212")
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var localRequest = singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId("1")
				.localBibId(null)
				.localItemId(itemId)
				.pickupLocationCode("5324532")
				.note("DCB Testing PACDisplayNotes")
				.patronRequestId(UUID.randomUUID().toString())
				.build()
		));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId("3773060"),
			hasLocalStatus("In Processing"),
			hasRequestedItemId("6737455"),
			hasRequestedItemBarcode("785574212")
		));
	}

	@Test
	void shouldBeAbleToPlaceRequestAtPickupAgency() {
		// Arrange
		final var itemId = "3453465";
		final var pickupLocationLocalId = "5324532";
		final var pickupLocation = Location.builder().id(randomUUID()).localId(pickupLocationLocalId).build();

		mockPolarisFixture.mockGetItem(itemId);
		mockPolarisFixture.mockGetBib("1106339");
		mockPolarisFixture.mockPlaceHold();
		mockPolarisFixture.mockListPatronLocalHolds();
		mockPolarisFixture.mockGetHold("3773060",
			LibraryHold.builder()
				.sysHoldStatus("In Processing")
				.itemRecordID(3453465)
				.itemBarcode("45673567")
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var localRequest = singleValueFrom(client.placeHoldRequestAtPickupAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId("1")
				.localItemId(itemId)
				.pickupLocation(pickupLocation)
				.note("DCB Testing PACDisplayNotes")
				.patronRequestId(randomUUID().toString())
				.build()
		));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId("3773060"),
			hasLocalStatus("In Processing"),
			hasRequestedItemId("3453465"),
			hasRequestedItemBarcode("45673567")
		));
	}

	@Test
	void shouldFailToPlaceRequestAtSupplyingAgencyWhenMatchingHoldIsEmpty() {
		// Arrange
		final var itemId = "6737455";

		mockPolarisFixture.mockGetItem(itemId);
		mockPolarisFixture.mockGetBib("1106339");
		mockPolarisFixture.mockPlaceHold();
		mockPolarisFixture.mockEmptyListPatronLocalHolds();
		mockPolarisFixture.mockGetHold("3773060",
			LibraryHold.builder()
				.sysHoldStatus("In Processing")
				.itemRecordID(6737455)
				.itemBarcode("785574212")
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var exception = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localPatronId("1")
					.localBibId(null)
					.localItemId(itemId)
					.pickupLocationCode("5324532")
					.note("DCB Testing PACDisplayNotes")
					.patronRequestId(UUID.randomUUID().toString())
					.build()
			)));

		// Assert
		assertThat(exception, allOf(
			notNullValue(),
			messageContains("No holds to process for local patron id: 1")
		));
	}

	@Test
	void shouldFailToPlaceRequestAtSupplyingAgencyWhenCreatingTheHoldFails() {
		// Arrange
		final var itemId = "12345";

		mockPolarisFixture.mockGetItem(itemId);
		mockPolarisFixture.mockGetBib("1106339");
		mockPolarisFixture.mockPlaceHoldUnsuccessful();

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var exception = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localPatronId("1")
					.localBibId(null)
					.localItemId(itemId)
					.pickupLocationCode("5324532")
					.note("No special note")
					.patronRequestId(randomUUID().toString())
					.build()
			)));

		// Assert
		assertThat(exception, allOf(
			notNullValue(),
			hasMessage("Failed to handle Polaris.ApplicationServices API workflow: Serial Holds")
		));
	}

	@Test
	void shouldDetectRequestHasBeenConfirmed() {
		// Arrange
		final var localHoldId = "2977175";

		mockPolarisFixture.mockGetHold(localHoldId,
			LibraryHold.builder()
				.sysHoldStatus("Pending")
				.itemRecordID(6737455)
				.itemBarcode("785574212")
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localHoldId).build();

		final var request = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(request, allOf(
			notNullValue(),
			HostLmsRequestMatchers.hasLocalId(localHoldId),
			hasStatus(HOLD_CONFIRMED),
			HostLmsRequestMatchers.hasRequestedItemId("6737455"),
			HostLmsRequestMatchers.hasRequestedItemBarcode("785574212")
		));
	}

	@Test
	void shouldDetectRequestHasBeenCancelled() {
		// Arrange
		final var localHoldId = "2977175";

		mockPolarisFixture.mockGetHold(localHoldId,
			LibraryHold.builder()
				.sysHoldStatus("Cancelled")
				.itemRecordID(6737455)
				.itemBarcode("785574212")
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localHoldId).build();

		final var request = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(request, allOf(
			notNullValue(),
			HostLmsRequestMatchers.hasLocalId(localHoldId),
			hasStatus(HOLD_CANCELLED),
			HostLmsRequestMatchers.hasRequestedItemId("6737455"),
			HostLmsRequestMatchers.hasRequestedItemBarcode("785574212")
		));
	}

	@Test
	void shouldHandleHoldRequestNotFound() {
		// Arrange
		final var localHoldId = "2977175";

		mockPolarisFixture.mockGetHoldNotFound(localHoldId, PolarisError.builder()
			.errorCode(60028)
			.message("Hold Request with ID 6348612 not found.")
			.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localHoldId).build();

		final var request = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(request, allOf(
			notNullValue(),
			HostLmsRequestMatchers.hasLocalId(localHoldId),
			hasStatus(HOLD_MISSING)
		));
	}

	@Test
	void shouldDetectRequestIsReadyForPickup() {
		// Arrange
		final var localHoldId = "2977175";

		mockPolarisFixture.mockGetHold(localHoldId,
			LibraryHold.builder()
				.sysHoldStatus("Held")
				.itemRecordID(6737455)
				.itemBarcode("785574212")
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localHoldId).build();

		final var request = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(request, allOf(
			notNullValue(),
			HostLmsRequestMatchers.hasLocalId(localHoldId),
			hasStatus(HOLD_READY),
			hasRawStatus("Held"),
			HostLmsRequestMatchers.hasRequestedItemId("6737455"),
			HostLmsRequestMatchers.hasRequestedItemBarcode("785574212")
		));
	}

	@Test
	void shouldDetectRequestIsInTransit() {
		// Arrange
		final var localHoldId = "2977175";

		mockPolarisFixture.mockGetHold(localHoldId,
			LibraryHold.builder()
				.sysHoldStatus("Shipped")
				.itemRecordID(6737455)
				.itemBarcode("785574212")
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localHoldId).build();

		final var request = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(request, allOf(
			notNullValue(),
			HostLmsRequestMatchers.hasLocalId(localHoldId),
			hasStatus(HOLD_CONFIRMED),
			HostLmsRequestMatchers.hasRequestedItemId("6737455"),
			HostLmsRequestMatchers.hasRequestedItemBarcode("785574212")
		));
	}

	@Test
	void shouldBeAbleToCreateVirtualPatron() {
		// Arrange
		final var localItemId = "3512742";

		mockPolarisFixture.mockGetItem(localItemId);
		mockPolarisFixture.mockCreatePatron();
		mockPolarisFixture.mockGetPatronBlocksSummary("1255217");

		final var patron = Patron.builder()
			.uniqueIds(List.of("dcb_unique_Id"))
			.localPatronType("1")
			.localHomeLibraryCode("39")
			.localBarcodes(List.of("0088888888"))
			.localItemId(localItemId)
			.build();

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var response = singleValueFrom(client.createPatron(patron));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, is("1255217"));
	}

	@Test
	void shouldFailWhenCreatingVirtualPatronReturnsNonZeroErrorCode() {
		// Arrange
		final var localItemId = "3512742";

		mockPolarisFixture.mockGetItem(localItemId);
		mockPolarisFixture.mockCreatePatron(PatronRegistrationCreateResult.builder()
			.papiErrorCode(-3505)
			.errorMessage("Duplicate barcode")
			.build());

		final var patron = Patron.builder()
			.uniqueIds(List.of("dcb_unique_Id"))
			.localPatronType("1")
			.localHomeLibraryCode("39")
			.localBarcodes(List.of("0088888888"))
			.localItemId(localItemId)
			.build();

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.createPatron(patron)));

		// Assert
		assertThat(problem, allOf(
			notNullValue(),
			hasMessage("Unable to create virtual patron at polaris - error code: -3505: Duplicate barcode"),
			hasParameters(hasEntry(equalTo("patron"), is(notNullValue()))),
			hasParameters(hasEntry(equalTo("errorCode"), is(-3505))),
			hasParameters(hasEntry(equalTo("errorMessage"), is("Duplicate barcode")))
		));
	}

	@Test
	void shouldBeAbleToUpdateAnExistingPatron() {
		// Arrange
		final var localId = "1255193";
		final var barcode = "0077777777";
		final var organisationId = "39";

		final var newPatronCodeId = "7";

		mockPolarisFixture.mockGetPatronBarcode(localId, barcode);
		mockPolarisFixture.mockUpdatePatron(barcode);

		mockPolarisFixture.mockGetPatron(localId,
			ApplicationServicesClient.PatronData.builder()
				.patronID((Integer.parseInt(localId)))
				.patronCodeID(Integer.parseInt(newPatronCodeId))
				.barcode(barcode)
				.organizationID(Integer.parseInt(organisationId))
				.build());

		mockPolarisFixture.mockGetPatronCirculationBlocks(barcode,
			PatronCirculationBlocksResult.builder()
				.canPatronCirculate(true)
				.build());

		final var canonicalPatronType = "UNDERGRADUATE";

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(CATALOGUING_HOST_LMS_CODE,
			parseLong(newPatronCodeId), parseLong(newPatronCodeId), "DCB", canonicalPatronType);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var updatedPatron = singleValueFrom(client.updatePatron(localId, newPatronCodeId));

		// Assert
		mockPolarisFixture.verifyUpdatePatron(barcode,
			PAPIClient.PatronRegistration.builder()
				// These values have to line up with the config of the client
				.logonBranchID(73)
				.logonUserID(1)
				.logonWorkstationID(1)
				.patronCode(Integer.valueOf(newPatronCodeId))
				.build());

		assertThat(updatedPatron, allOf(
			notNullValue(),
			hasLocalIds(localId),
			hasLocalPatronType(newPatronCodeId),
			hasCanonicalPatronType(canonicalPatronType),
			hasLocalBarcodes(barcode),
			hasHomeLibraryCode(organisationId),
			isNotBlocked()
		));
	}

	@Test
	void shouldBeAbleToCheckOutAnItemToPatron() {
		// Arrange
		final var localItemId = "2273395";
		final var localPatronId = "3424738";
		final var localPatronBarcode = "0077777777";

		// update item status to available
		mockPolarisFixture.mockGetItemStatuses();
		mockPolarisFixture.mockGetItem(localItemId);
		mockPolarisFixture.mockStartWorkflow("item-workflow-response.json");
		mockPolarisFixture.mockContinueWorkflow("0e4c9e68-785e-4a1e-9417-f9bd245cc147",
			"create-item-resp.json");

		// checkout
		mockPolarisFixture.mockGetPatronBarcode(localPatronId, localPatronBarcode);
		mockPolarisFixture.mockGetItemBarcode(localItemId, "126448190");
		mockPolarisFixture.mockCheckoutItemToPatron(localPatronBarcode);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var command = CheckoutItemCommand.builder()
			.itemId(localItemId)
			.patronId(localPatronId)
			.patronBarcode(localPatronBarcode)
			.build();

		final var response = singleValueFrom(client.checkOutItemToPatron(command));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, is("OK"));
	}

	@Test
	void shouldBeAbleToRenewItem() {
		// Arrange
		final var localItemId = "8142391";
		final var localPatronId = "2198742";
		final var localItemBarcode = "4678231";
		final var localPatronBarcode = "9821734";

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(localItemId).localPatronId(localPatronId)
			.localItemBarcode(localItemBarcode).localPatronBarcode(localPatronBarcode).build();

		mockPolarisFixture.mockRenewalSuccess(localPatronBarcode);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var response = singleValueFrom(client.renew(hostLmsRenewal));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, allOf(
			hasProperty("localItemId", is("8142391")),
			hasProperty("localPatronId", is("2198742")),
			hasProperty("localItemBarcode", is("4678231")),
			hasProperty("localPatronBarcode", is("9821734"))
		));
	}

	@Test
	void shouldTranslateRenewalErrorResponses() {
		// Arrange
		final var localItemId = "3519827";
		final var localPatronId = "6584219";
		final var localItemBarcode = "2756348";
		final var localPatronBarcode = "9432198";

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(localItemId).localPatronId(localPatronId)
			.localItemBarcode(localItemBarcode).localPatronBarcode(localPatronBarcode).build();

		mockPolarisFixture.mockRenewalItemBlockedError(localPatronBarcode);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class, () -> singleValueFrom(client.renew(hostLmsRenewal)));

		// Assert
		assertThat(problem, allOf(
			hasProperty("title", is("Polaris ItemCheckoutPost failed")),
			hasProperty("detail", is("The item cannot be checked out because the item is blocked."))
		));
	}

	@Test
	void shouldBeAbleToCreateBib() {
		// Arrange
		mockPolarisFixture.mockCreateBib();

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var bib = singleValueFrom(client.createBib(
			Bib.builder()
				.title("title")
				.build()));

		// Assert
		assertThat(bib, is(notNullValue()));
		assertThat(bib, is("1203065"));
	}

	@Test
	void shouldFailWhenUnexpectedResponseReceivedDuringBibCreation() {
		// Arrange
		mockPolarisFixture.mockCreateBibNotAuthorisedResponse();

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.createBib(
				Bib.builder()
					.title("title")
					.build())));

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms(CATALOGUING_HOST_LMS_CODE),
			hasResponseStatusCode(401),
			hasTextResponseBody("No body"),
			hasRequestUrl("https://polaris-hostlms-tests.com/polaris.applicationservices/api/v1/eng/20/polaris/73/1/bibliographicrecords?type=create")
		));
	}

	@Test
	void shouldBeAbleToDeleteBib() {
		// Arrange
		final var localBibId = "3214809";
		mockPolarisFixture.mockStartWorkflow("continue-bib-delete.json");

		mockPolarisFixture.mockContinueWorkflow("ba8ce734-7b49-48b2-bdc3-c42f56d60091",
			"successful-bib-delete.json");

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var response = singleValueFrom(client.deleteBib(localBibId));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, is("OK"));
	}

	@Test
	void shouldBeAbleToCreateVirtualItem() {
		// Arrange
		mockPolarisFixture.mockStartWorkflow("item-workflow-response.json");
		mockPolarisFixture.mockContinueWorkflow("0e4c9e68-785e-4a1e-9417-f9bd245cc147",
			"create-item-resp.json");

		referenceValueMappingFixture.defineMapping("DCB", "ItemType", "TEST:CIRC", CATALOGUING_HOST_LMS_CODE, "ItemType", "007");

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var item = singleValueFrom(client.createItem(
			CreateItemCommand.builder()
				.bibId("1203065")
				.barcode("3430470102")
				.patronHomeLocation("37")
				.canonicalItemType("TEST:CIRC")
				.build()));

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			HostLmsItemMatchers.hasLocalId("4314002")
		));
	}

	@Test
	void shouldFailToCreateVirtualItemIfStartingWorkflowRespondsWithMissingAnswer() {
		// Arrange
		mockPolarisFixture.mockStartWorkflow(
			ApplicationServicesClient.WorkflowResponse.builder()
				.answerExtension(null)
				.informationMessages(List.of(
					InformationMessage.builder()
					.message("missing answer message")
					.build()))
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.createItem(
				CreateItemCommand.builder()
					.bibId("1203065")
					.barcode("3430470102")
					.patronHomeLocation("37")
					.build()
			)));

		// Assert
		assertThat(problem, allOf(
			notNullValue(),
			messageContains(
				"Request to map item type was missing required parameters: itemTypeCode=null, hostLmsCode=polaris-cataloguing")
		));
	}

	@Test
	void shouldBeAbleToGetAnItemById() {
		// Arrange
		final var localItemId = "3512742";

		mockPolarisFixture.mockGetItem(localItemId);
		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).build();

		final var localItem = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			HostLmsItemMatchers.hasLocalId(localItemId),
			HostLmsItemMatchers.hasStatus("LOANED"),
			HostLmsItemMatchers.hasBarcode("3430470102"),
			HostLmsItemMatchers.hasRenewalCount(1)
		));
	}

	@Test
	void shouldBeAbleToDefaultRenewalCount() {
		// Arrange
		final var localItemId = "3512742";

		mockPolarisFixture.mockGetItemWithNullRenewalCount(localItemId);
		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).build();

		final var localItem = singleValueFrom(client.getItem(hostLmsItem));

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			HostLmsItemMatchers.hasLocalId(localItemId),
			HostLmsItemMatchers.hasStatus("LOANED"),
			HostLmsItemMatchers.hasBarcode("3430470102"),
			HostLmsItemMatchers.hasRenewalCount(0)
		));
	}

	@Test
	void shouldFailToGetAnItemWhenUnexpectedResponseReceived() {
		// Arrange
		final var localItemId = "628125";

		mockPolarisFixture.mockGetItemServerErrorResponse(localItemId);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder().localId(localItemId).build();

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.getItem(hostLmsItem)));

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms(CATALOGUING_HOST_LMS_CODE),
			hasResponseStatusCode(500),
			hasTextResponseBody("Something went wrong")
		));
	}

	@Test
	void shouldBeAbleToDeleteAnExistingItem() {
		// Arrange
		final var localItemId = "3512742";

		mockPolarisFixture.mockStartWorkflow("deleteSingleItemContinue.json");

		mockPolarisFixture.mockContinueWorkflow("c457e0b8-3d89-45dc-abcd-a389f0993203",
			"deleteBibIfLastItem.json");

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var deleteItemCommand = DeleteCommand.builder()
			.itemId(localItemId)
			.build();

		final var response = singleValueFrom(client.deleteItem(deleteItemCommand));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, is("OK"));
	}

	@Test
	void ShouldBeAbleToUpdateStatusOfAnExistingItem() {
		// Arrange
		final var localItemId = "3512742";

		mockPolarisFixture.mockGetItem(localItemId);
		mockPolarisFixture.mockStartWorkflow("item-workflow-response.json");

		mockPolarisFixture.mockContinueWorkflow("0e4c9e68-785e-4a1e-9417-f9bd245cc147",
			"create-item-resp.json");

		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);
		final var string = singleValueFrom(client.updateItemStatus(localItemId, AVAILABLE, null));

		// Assert
		assertThat(string, is(notNullValue()));
		assertThat(string, is("OK"));
	}

	@Test
	void ShouldBeAbleToUpdatePatronRequest() {
		// Arrange
		final var localItemId = "3512742";

		mockPolarisFixture.mockGetItem(localItemId);
		mockPolarisFixture.mockStartWorkflow("item-workflow-response.json");

		mockPolarisFixture.mockContinueWorkflow("0e4c9e68-785e-4a1e-9417-f9bd245cc147",
			"create-item-resp.json");

		mockPolarisFixture.mockGetItemStatuses();

		final var localHoldId = "2977175";

		mockPolarisFixture.mockGetHold(localHoldId,
			LibraryHold.builder()
				.sysHoldStatus("Pending")
				.itemRecordID(3512742)
				.itemBarcode("785574212")
				.build());

		final var localRequest = LocalRequest.builder()
			.localId(localHoldId)
			.requestedItemId(localItemId)
			.requestedItemBarcode("785574212")
			.supplyingHostLmsCode("supplyingHostLmsCode")
			.supplyingAgencyCode("supplyingAgencyCode")
			.canonicalItemType("canonicalItemType")
			.requestedItemId(localItemId)
			.build();

		referenceValueMappingFixture.defineMapping("DCB", "ItemType", "canonicalItemType", CATALOGUING_HOST_LMS_CODE, "ItemType", "007");

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);
		final var returnValue = singleValueFrom(client.updateHoldRequest(localRequest));

		// Assert
		assertThat(returnValue, is(notNullValue()));
		assertThat(returnValue.getLocalId(), is("2977175"));
		assertThat(returnValue.getRequestedItemId(), is("3512742"));
		assertThat(returnValue.getLocalStatus(), is("CONFIRMED"));
		assertThat(returnValue.getRawLocalStatus(), is("Pending"));
	}

	private static String barcodeAsSerialisedList(String barcode) {
		// Multiple barcodes may be formatted as a serialised list in a string
		return "[%s]".formatted(barcode);
	}
}

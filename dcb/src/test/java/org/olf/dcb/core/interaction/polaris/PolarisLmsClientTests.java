package org.olf.dcb.core.interaction.polaris;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState.AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_READY;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.ERR0210;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.InformationMessage;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.ConfirmBibRecordDelete;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.ConfirmItemRecordDelete;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.LastCopyOrRecordOptions;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.NoDisplayInPAC;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowResponse.InputRequired;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasRawStatus;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyName;
import static org.olf.dcb.test.matchers.ItemMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasCallNumber;
import static org.olf.dcb.test.matchers.ItemMatchers.hasCanonicalItemType;
import static org.olf.dcb.test.matchers.ItemMatchers.hasDueDate;
import static org.olf.dcb.test.matchers.ItemMatchers.hasHostLmsCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalBibId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalItemType;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalItemTypeCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocation;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoAgency;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoHostLmsCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoLocation;
import static org.olf.dcb.test.matchers.ItemMatchers.hasOwningContext;
import static org.olf.dcb.test.matchers.ItemMatchers.hasSourceHostLmsCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ItemMatchers.hasZeroHoldCount;
import static org.olf.dcb.test.matchers.ItemMatchers.isNotDeleted;
import static org.olf.dcb.test.matchers.ItemMatchers.isNotSuppressed;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasRawLocalStatus;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasRequestedItemBarcode;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasRequestedItemId;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.ThrowableMatchers.messageContains;
import static org.olf.dcb.test.matchers.ThrowableProblemMatchers.hasParameters;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasMessageForHostLms;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestUrl;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasTextResponseBody;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasCanonicalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasHomeLibraryCode;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalBarcodes;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalIds;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoLocalNames;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isNotBlocked;
import static services.k_int.utils.StringUtils.convertIntegerToString;

import java.util.List;
import java.util.Random;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.DeleteCommand;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRenewal;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.AnswerData;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.AnswerExtension;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.BibInfo;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.BibliographicRecord;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.CirculationData;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.ItemRecord;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.ItemRecordFull;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.LibraryHold;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.SysHoldRequest;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowResponse;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronCirculationBlocksResult;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronRegistrationCreateResult;
import org.olf.dcb.core.interaction.polaris.exceptions.FindVirtualPatronException;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;
import org.olf.dcb.test.matchers.HostLmsRequestMatchers;
import org.olf.dcb.test.matchers.ItemMatchers;
import org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers;
import org.olf.dcb.test.matchers.interaction.PatronMatchers;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PolarisLmsClientTests {
	private static final String CATALOGUING_HOST_LMS_CODE = "polaris-cataloguing";
	private static final String CIRCULATING_HOST_LMS_CODE = "polaris-circulating";
	private static final int ILL_LOCATION_ID = 50;

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockPolarisFixture mockPolarisFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final var host = "polaris-hostlms-tests.com";
		final var baseUrl = "https://" + host;
		final var key = "test-key";
		final var secret = "test-secret";
		final var domain = "TEST";

		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createPolarisHostLms(CATALOGUING_HOST_LMS_CODE, key,
			secret, baseUrl, domain, key, secret, "default-agency-code",
			ILL_LOCATION_ID);

		hostLmsFixture.createPolarisHostLms(CIRCULATING_HOST_LMS_CODE, key,
			secret, baseUrl, domain, key, secret);

		mockPolarisFixture = new MockPolarisFixture(host,
			mockServerClient, testResourceLoaderProvider);
	}

	@BeforeEach
	void beforeEach() {
		mockPolarisFixture.mockPapiStaffAuthentication();
		mockPolarisFixture.mockAppServicesStaffAuthentication();

		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldBeAbleToGetItemsByBibIdWithDefaultAgency() {
		// Arrange
		defineItemTypeRangeMapping();

		// mock will return null shelf location so a fall back to the default agency will be used
		agencyFixture.defineAgency("default-agency-code", "Default Agency",
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		final var bibId = generateLocalId();

		mockPolarisFixture.mockGetItemsForBib(bibId);
		mockPolarisFixture.mockGetMaterialTypes();
		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var items = getItems(bibId, CATALOGUING_HOST_LMS_CODE);

		// Assert
		assertThat(items, hasSize(3));

		final var specificItem = items.stream()
			.filter(item -> "3512742".equals(item.getLocalId()))
			.findFirst()
			.orElse(null);

		assertThat(specificItem, allOf(
			notNullValue(),
			ItemMatchers.hasLocalId("3512742"),
			hasStatus(CHECKED_OUT),
			hasDueDate("2023-10-14T23:59:00Z"),
			hasNoLocation(),
			hasBarcode("3430470102"),
			hasCallNumber("E Bellini Mario"),
			hasLocalBibId(bibId),
			hasLocalItemType("Book"),
			hasLocalItemTypeCode("3"),
			hasCanonicalItemType("loanable-item"),
			hasZeroHoldCount(),
			isNotSuppressed(),
			isNotDeleted(),
			hasAgencyCode("default-agency-code"),
			hasAgencyName("Default Agency"),
			hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
			hasSourceHostLmsCode(CATALOGUING_HOST_LMS_CODE),
			hasOwningContext(CIRCULATING_HOST_LMS_CODE)
		));
	}

	@Test
	void shouldBeAbleToGetItemsByBibIdWithShelfLocationMapping() {
		// Arrange
		defineItemTypeRangeMapping();

		agencyFixture.defineAgency("mapped-agency-code", "Mapped Agency",
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		// Note: 'Bestseller' is the returned shelf location from mock mockGetItemsForBibWithShelfLocations
		// referenceValueMappingFixture.defineLocationToAgencyMapping(
		// 	CIRCULATING_HOST_LMS_CODE, "Bestseller", "mapped-agency-code");

		// Note - (II 25th Feb 2025) We should be mapping locationID to agency code and NOT shelving location.
		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CIRCULATING_HOST_LMS_CODE, "15", "mapped-agency-code");

		final var bibId = generateLocalId();

		mockPolarisFixture.mockGetItemsForBibWithShelfLocations(bibId);
		mockPolarisFixture.mockGetMaterialTypes();
		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var items = getItems(bibId, CIRCULATING_HOST_LMS_CODE);

		// Assert
		assertThat(items, hasSize(3));

		final var specificItem = items.stream()
			.filter(item -> "3512742".equals(item.getLocalId()))
			.findFirst()
			.orElse(null);

		assertThat(specificItem, allOf(
			notNullValue(),
			ItemMatchers.hasLocalId("3512742"),
			hasStatus(CHECKED_OUT),
			hasDueDate("2023-10-14T23:59:00Z"),
			hasLocation("Bestseller", "15"),
			hasBarcode("3430470102"),
			hasCallNumber("E Bellini Mario"),
			hasLocalBibId(bibId),
			hasLocalItemType("Book"),
			hasLocalItemTypeCode("3"),
			hasCanonicalItemType("loanable-item"),
			hasZeroHoldCount(),
			isNotSuppressed(),
			isNotDeleted(),
			hasAgencyCode("mapped-agency-code"),
			hasAgencyName("Mapped Agency"),
			hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
			hasSourceHostLmsCode(CIRCULATING_HOST_LMS_CODE),
			hasOwningContext(CIRCULATING_HOST_LMS_CODE)
		));
	}

	@Test
	void shouldBeAbleToGetItemsByBibIdWithNoAgency() {
		// Arrange
		final var bibId = generateLocalId();

		mockPolarisFixture.mockGetItemsForBib(bibId);
		mockPolarisFixture.mockGetMaterialTypes();
		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var items = getItems(bibId, CIRCULATING_HOST_LMS_CODE);

		// Assert
		assertThat(items, hasSize(3));

		final var specificItem = items.stream()
			.filter(item -> "3512742".equals(item.getLocalId()))
			.findFirst()
			.orElse(null);

		assertThat(specificItem, allOf(
			notNullValue(),
			ItemMatchers.hasLocalId("3512742"),
			hasStatus(CHECKED_OUT),
			hasDueDate("2023-10-14T23:59:00Z"),
			hasNoLocation(),
			hasBarcode("3430470102"),
			hasCallNumber("E Bellini Mario"),
			hasLocalBibId(bibId),
			hasLocalItemType("Book"),
			hasLocalItemTypeCode("3"),
			// Note: if there is no agency we cannot use the owning context to get the canonical item type
			hasCanonicalItemType("UNKNOWN - No mapping found"),
			hasZeroHoldCount(),
			isNotSuppressed(),
			isNotDeleted(),
			hasNoAgency(),
			hasNoHostLmsCode(),
			hasSourceHostLmsCode(CIRCULATING_HOST_LMS_CODE),
			hasOwningContext(CIRCULATING_HOST_LMS_CODE)
		));
	}

	@Test
	void shouldBeAbleToAuthenticatePatron() {
		// Arrange

		// Values come from hardcoded mock responses
		final var patronId = 1255192;
		final var barcode = "0088888888";
		final var branchId = 39;
		final var patronCodeId = 5;

		mockPolarisFixture.mockPatronAuthentication();
		mockPolarisFixture.mockGetPatronByBarcode(barcode);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var patron = singleValueFrom(client.patronAuth(
			"BASIC/BARCODE+PASSWORD", barcode, "password123"));

		// Assert
		assertThat(patron, allOf(
			PatronMatchers.hasLocalIds(convertIntegerToString(patronId)),
			hasLocalBarcodes(barcode),
			hasHomeLibraryCode(convertIntegerToString(branchId)),
			hasNoLocalNames(),
			hasLocalPatronType(patronCodeId)
		));
	}

	@Test
	void shouldBeAbleToFindVirtualPatron() {
		// Arrange
		final var localId = generateLocalId();
		final var barcode = generateBarcode();
		final var organisationId = "39";
		final var patronCodeId = "3";

		mockPolarisFixture.mockPatronSearch(barcode, barcode, localId);

		mockPolarisFixture.mockGetPatron(localId,
			ApplicationServicesClient.PatronData.builder()
				.patronID((localId))
				.patronCodeID(parseInt(patronCodeId))
				.barcode(barcode)
				.organizationID(parseInt(organisationId))
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
					.localId(convertIntegerToString(localId))
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
			hasLocalIds(convertIntegerToString(localId)),
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
		final var localId = generateLocalId();
		final var barcode = generateBarcode();
		final var organisationId = "39";
		final var patronCodeId = "3";

		mockPolarisFixture.mockPatronSearch(barcode, barcode, localId);

		mockPolarisFixture.mockGetPatron(localId,
			ApplicationServicesClient.PatronData.builder()
				.patronID((localId))
				.patronCodeID(parseInt(patronCodeId))
				.barcode(barcode)
				.organizationID(parseInt(organisationId))
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
					.localId(convertIntegerToString(localId))
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
			hasLocalIds(convertIntegerToString(localId)),
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
		final var localId = generateLocalId();
		final var barcode = generateBarcode();

		mockPolarisFixture.mockPatronSearch(barcode, barcode, localId);

		mockPolarisFixture.mockGetPatron(localId,
			ApplicationServicesClient.PatronData.builder()
				.patronID((localId))
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
					.localId(convertIntegerToString(localId))
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
		final var barcode = "3482656";

		final int errorCode = -5;
		final var errorMessage = "Something went wrong";

		mockPolarisFixture.mockPatronSearchPapiError(barcode, errorCode, errorMessage);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var patron = org.olf.dcb.core.model.Patron.builder()
			.id(randomUUID())
			.patronIdentities(List.of(
				PatronIdentity.builder()
					.localId("1255193")
					.localBarcode(barcodeAsSerialisedList(barcode))
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
		final var itemId = generateLocalId();
		final var itemBarcode = generateBarcode();
		final var bibId = generateLocalId();

		mockGetItem(itemId, itemBarcode, bibId);

		mockGetBibId(bibId);

		mockPolarisFixture.mockPlaceHold();

		final var patronId = generateLocalId();
		final var holdId = generateLocalId();
		final var matchingNote = "DCB Testing PACDisplayNotes";

		mockPolarisFixture.mockListPatronLocalHolds(patronId,
			SysHoldRequest.builder()
				.sysHoldRequestID(holdId)
				.bibliographicRecordID(bibId)
				.pacDisplayNotes(matchingNote)
				.build());

		mockPolarisFixture.mockGetHold(holdId,
			LibraryHold.builder()
				.sysHoldStatus("In Processing")
				.itemRecordID(itemId)
				.itemBarcode(itemBarcode)
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var localRequest = singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId(convertIntegerToString(patronId))
				.localBibId(null)
				.localItemId(convertIntegerToString(itemId))
				.pickupLocationCode("5324532")
				.note(matchingNote)
				.patronRequestId(randomUUID().toString())
				.build()
		));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(holdId),
			hasLocalStatus("In Processing"),
			hasRequestedItemId(itemId),
			hasRequestedItemBarcode(itemBarcode)
		));
	}

	@Test
	void shouldBeAbleToPlaceRequestAtBorrowingAndPickupAgency() {
		// Arrange
		final var itemId = generateLocalId();
		final var bibId = generateLocalId();

		mockGetItem(itemId, generateBarcode(), bibId);
		mockGetBibId(bibId);

		mockPolarisFixture.mockPlaceHold();

		final var holdId = generateLocalId();
		final var matchingNote = "DCB Testing PACDisplayNotes";

		final var patronId = generateLocalId();

		mockPolarisFixture.mockListPatronLocalHolds(patronId,
			SysHoldRequest.builder()
				.sysHoldRequestID(holdId)
				.bibliographicRecordID(bibId)
				.pacDisplayNotes(matchingNote)
				.build());

		final var itemBarcode = "785574212";
		final var localHoldStatus = "In Processing";

		mockPolarisFixture.mockGetHold(holdId,
			LibraryHold.builder()
				.sysHoldStatus(localHoldStatus)
				.itemRecordID(itemId)
				.itemBarcode(itemBarcode)
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final Integer pickupBranchId = 3721593;

		final var localRequest = singleValueFrom(client.placeHoldRequestAtBorrowingAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId(convertIntegerToString(patronId))
				.localBibId(convertIntegerToString(bibId))
				.localItemId(convertIntegerToString(itemId))
				.pickupLocationCode(convertIntegerToString(pickupBranchId))
				.note(matchingNote)
				.patronRequestId(randomUUID().toString())
				.activeWorkflow(STANDARD_WORKFLOW)
				.build()
		));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(holdId),
			hasLocalStatus(localHoldStatus),
			hasRequestedItemId(itemId),
			hasRequestedItemBarcode(itemBarcode)
		));

		mockPolarisFixture.verifyPlaceHold(
			ApplicationServicesClient.RequestExtensionData.builder()
				.itemRecordID(itemId)
				.patronID(patronId)
				.pickupBranchID(pickupBranchId)
				.origin(2)
				.bibliographicRecordID(bibId)
				.itemLevelHold(true)
				.build());
	}



	@Test
	void shouldBeAbleToPlaceRequestAtPickupAgency() {
		// Arrange
		final var itemId = generateLocalId();
		final var patronId = generateLocalId();
		final var itemBarcode = generateBarcode();

		final var pickupLocationLocalId = "5324532";
		final var pickupLocation = Location.builder().id(randomUUID()).localId(pickupLocationLocalId).build();

		final var bibId = generateLocalId();

		mockGetItem(itemId, itemBarcode, bibId);

		mockGetBibId(bibId);

		mockPolarisFixture.mockPlaceHold();

		final var holdId = generateLocalId();
		final var matchingNote = "DCB Testing PACDisplayNotes";

		mockPolarisFixture.mockListPatronLocalHolds(patronId,
			SysHoldRequest.builder()
				.sysHoldRequestID(holdId)
				.bibliographicRecordID(bibId)
				.pacDisplayNotes(matchingNote)
				.build());

		mockPolarisFixture.mockGetHold(holdId,
			LibraryHold.builder()
				.sysHoldStatus("In Processing")
				.itemRecordID(itemId)
				.itemBarcode(itemBarcode)
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var localRequest = singleValueFrom(client.placeHoldRequestAtPickupAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId(convertIntegerToString(patronId))
				.localItemId(convertIntegerToString(itemId))
				.pickupLocation(pickupLocation)
				.note(matchingNote)
				.patronRequestId(randomUUID().toString())
				.build()
		));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(holdId),
			hasLocalStatus("In Processing"),
			hasRequestedItemId(itemId),
			hasRequestedItemBarcode(itemBarcode)
		));
	}

	@Test
	void shouldBeAbleToPlaceRequestAtBorrowingOnlyAgency() {
		// Arrange
		final Integer itemId = generateLocalId();
		final Integer bibId = generateLocalId();

		mockGetItem(itemId, generateBarcode(), bibId);
		mockGetBibId(bibId);

		mockPolarisFixture.mockPlaceHold();

		final var holdId = generateLocalId();
		final var matchingNote = "DCB Testing PACDisplayNotes";

		final var patronId = generateLocalId();

		mockPolarisFixture.mockListPatronLocalHolds(patronId,
			SysHoldRequest.builder()
				.sysHoldRequestID(holdId)
				.bibliographicRecordID(bibId)
				.pacDisplayNotes(matchingNote)
				.build());

		final var itemBarcode = generateBarcode();
		final var localHoldStatus = "In Processing";

		mockPolarisFixture.mockGetHold(holdId.toString(),
			LibraryHold.builder()
				.sysHoldStatus(localHoldStatus)
				.itemRecordID(itemId)
				.itemBarcode(itemBarcode)
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var localRequest = singleValueFrom(client.placeHoldRequestAtBorrowingAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId(patronId.toString())
				.localBibId(bibId.toString())
				.localItemId(itemId.toString())
				.pickupLocationCode("543875")
				.note(matchingNote)
				.patronRequestId(randomUUID().toString())
				.activeWorkflow(PICKUP_ANYWHERE_WORKFLOW)
				.build()
		));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(holdId),
			hasLocalStatus(localHoldStatus),
			hasRequestedItemId(itemId.toString()),
			hasRequestedItemBarcode(itemBarcode)
		));

		mockPolarisFixture.verifyPlaceHold(
			ApplicationServicesClient.RequestExtensionData.builder()
				.itemRecordID(itemId)
				.patronID(patronId)
				.pickupBranchID(ILL_LOCATION_ID)
				.origin(2)
				.bibliographicRecordID(bibId)
				.itemLevelHold(true)
				.build());
	}

	@Test
	void shouldFailToPlaceRequestAtSupplyingAgencyWhenMatchingHoldIsEmpty() {
		// Arrange
		final var itemId = generateLocalId();
		final var itemBarcode = generateBarcode();
		final var bibId = generateLocalId();

		mockGetItem(itemId, itemBarcode, bibId);

		mockGetBibId(bibId);

		mockPolarisFixture.mockPlaceHold();

		final var patronId = generateLocalId();

		mockPolarisFixture.mockListPatronLocalHolds(patronId, List.of());

		mockPolarisFixture.mockGetHold("3773060",
			LibraryHold.builder()
				.sysHoldStatus("In Processing")
				.itemRecordID(generateLocalId())
				.itemBarcode(generateBarcode())
				.build());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var exception = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localPatronId(convertIntegerToString(patronId))
					.localBibId(null)
					.localItemId(convertIntegerToString(itemId))
					.pickupLocationCode("5324532")
					.note("DCB Testing PACDisplayNotes")
					.patronRequestId(randomUUID().toString())
					.build()
			)));

		// Assert
		assertThat(exception, allOf(
			notNullValue(),
			messageContains("No holds to process for local patron id: %s".formatted(patronId))
		));
	}

	@Test
	void shouldFailToPlaceRequestAtSupplyingAgencyWhenCreatingTheHoldFails() {
		// Arrange
		final var itemId = generateLocalId();
		final var bibId = generateLocalId();

		final var patronId = generateLocalId();

		mockGetItem(itemId, generateBarcode(), bibId);
		mockGetBibId(bibId);
		mockPolarisFixture.mockPlaceHoldUnsuccessful();

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var exception = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localPatronId(convertIntegerToString(patronId))
					.localBibId(null)
					.localItemId(convertIntegerToString(itemId))
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
		final var localHoldId = generateLocalId();

		final var itemId = generateLocalId();
		final var barcode = generateBarcode();

		mockPolarisFixture.mockGetHold(localHoldId,
			LibraryHold.builder()
				.sysHoldStatus("Pending")
				.itemRecordID(itemId )
				.itemBarcode(barcode)
				.build());

		// Act
		final var request = getRequest(localHoldId);

		// Assert
		assertThat(request, allOf(
			notNullValue(),
			HostLmsRequestMatchers.hasLocalId(localHoldId),
			hasStatus(HOLD_CONFIRMED),
			HostLmsRequestMatchers.hasRequestedItemId(itemId),
			HostLmsRequestMatchers.hasRequestedItemBarcode(barcode)
		));
	}

	@Test
	void shouldDetectRequestHasBeenCancelled() {
		// Arrange
		final var localHoldId = generateLocalId();

		mockPolarisFixture.mockGetHold(localHoldId,
			LibraryHold.builder()
				.sysHoldStatus("Cancelled")
				.itemRecordID(6737455)
				.itemBarcode("785574212")
				.build());

		// Act
		final var request = getRequest(localHoldId);

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
		final var localHoldId = generateLocalId();

		mockPolarisFixture.mockGetHoldNotFound(localHoldId, PolarisError.builder()
			.errorCode(60028)
			.message("Hold Request with ID 6348612 not found.")
			.build());

		// Act
		final var request = getRequest(localHoldId);

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
		final var localHoldId = generateLocalId();

		mockPolarisFixture.mockGetHold(localHoldId,
			LibraryHold.builder()
				.sysHoldStatus("Held")
				.itemRecordID(6737455)
				.itemBarcode("785574212")
				.build());

		// Act
		final var request = getRequest(localHoldId);

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
		final var localHoldId = generateLocalId();

		mockPolarisFixture.mockGetHold(localHoldId,
			LibraryHold.builder()
				.sysHoldStatus("Shipped")
				.itemRecordID(6737455)
				.itemBarcode("785574212")
				.build());

		// Act
		final var request = getRequest(localHoldId);

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
		final var itemId = generateLocalId();

		mockGetItem(itemId, generateBarcode(), generateLocalId());

		final var patronId = generateLocalId();

		mockPolarisFixture.mockCreatePatron(
			PatronRegistrationCreateResult.builder()
				.patronID(patronId)
				.papiErrorCode(0)
				.build());

		mockPolarisFixture.mockGetPatronBlocksSummary(patronId);

		final var patronBarcode = generateBarcode();

		final var patron = Patron.builder()
			.uniqueIds(List.of("dcb_unique_Id"))
			.localPatronType("1")
			.localHomeLibraryCode("39")
			.localBarcodes(List.of(patronBarcode))
			.localItemId(convertIntegerToString(itemId))
			.build();

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var response = singleValueFrom(client.createPatron(patron));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, is(convertIntegerToString(patronId)));
	}

	@Test
	void shouldFailWhenCreatingVirtualPatronReturnsNonZeroErrorCode() {
		// Arrange
		final var localItemId = generateLocalId();

		mockGetItem(localItemId, generateBarcode(), generateLocalId());

		mockPolarisFixture.mockCreatePatron(PatronRegistrationCreateResult.builder()
			.papiErrorCode(-3505)
			.errorMessage("Duplicate barcode")
			.build());

		final var patronBarcode = generateBarcode();

		final var patron = Patron.builder()
			.uniqueIds(List.of("dcb_unique_Id"))
			.localPatronType("1")
			.localHomeLibraryCode("39")
			.localBarcodes(List.of(patronBarcode))
			.localItemId(convertIntegerToString(localItemId))
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
		final var patronId = generateLocalId();
		final var barcode = generateBarcode();

		final var organisationId = "39";
		final var newPatronCodeId = "7";

		mockPolarisFixture.mockGetPatronBarcode(patronId, barcode);
		mockPolarisFixture.mockUpdatePatron(barcode);

		mockPolarisFixture.mockGetPatron(patronId,
			ApplicationServicesClient.PatronData.builder()
				.patronID((patronId))
				.patronCodeID(parseInt(newPatronCodeId))
				.barcode(barcode)
				.organizationID(parseInt(organisationId))
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

		final var updatedPatron = singleValueFrom(client.updatePatron(
			convertIntegerToString(patronId), newPatronCodeId));

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
			hasLocalIds(convertIntegerToString(patronId)),
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
		final var itemId = generateLocalId();
		final var patronId = generateLocalId();
		final var patronBarcode = generateBarcode();

		mockPolarisFixture.mockGetItemStatuses();

		final var itemBarcode = generateBarcode();

		mockGetItem(itemId, itemBarcode, generateLocalId());

		mockItemWorkflow(generateLocalId());

		// checkout
		mockPolarisFixture.mockGetPatronBarcode(patronId, patronBarcode);
		mockPolarisFixture.mockGetItemBarcode(itemId, itemBarcode);

		mockPolarisFixture.mockCheckoutItemToPatron(patronBarcode);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var command = CheckoutItemCommand.builder()
			.itemId(convertIntegerToString(itemId))
			.patronId(convertIntegerToString(patronId))
			.patronBarcode(patronBarcode)
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
		final var bibId = generateLocalId();

		mockPolarisFixture.mockCreateBib(bibId);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var receivedBibId = singleValueFrom(client.createBib(
			Bib.builder()
				.title("title")
				.canonicalItemType("CIRCAV")
				.build()));

		// Assert
		assertThat(receivedBibId, is(convertIntegerToString(bibId)));
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
		final var localBibId = generateLocalId();

		final var workflowRequestId = randomUUID().toString();

		mockPolarisFixture.mockStartWorkflow(confirmDeleteBibWorkflowPrompt(workflowRequestId));
		mockPolarisFixture.mockContinueWorkflow(workflowRequestId, defaultWorkflowResponse());

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var response = singleValueFrom(client.deleteBib(convertIntegerToString(localBibId)));

		// Assert
		assertThat(response, is("OK"));
	}

	@Test
	void shouldBeAbleToCreateVirtualItem() {
		// Arrange
		final var itemId = generateLocalId();

		mockPolarisFixture.mockStartWorkflow(
			WorkflowResponse.builder()
				.workflowRequestGuid(randomUUID().toString())
				.answerExtension(AnswerExtension.builder()
					.answerData(AnswerData.builder()
						.itemRecord(ItemRecord.builder()
							.itemRecordID(itemId)
							.build())
						.build())
					.build())
				.build());

		defineItemTypeMapping("TEST:CIRC");

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
			HostLmsItemMatchers.hasLocalId(itemId)
		));
	}

	@Test
	void shouldFailToCreateVirtualItemIfStartingWorkflowRespondsWithMissingAnswer() {
		// Arrange
		mockPolarisFixture.mockStartWorkflow(
			WorkflowResponse.builder()
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
		final var localItemId = generateLocalId();
		final var itemBarcode = generateBarcode();

		mockPolarisFixture.mockGetItem(localItemId,
			ItemRecordFull.builder()
				.itemRecordID(localItemId)
				.barcode(itemBarcode)
				.itemStatusDescription("Checked Out")
				.circulationData(CirculationData.builder()
					.renewalCount(1)
					.build())
				// Must have some bib info because logic for renewal checks
				// does not tolerate this part of the response not being present
				.bibInfo(BibInfo.builder()
					.bibliographicRecordID(generateLocalId())
					.canItemBeRenewed(false)
					.build())
				.build());

		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var localItem = getItem(localItemId);

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			HostLmsItemMatchers.hasLocalId(localItemId),
			HostLmsItemMatchers.hasStatus("LOANED"),
			HostLmsItemMatchers.hasBarcode(itemBarcode),
			HostLmsItemMatchers.hasRenewalCount(1)
		));
	}

	@Test
	void shouldBeAbleToDefaultRenewalCount() {
		// Arrange
		final var localItemId = generateLocalId();
		final var barcode = generateBarcode();

		mockPolarisFixture.mockGetItem(localItemId,
			ItemRecordFull.builder()
				.itemRecordID(localItemId)
				.barcode(barcode)
				.itemStatusDescription("Checked Out")
				// Must have some bib info because logic for renewal checks
				// does not tolerate this part of the response not being present
				.bibInfo(BibInfo.builder()
					.bibliographicRecordID(generateLocalId())
					.canItemBeRenewed(false)
					.build())
				.build());

		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var localItem = getItem(localItemId);

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			HostLmsItemMatchers.hasLocalId(localItemId),
			HostLmsItemMatchers.hasStatus("LOANED"),
			HostLmsItemMatchers.hasBarcode(barcode),
			HostLmsItemMatchers.hasRenewalCount(0)
		));
	}

	@Test
	void shouldFailToGetAnItemWhenUnexpectedResponseReceived() {
		// Arrange
		final var localItemId = generateLocalId();

		mockPolarisFixture.mockGetItemServerErrorResponse(localItemId);

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder()
			.localId(convertIntegerToString(localItemId))
			.build();

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
		final var localItemId = generateLocalId();
		final var workflowRequestId = randomUUID().toString();

		mockPolarisFixture.mockStartWorkflow(confirmItemDeleteWorkflowPrompt(workflowRequestId));
		mockPolarisFixture.mockContinueWorkflow(workflowRequestId,
			lastCopyOptionsWorkflowPrompt(workflowRequestId));
		mockPolarisFixture.mockContinueWorkflow(workflowRequestId, defaultWorkflowResponse());

		// Act
		final var response = deleteItem(localItemId);

		// Assert
		assertThat(response, is("OK"));
	}

	@Test
	void ShouldBeAbleToUpdateStatusOfAnExistingItem() {
		// Arrange
		final var localItemId = generateLocalId();

		mockGetItem(localItemId, generateBarcode(), generateLocalId());

		mockItemWorkflow(generateLocalId());

		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var response = updateItemStatus(localItemId);

		// Assert
		assertThat(response, is("OK"));
	}

	@Test
	void ShouldBeAbleToUpdatePatronRequest() {
		// Arrange
		final var itemId = generateLocalId();
		final var itemBarcode = generateBarcode();

		mockGetItem(itemId, itemBarcode, generateLocalId());

		mockItemWorkflow(generateLocalId());

		mockPolarisFixture.mockGetItemStatuses();

		final var holdId = generateLocalId();

		mockPolarisFixture.mockGetHold(holdId,
			LibraryHold.builder()
				.sysHoldStatus("Pending")
				.itemRecordID(itemId)
				.itemBarcode(itemBarcode)
				.build());

		final var localRequest = LocalRequest.builder()
			.localId(convertIntegerToString(holdId))
			.requestedItemId(convertIntegerToString(itemId))
			.requestedItemBarcode(itemBarcode)
			.supplyingHostLmsCode("supplyingHostLmsCode")
			.supplyingAgencyCode("supplyingAgencyCode")
			.canonicalItemType("canonicalItemType")
			.requestedItemId(convertIntegerToString(itemId))
			.build();

		defineItemTypeMapping("canonicalItemType");

		// Act
		final var updatedLocalRequest = updateHoldRequest(localRequest);

		// Assert
		assertThat(updatedLocalRequest, allOf(
			notNullValue(),
			hasLocalId(holdId),
			hasRequestedItemId(itemId),
			hasLocalStatus("CONFIRMED"),
			hasRawLocalStatus("Pending")
		));
	}

	private String deleteItem(Integer localItemId) {
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var deleteItemCommand = DeleteCommand.builder()
			.itemId(convertIntegerToString(localItemId))
			.build();

		return singleValueFrom(client.deleteItem(deleteItemCommand));
	}

	private String updateItemStatus(Integer localItemId) {
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var item = HostLmsItem.builder()
			.localId(convertIntegerToString(localItemId))
			.build();

		return singleValueFrom(client.updateItemStatus(item, AVAILABLE));
	}

	private LocalRequest updateHoldRequest(LocalRequest localRequest) {
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		return singleValueFrom(client.updateHoldRequest(localRequest));
	}

	private void defineItemTypeMapping(String canonicalItemType) {
		referenceValueMappingFixture.defineMapping("DCB", "ItemType",
			canonicalItemType, CATALOGUING_HOST_LMS_CODE, "ItemType", "007");
	}

	private void defineItemTypeRangeMapping() {
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			CIRCULATING_HOST_LMS_CODE, 3, 3, "loanable-item");
	}

	private List<Item> getItems(Integer bibId, String hostLmsCode) {
		final var client = hostLmsFixture.createClient(hostLmsCode);

		return singleValueFrom(client.getItems(BibRecord.builder()
			.sourceRecordId(convertIntegerToString(bibId))
			.build()));
	}

	private HostLmsItem getItem(Integer localItemId) {
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var hostLmsItem = HostLmsItem.builder()
			.localId(convertIntegerToString(localItemId))
			.build();

		return singleValueFrom(client.getItem(hostLmsItem));
	}

	private HostLmsRequest getRequest(Integer localHoldId) {
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder()
			.localId(convertIntegerToString(localHoldId))
			.build();

		return singleValueFrom(client.getRequest(hostLmsRequest));
	}

	private static String barcodeAsSerialisedList(String barcode) {
		// Multiple barcodes may be formatted as a serialised list in a string
		return "[%s]".formatted(barcode);
	}

	private void mockGetItem(Integer itemId, String itemBarcode, Integer bibId) {
		mockPolarisFixture.mockGetItem(itemId,
			ItemRecordFull.builder()
				.itemRecordID(itemId)
				.barcode(itemBarcode)
				.bibInfo(BibInfo.builder()
					.bibliographicRecordID(bibId)
					.build())
				.build());
	}

	private void mockGetBibId(Integer bibId) {
		mockPolarisFixture.mockGetBib(bibId,
			BibliographicRecord.builder()
				.bibliographicRecordID(bibId)
				.build());
	}

	private void mockItemWorkflow(Integer itemRecordId) {
		final var workflowRequestId = randomUUID().toString();

		mockPolarisFixture.mockStartWorkflow(noDisplayInPacWorkflowPrompt(workflowRequestId));
		mockPolarisFixture.mockContinueWorkflow(workflowRequestId,
			WorkflowResponse.builder()
				.answerExtension(AnswerExtension.builder()
					.answerData(AnswerData.builder()
						.itemRecord(ItemRecord.builder()
							.itemRecordID(itemRecordId)
							.build())
						.build())
					.build())
				.build());
	}

	private static WorkflowResponse noDisplayInPacWorkflowPrompt(String workflowRequestId) {
		return WorkflowResponse.builder()
			.workflowRequestGuid(workflowRequestId)
			.workflowStatus(InputRequired)
			.prompt(Prompt.builder()
				.WorkflowPromptID(NoDisplayInPAC)
				.title("Update item record")
				.message(
					"This item will not display in PAC. Do you want to continue saving?")
				.build())
			.informationMessages(emptyList())
			.build();
	}

	private static WorkflowResponse confirmDeleteBibWorkflowPrompt(String workflowRequestId) {
		return WorkflowResponse.builder()
			.workflowRequestGuid(workflowRequestId)
			.workflowStatus(InputRequired)
			.prompt(Prompt.builder()
				.WorkflowPromptID(ConfirmBibRecordDelete)
				.title("Delete bibliographic record")
				.message(
					"The bibliographic record will be marked for deletion. Do you want to continue?")
				.build())
			.build();
	}

	private static WorkflowResponse confirmItemDeleteWorkflowPrompt(
		String workflowRequestId) {
		return WorkflowResponse.builder()
			.workflowRequestGuid(workflowRequestId)
			.workflowStatus(InputRequired)
			.prompt(Prompt.builder()
				.WorkflowPromptID(ConfirmItemRecordDelete)
				.title("Delete item record")
				.message(
					"The item record will be marked for deletion.  Do you want to continue?")
				.build())
			.build();
	}

	private static WorkflowResponse lastCopyOptionsWorkflowPrompt(String workflowRequestId) {
		return WorkflowResponse.builder()
			.workflowRequestGuid(workflowRequestId)
			.workflowStatus(InputRequired)
			.prompt(Prompt.builder()
				.WorkflowPromptID(LastCopyOrRecordOptions)
				.title("Last copy options")
				.message("The following record options are available:")
				.build())
			.build();
	}

	private static WorkflowResponse defaultWorkflowResponse() {
		return WorkflowResponse.builder().build();
	}

	private static Integer generateLocalId() {
		return new Random().nextInt(1000000);
	}

	private static @NonNull String generateBarcode() {
		return String.valueOf(new Random().nextLong(1000000000));
	}
}

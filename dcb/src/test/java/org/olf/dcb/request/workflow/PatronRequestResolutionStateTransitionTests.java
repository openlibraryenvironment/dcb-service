package org.olf.dcb.request.workflow;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.FunctionalSettingType.OWN_LIBRARY_BORROWING;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.core.model.WorkflowConstants.EXPEDITED_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.LOCAL_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasBriefDescription;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasNestedAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasActiveWorkflow;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasErrorMessage;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasNoResolutionCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasResolutionCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ResolutionAuditMatchers.isNoSelectableItemResolutionAudit;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasHostLmsCode;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalAgencyCode;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalBibId;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemBarcode;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemId;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemLocationCode;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasNoLocalId;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasNoLocalItemStatus;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasNoLocalStatus;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasResolvedAgency;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PatronRequestResolutionStateTransitionTests {
	private final String CATALOGUING_HOST_LMS_CODE = "resolution-cataloguing";
	private final String CIRCULATING_HOST_LMS_CODE = "resolution-circulating";
	private final String BORROWING_HOST_LMS_CODE = "resolution-borrowing";

	private final String SUPPLYING_AGENCY_CODE = "supplying-agency";
	private final String BORROWING_AGENCY_CODE = "borrowing-agency";

	private final String ITEM_LOCATION_CODE = "item-location";

	@Inject
	private PatronRequestResolutionStateTransition patronRequestResolutionStateTransition;

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private PatronRequestWorkflowService patronRequestWorkflowService;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private LocationFixture locationFixture;
	@Inject
	private ConsortiumFixture consortiumFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	private DataHostLms cataloguingHostLms;
	private DataHostLms borrowingHostLms;
	private DataAgency borrowingAgency;

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mockServerClient) {
		log.info("beforeAll\n\n");

		final var token = "resolution-system-token";
		final var key = "resolution-system-key";
		final var secret = "resolution-system-secret";

		sierraItemsAPIFixture = sierraApiFixtureProvider.items(mockServerClient);

		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();

		hostLmsFixture.deleteAll();

		final var cataloguingHostLmsUrl = "https://resolution-tests.com";

		SierraTestUtils.mockFor(mockServerClient, cataloguingHostLmsUrl)
			.setValidCredentials(key, secret, token, 60);

		cataloguingHostLms = hostLmsFixture.createSierraHostLms(CATALOGUING_HOST_LMS_CODE,
			key, secret, cataloguingHostLmsUrl, "item");

		hostLmsFixture.createSierraHostLms(
			CIRCULATING_HOST_LMS_CODE, key,
			secret, "http://some-circulating-system", "item");

		final var borrowingHostLmsUrl = "http://some-borrowing-system";

		SierraTestUtils.mockFor(mockServerClient, borrowingHostLmsUrl)
			.setValidCredentials(key, secret, token, 60);

		borrowingHostLms = hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE,
			key, secret, borrowingHostLmsUrl, "item");
	}

	@BeforeEach
	void beforeEach() {
		log.info("beforeEach\n\n");

		consortiumFixture.deleteAll();
		clusterRecordFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		locationFixture.deleteAll();

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, ITEM_LOCATION_CODE, SUPPLYING_AGENCY_CODE);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_CODE,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		borrowingAgency = agencyFixture.defineAgency(BORROWING_AGENCY_CODE,
			BORROWING_AGENCY_CODE, hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE));
	}

	@Test
	void shouldChooseFirstAvailableItem() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			"465675", clusterRecord);

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			CIRCULATING_HOST_LMS_CODE, 1, 1, "loanable-item");

		sierraItemsAPIFixture.itemsForBibId("465675", List.of(
			SierraItem.builder()
				.id("1000001")
				.barcode("30800005238487")
				.locationCode(ITEM_LOCATION_CODE)
				.statusCode("-")
				.dueDate(Instant.parse("2021-02-25T12:00:00Z"))
				.itemType("1")
				.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
				.build(),
			SierraItem.builder()
				.id("1000002")
				.barcode("6565750674")
				.locationCode(ITEM_LOCATION_CODE)
				.statusCode("-")
				.itemType("1")
				.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
				.build()
		));

		final var agency = agencyFixture.findByCode(BORROWING_AGENCY_CODE);

		final var pickupLocation = locationFixture.createPickupLocation(agency);

		final var pickupLocationId = pickupLocation.getIdAsString();

		final var patron = definePatron("872321", "465636");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCode(pickupLocationId)
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertSuccessfulResolution(fetchedPatronRequest, STANDARD_WORKFLOW);

		final var onlySupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		final var expectedAgency = agencyFixture.findByCode(SUPPLYING_AGENCY_CODE);

		assertThat(onlySupplierRequest, allOf(
			notNullValue(),
			hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
			hasLocalItemId("1000002"),
			hasLocalItemBarcode("6565750674"),
			hasLocalBibId("465675"),
			hasLocalItemLocationCode(ITEM_LOCATION_CODE),
			hasNoLocalItemStatus(),
			hasNoLocalId(),
			hasNoLocalStatus(),
			hasLocalAgencyCode(SUPPLYING_AGENCY_CODE),
			hasResolvedAgency(expectedAgency)
		));

		assertSuccessfulResolutionAudit(fetchedPatronRequest, "1000002",
			"6565750674", CIRCULATING_HOST_LMS_CODE);
	}

	@Test
	void shouldChooseItemFromDifferentSupplierWhenPickingUpAnywhere() {
		// Arrange
		allowOwnLibraryBorrowing();

		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var localBibId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			localBibId, clusterRecord);

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			CIRCULATING_HOST_LMS_CODE, 1, 1, "loanable-item");

		final var localItemId = "583723";
		final var localItemBarcode = "0873753";

		sierraItemsAPIFixture.itemsForBibId(localBibId, List.of(
			availableItem(localItemId, localItemBarcode, ITEM_LOCATION_CODE)
		));

		final var pickupLocation = locationFixture.createPickupLocation(
			agencyFixture.defineAgency("pickup-agency", "Pickup Agency",
				hostLmsFixture.createDummyHostLms("pickup-host-lms")));

		final var patron = definePatron("365342", "837532");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
		.pickupLocationCode(pickupLocation.getIdAsString())
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertSuccessfulResolution(fetchedPatronRequest, PICKUP_ANYWHERE_WORKFLOW);

		final var onlySupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		final var expectedAgency = agencyFixture.findByCode(SUPPLYING_AGENCY_CODE);

		assertThat(onlySupplierRequest, allOf(
			notNullValue(),
			hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
			hasLocalItemId(localItemId),
			hasLocalItemBarcode(localItemBarcode),
			hasLocalBibId(localBibId),
			hasLocalItemLocationCode(ITEM_LOCATION_CODE),
			hasNoLocalItemStatus(),
			hasNoLocalId(),
			hasNoLocalStatus(),
			hasLocalAgencyCode(SUPPLYING_AGENCY_CODE),
			hasResolvedAgency(expectedAgency)
		));

		assertSuccessfulResolutionAudit(fetchedPatronRequest, localItemId,
			localItemBarcode, CIRCULATING_HOST_LMS_CODE);
	}

	@Test
	void shouldChooseLocalItemWhenAlsoPickingUpLocally() {
		// Arrange
		allowOwnLibraryBorrowing();

		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var localBibId = "35255";

		bibRecordFixture.createBibRecord(bibRecordId, borrowingHostLms.getId(),
			localBibId, clusterRecord);

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, 1, 1, "loanable-item");

		final var localItemLocationCode = "local-location";

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			BORROWING_HOST_LMS_CODE, localItemLocationCode, borrowingAgency.getCode());

		final var localItemId = "529772";
		final var localItemBarcode = "09877573";

		sierraItemsAPIFixture.itemsForBibId(localBibId, List.of(
			availableItem(localItemId, localItemBarcode, localItemLocationCode)
		));

		final var pickupLocation = locationFixture.createPickupLocation(borrowingAgency);

		final var patron = definePatron("782753", "home-library");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCode(pickupLocation.getIdAsString())
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertSuccessfulResolution(fetchedPatronRequest, LOCAL_WORKFLOW);

		final var onlySupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat(onlySupplierRequest, allOf(
			notNullValue(),
			hasHostLmsCode(BORROWING_HOST_LMS_CODE),
			hasLocalItemId(localItemId),
			hasLocalItemBarcode(localItemBarcode),
			hasLocalBibId(localBibId),
			hasLocalItemLocationCode(localItemLocationCode),
			hasNoLocalItemStatus(),
			hasNoLocalId(),
			hasNoLocalStatus(),
			hasLocalAgencyCode(borrowingAgency.getCode()),
			hasResolvedAgency(borrowingAgency)
		));

		assertSuccessfulResolutionAudit(fetchedPatronRequest, localItemId,
			localItemBarcode, BORROWING_HOST_LMS_CODE, borrowingAgency.getCode());
	}

	@Test
	void shouldExpediteCheckoutWhenPatronFromDifferentLibraryPicksUpItemLocally() {
		// Arrange
		allowOwnLibraryBorrowing();

		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var localBibId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			localBibId, clusterRecord);

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			CIRCULATING_HOST_LMS_CODE, 1, 1, "loanable-item");

		final var localItemId = "365453";
		final var localItemBarcode = "0973656";

		sierraItemsAPIFixture.itemsForBibId(localBibId, List.of(
			availableItem(localItemId, localItemBarcode, ITEM_LOCATION_CODE)
		));

		final var pickupLocation = locationFixture.createPickupLocation(
			agencyFixture.findByCode(SUPPLYING_AGENCY_CODE));

		final var patron = definePatron("187536", "6385731");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCode(pickupLocation.getIdAsString())
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.isExpeditedCheckout(true)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertSuccessfulResolution(fetchedPatronRequest, EXPEDITED_WORKFLOW);

		final var onlySupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		final var expectedAgency = agencyFixture.findByCode(SUPPLYING_AGENCY_CODE);

		assertThat(onlySupplierRequest, allOf(
			notNullValue(),
			hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
			hasLocalItemId(localItemId),
			hasLocalItemBarcode(localItemBarcode),
			hasLocalBibId(localBibId),
			hasLocalItemLocationCode(ITEM_LOCATION_CODE),
			hasNoLocalItemStatus(),
			hasNoLocalId(),
			hasNoLocalStatus(),
			hasLocalAgencyCode(SUPPLYING_AGENCY_CODE),
			hasResolvedAgency(expectedAgency)
		));

		assertSuccessfulResolutionAudit(fetchedPatronRequest, localItemId,
			localItemBarcode, CIRCULATING_HOST_LMS_CODE);
	}

	@Test
	void shouldExcludeItemWhenLocationIsNotMappedToAgency() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			"673634", clusterRecord);

		sierraItemsAPIFixture.itemsForBibId("673634", List.of(
			SierraItem.builder()
				.id("2656456")
				.barcode("6736553266")
				.locationCode("unknown-location")
				.statusCode("-")
				.build()
		));

		final var pickupLocationId = createPickupLocation();

		final var patron = definePatron("872321", "465636");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCode(pickupLocationId)
			.status(PATRON_VERIFIED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertNoItemsSelectableResolution(fetchedPatronRequest);

		assertNoSupplierRequestsFor(patronRequest);
	}

	@Test
	void shouldExcludeItemCirculatingHostLmsIsUnknown() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "265423";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var localItemId = "4275631";
		final var localItemBarcode = "236464423";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			SierraItem.builder()
				.id(localItemId)
				.barcode(localItemBarcode)
				.locationCode("example-location")
				.statusCode("-")
				.build()
		));

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, "example-location", "unknown-circulating-host-lms");

		agencyFixture.defineAgencyWithNoHostLms("unknown-circulating-host-lms",
			"Unknown Circulating Host LMS");

		final var pickupLocationId = createPickupLocation();

		final var patron = definePatron("872321", "465636");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCode(pickupLocationId)
			.status(PATRON_VERIFIED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertNoItemsSelectableResolution(fetchedPatronRequest);

		assertNoSupplierRequestsFor(patronRequest);
	}

	@Test
	void shouldResolveToNoSelectableItemsWhenNoItemsToChooseFrom() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			"245375", clusterRecord);

		sierraItemsAPIFixture.itemsForBibId("245375", emptyList());

		final var pickupLocationId = createPickupLocation();

		final var patron = definePatron("872321", "294385");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCode(pickupLocationId)
			.status(PATRON_VERIFIED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertNoItemsSelectableResolution(fetchedPatronRequest);

		assertNoSupplierRequestsFor(patronRequest);
	}

	@Test
	void shouldFailToResolveRequestWhenClusterRecordCannotBeFound() {
		// Arrange
		final var pickupLocationId = createPickupLocation();

		final var patron = definePatron("86848", "757646");

		final var clusterRecordId = randomUUID();

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode(pickupLocationId)
			.status(PATRON_VERIFIED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var exception = assertThrows(CannotFindClusterRecordException.class,
			() -> resolve(patronRequest));

		// Assert
		final var expectedErrorMessage = "Cannot find cluster record for: " + clusterRecordId;

		assertThat(exception, allOf(
			notNullValue(),
			hasMessage(expectedErrorMessage)
		));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat(fetchedPatronRequest, allOf(
			hasStatus(ERROR),
			hasErrorMessage(expectedErrorMessage),
			hasNoResolutionCount()
		));

		assertThat("Should not find any supplier requests",
			supplierRequestsFixture.findAllFor(patronRequest), hasSize(0));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedErrorMessage);
	}

	@Test
	void shouldResolveToNoSelectableItemsWhenNoBibsForClusterRecord() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), null);

		final var pickupLocationId = createPickupLocation();

		final var patron = definePatron("86848", "757646");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCode(pickupLocationId)
			.status(PATRON_VERIFIED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertNoItemsSelectableResolution(fetchedPatronRequest);

		assertNoSupplierRequestsFor(patronRequest);
	}

	private Patron definePatron(String localId, String homeLibraryCode) {
		return patronFixture.definePatron(localId, homeLibraryCode,
			cataloguingHostLms, agencyFixture.findByCode(BORROWING_AGENCY_CODE));
	}

	private void resolve(PatronRequest patronRequest) {
		singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(attemptTransition()));
	}

	private Function<RequestWorkflowContext, Mono<RequestWorkflowContext>> attemptTransition() {
		return ctx -> Mono.just(ctx.getPatronRequest())
			.flatMap(patronRequestWorkflowService.attemptTransitionWithErrorTransformer(
				patronRequestResolutionStateTransition, ctx));
	}

	private String createPickupLocation() {
		final var agency = agencyFixture.findByCode(BORROWING_AGENCY_CODE);

		final var pickupLocation = locationFixture.createPickupLocation(agency);

		return pickupLocation.getIdAsString();
	}

	private static void assertSuccessfulResolution(PatronRequest request, String expectedWorkflow) {
		assertThat(request, allOf(
			hasStatus(RESOLVED),
			hasResolutionCount(1),
			hasActiveWorkflow(expectedWorkflow)
		));
	}

	private void assertNoItemsSelectableResolution(PatronRequest patronRequest) {
		assertThat(patronRequest, allOf(
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasNoResolutionCount()
		));

		assertThat(patronRequestsFixture.findAuditEntries(patronRequest),
			hasItem(isNoSelectableItemResolutionAudit("Resolution")));
	}

	private void assertNoSupplierRequestsFor(PatronRequest patronRequest) {
		assertThat("Should not find any supplier requests",
			supplierRequestsFixture.findAllFor(patronRequest), empty());
	}

	public void assertSuccessfulResolutionAudit(PatronRequest patronRequest,
		String expectedItemId, String expectedItemBarcode, String expectedHostLms) {
		assertSuccessfulResolutionAudit(patronRequest, expectedItemId,
			expectedItemBarcode, expectedHostLms, SUPPLYING_AGENCY_CODE);
	}

	public void assertSuccessfulResolutionAudit(PatronRequest patronRequest,
		String expectedItemId, String expectedItemBarcode, String expectedHostLms,
		String supplyingAgencyCode) {

		final var fetchedAudit = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat(fetchedAudit, allOf(
			notNullValue(),
			hasBriefDescription("Resolution selected an item with local ID \"%s\" from Host LMS \"%s\""
				.formatted(expectedItemId, expectedHostLms)),
			hasNestedAuditDataProperty("selectedItem", "barcode", expectedItemBarcode),
			hasNestedAuditDataProperty("selectedItem", "requestable", true),
			hasNestedAuditDataProperty("selectedItem", "statusCode", "AVAILABLE"),
			hasNestedAuditDataProperty("selectedItem", "localItemType", "1"),
			hasNestedAuditDataProperty("selectedItem", "canonicalItemType", "loanable-item"),
			hasNestedAuditDataProperty("selectedItem", "holdCount", 0),
			hasNestedAuditDataProperty("selectedItem", "agencyCode", supplyingAgencyCode),
			hasAuditDataProperty("filteredItems"),
			hasAuditDataProperty("sortedItems"),
			hasAuditDataProperty("allItems")
		));
	}

	public void assertUnsuccessfulTransitionAudit(PatronRequest patronRequest, String description) {
		final var fetchedAudit = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat("Patron Request audit should have brief description",
			fetchedAudit.getBriefDescription(),
			is(description));

		// If we failed to look up a bib record then we're moving from patron verified to error
		assertThat("Patron Request audit should have from state ",
			fetchedAudit.getFromStatus(), is(PATRON_VERIFIED));

		assertThat("Patron Request audit should have to state",
			fetchedAudit.getToStatus(), is(ERROR));
	}

	private SierraItem availableItem(String id, String barcode,
		String itemLocationCode) {

		return SierraItem.builder()
			.id(id)
			.barcode(barcode)
			.locationCode(itemLocationCode)
			.statusCode("-")
			// needs to align with NumericRangeMapping
			.itemType("1")
			.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
			.build();
	}

	private void allowOwnLibraryBorrowing() {
		defineSetting(OWN_LIBRARY_BORROWING, true);
	}

	private void defineSetting(FunctionalSettingType settingType, boolean enabled) {
		consortiumFixture.createConsortiumWithFunctionalSetting(settingType, enabled);
	}
}

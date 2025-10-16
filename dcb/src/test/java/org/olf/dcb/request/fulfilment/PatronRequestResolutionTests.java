package org.olf.dcb.request.fulfilment;

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
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasBriefDescription;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasNestedAuditDataProperty;
import static org.olf.dcb.test.matchers.ResolutionAuditMatchers.isNoSelectableItemResolutionAudit;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasActiveWorkflow;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasErrorMessage;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasNoResolutionCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasResolutionCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
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
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;
import org.olf.dcb.request.resolution.UnableToResolvePatronRequest;
import org.olf.dcb.request.workflow.PatronRequestResolutionStateTransition;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
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
class PatronRequestResolutionTests {
	private final String CATALOGUING_HOST_LMS_CODE = "resolution-cataloguing";
	private final String CIRCULATING_HOST_LMS_CODE = "resolution-circulating";
	private final String BORROWING_HOST_LMS_CODE = "resolution-borrowing";

	private final String SUPPLYING_AGENCY_CODE = "supplying-agency";
	private final String BORROWING_AGENCY_CODE = "borrowing-agency";

	private final String PICKUP_LOCATION_CODE = "pickup-location";
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

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	private DataHostLms cataloguingHostLms;

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mockServerClient) {
		log.info("beforeAll\n\n");

		final String HOST_LMS_BASE_URL = "https://resolution-tests.com";
		final String HOST_LMS_TOKEN = "resolution-system-token";
		final String HOST_LMS_KEY = "resolution-system-key";
		final String HOST_LMS_SECRET = "resolution-system-secret";

		SierraTestUtils.mockFor(mockServerClient, HOST_LMS_BASE_URL)
			.setValidCredentials(HOST_LMS_KEY, HOST_LMS_SECRET, HOST_LMS_TOKEN, 60);

		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();

		hostLmsFixture.deleteAll();

		cataloguingHostLms = hostLmsFixture.createSierraHostLms(CATALOGUING_HOST_LMS_CODE, HOST_LMS_KEY,
			HOST_LMS_SECRET, HOST_LMS_BASE_URL, "item");

		hostLmsFixture.createSierraHostLms(CIRCULATING_HOST_LMS_CODE, "",
			"", "http://some-system", "item");

		hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, "",
			"", "http://some-borrowing-system", "item");
	}

	@BeforeEach
	void beforeEach() {
		log.info("beforeEach\n\n");

		clusterRecordFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			BORROWING_HOST_LMS_CODE, PICKUP_LOCATION_CODE, BORROWING_AGENCY_CODE);

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, ITEM_LOCATION_CODE, SUPPLYING_AGENCY_CODE);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_CODE,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		agencyFixture.defineAgency(BORROWING_AGENCY_CODE, BORROWING_AGENCY_CODE,
			hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE));
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

		final var patron = definePatron("872321", "465636");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertSuccessfulResolution(fetchedPatronRequest);

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
			CIRCULATING_HOST_LMS_CODE);
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

		final var patron = definePatron("872321", "465636");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
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

		final var patron = definePatron("872321", "465636");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
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
	void shouldResolveToNoAvailableItemsWhenNoItemsToChooseFrom() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			"245375", clusterRecord);

		sierraItemsAPIFixture.itemsForBibId("245375", emptyList());

		final var patron = definePatron("872321", "294385");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
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
	void shouldFailToResolveVerifiedRequestWhenClusterRecordCannotBeFound() {
		log.info("shouldFailToResolveVerifiedRequestWhenClusterRecordCannotBeFound - entering\n\n");

		// Arrange
		final var patron = definePatron("86848", "757646");

		final var clusterRecordId = randomUUID();

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
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

		log.info("shouldFailToResolveVerifiedRequestWhenClusterRecordCannotBeFound - exiting\n\n");
	}

	@Test
	void shouldFailToResolveVerifiedRequestWhenNoBibsForClusterRecord() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), null);

		final var patron = definePatron("86848", "757646");

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
			.status(PATRON_VERIFIED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var exception = assertThrows(UnableToResolvePatronRequest.class,
			() -> resolve(patronRequest));

		// Assert
		final var expectedErrorMessage = "Cluster record: \"" + clusterRecord.getId() + "\" has no bibs";

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

	private static void assertSuccessfulResolution(PatronRequest fetchedPatronRequest) {
		assertThat(fetchedPatronRequest, allOf(
			hasStatus(RESOLVED),
			hasResolutionCount(1),
			hasActiveWorkflow(STANDARD_WORKFLOW)
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
		String expectedItemId, String expectedHostLms) {

		final var fetchedAudit = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat(fetchedAudit, allOf(
			notNullValue(),
			hasBriefDescription("Resolution selected an item with local ID \"%s\" from Host LMS \"%s\""
				.formatted(expectedItemId, expectedHostLms)),
			hasNestedAuditDataProperty("selectedItem", "barcode", "6565750674"),
			hasNestedAuditDataProperty("selectedItem", "requestable", true),
			hasNestedAuditDataProperty("selectedItem", "statusCode", "AVAILABLE"),
			hasNestedAuditDataProperty("selectedItem", "localItemType", "1"),
			hasNestedAuditDataProperty("selectedItem", "canonicalItemType", "loanable-item"),
			hasNestedAuditDataProperty("selectedItem", "holdCount", 0),
			hasNestedAuditDataProperty("selectedItem", "agencyCode", SUPPLYING_AGENCY_CODE),
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
}

package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.FunctionalSettingType.OWN_LIBRARY_BORROWING;
import static org.olf.dcb.core.model.FunctionalSettingType.RE_RESOLUTION;
import static org.olf.dcb.core.model.PatronRequest.Status.NOT_SUPPLIED_CURRENT_SUPPLIER;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.core.model.WorkflowConstants.LOCAL_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataDetail;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasBriefDescription;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasActiveWorkflow;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasNoResolutionCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasResolutionCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ResolutionAuditMatchers.isNoSelectableItemResolutionAudit;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemBarcode;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemId;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.api.exceptions.MultipleConsortiumException;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.InactiveSupplierRequest;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.InactiveSupplierRequestsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;
import org.olf.dcb.utils.PropertyAccessUtils;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ResolveNextSupplierTransitionTests {
	private static final String BORROWING_HOST_LMS_CODE = "next-supplier-borrowing-tests";
	private static final String SUPPLYING_HOST_LMS_CODE = "next-supplier-tests";
	private static final String PREVIOUSLY_SUPPLYING_HOST_LMS_CODE = "next-supplier-previous-tests";

	private static final String NEWLY_SUPPLYING_AGENCY_CODE = "new-supplying-agency";
	private static final String PREVIOUSLY_SUPPLYING_AGENCY_CODE = "previous-supplying-agency";

	private static final String BORROWING_AGENCY_CODE = "borrowing-agency";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private InactiveSupplierRequestsFixture inactiveSupplierRequestsFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private ConsortiumFixture consortiumFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private LocationFixture locationFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private ResolveNextSupplierTransition resolveNextSupplierTransition;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private DataHostLms borrowingHostLms;
	private DataAgency borrowingAgency;

	private DataHostLms supplyingHostLms;
	private DataAgency supplyingAgency;

	private DataHostLms previouslySupplyingHostLms;
	private DataAgency previouslySupplyingAgency;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final var token = "test-token";
		final var key = "key";
		final var secret = "secret";
		final var supplyingHostLmsBaseUrl = "https://supplying-host-lms.com";
		final var borrowingHostLmsBaseUrl = "https://borrowing-host-lms.com";

		hostLmsFixture.deleteAll();

		SierraTestUtils.mockFor(mockServerClient, supplyingHostLmsBaseUrl)
			.setValidCredentials(key, secret, token, 60);

		SierraTestUtils.mockFor(mockServerClient, borrowingHostLmsBaseUrl)
			.setValidCredentials(key, secret, token, 60);

		borrowingHostLms = hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE,
			key, secret, borrowingHostLmsBaseUrl);

		supplyingHostLms = hostLmsFixture.createSierraHostLms(SUPPLYING_HOST_LMS_CODE,
			key, secret, supplyingHostLmsBaseUrl);

		previouslySupplyingHostLms = hostLmsFixture.createSierraHostLms(
			PREVIOUSLY_SUPPLYING_HOST_LMS_CODE, "anything", "anything", "http://anywhere");

		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);
		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
	}

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		consortiumFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();

		borrowingAgency = agencyFixture.defineAgency(BORROWING_AGENCY_CODE,
			"Borrowing Agency", borrowingHostLms);

		supplyingAgency = agencyFixture.defineAgency(NEWLY_SUPPLYING_AGENCY_CODE,
			"Supplying Agency", supplyingHostLms);

		previouslySupplyingAgency = agencyFixture.defineAgency(PREVIOUSLY_SUPPLYING_AGENCY_CODE,
			"Previously Supplying Agency", previouslySupplyingHostLms);
	}

	@Test
	void shouldCancelRequestWhenSettingIsDisabled() {
		// Arrange
		disableReResolution();

		final var borrowingLocalRequestId = "4656352";

		final var patron = patronFixture.definePatron("265635", "home-library",
			borrowingHostLms, borrowingAgency);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			/*
			 This is important to include to trigger the audit service adding additional data
			 to the audit details, which is how this will be used in practice
			*/
			.previousStatus(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.status(NOT_SUPPLIED_CURRENT_SUPPLIER)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.localRequestId(borrowingLocalRequestId)
			.localRequestStatus(HOLD_CONFIRMED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var supplierRequest = defineCancelledSupplierRequest(patronRequest);

		sierraPatronsAPIFixture.mockDeleteHold(borrowingLocalRequestId);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasNoResolutionCount()
		));

		assertThat("Previous supplier request should still exist",
			supplierRequestsFixture.exists(supplierRequest.getId()), is(true));

		assertThat("There should be no inactive supplier requests",
			inactiveSupplierRequestsFixture.findAllFor(updatedPatronRequest), is(emptyIterable()));

		assertThat(patronRequestsFixture.findAuditEntries(patronRequest), contains(
			isNotRequiredAuditEntry("Consortial setting is not enabled")));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(borrowingLocalRequestId);
	}

	@ParameterizedTest
	@ValueSource(strings = {HOLD_MISSING, HOLD_CANCELLED})
	void shouldNotCancelBorrowingRequestWhenMissingOrCancelled(String localRequestStatus) {
		// Arrange
		enableReResolution();

		final var borrowingLocalRequestId = "7836734";

		final var patron = patronFixture.definePatron("783174", "home-library",
			borrowingHostLms, borrowingAgency);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(NOT_SUPPLIED_CURRENT_SUPPLIER)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.localRequestId(borrowingLocalRequestId)
			.localRequestStatus(localRequestStatus)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var supplierRequest = defineCancelledSupplierRequest(patronRequest);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasNoResolutionCount()
		));

		assertThat("Previous supplier request should still exist",
			supplierRequestsFixture.exists(supplierRequest.getId()), is(true));

		assertThat("There should be no inactive supplier requests",
			inactiveSupplierRequestsFixture.findAllFor(updatedPatronRequest), is(emptyIterable()));

		assertThat(patronRequestsFixture.findAuditEntries(patronRequest),
			hasItem(isNoSelectableItemResolutionAudit("Re-resolution")));

		sierraPatronsAPIFixture.verifyNoDeleteHoldRequestMade(borrowingLocalRequestId);
	}

	@Test
	void shouldNotCancelBorrowingRequestWhenRequestHasNotBeenPlacedYet() {
		// Arrange
		final var patron = patronFixture.definePatron("252556", "home-library",
			borrowingHostLms, borrowingAgency);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(NOT_SUPPLIED_CURRENT_SUPPLIER)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.localRequestId(null)
			.localRequestStatus(null)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var supplierRequest = defineCancelledSupplierRequest(patronRequest);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasNoResolutionCount()
		));

		assertThat("Previous supplier request should still exist",
			supplierRequestsFixture.exists(supplierRequest.getId()), is(true));

		assertThat("There should be no inactive supplier requests",
			inactiveSupplierRequestsFixture.findAllFor(updatedPatronRequest), is(emptyIterable()));

		assertThat(patronRequestsFixture.findAuditEntries(patronRequest), contains(
			isNotRequiredAuditEntry("Consortial setting is not enabled"),
			allOf(
				notNullValue(),
				hasBriefDescription("Could not cancel local borrowing request because no local ID is known (ID: \"%s\")"
					.formatted(patronRequest.getId()))
			)
		));

		sierraPatronsAPIFixture.verifyNoDeleteHoldRequestMade();
	}

	@Test
	void shouldNotApplyWhenItemHasBeenDispatchedForPickup() {
		// Arrange
		final var sourceRecordId = "165452";
		final var clusterRecordId = defineClusterRecordWithSingleBib(sourceRecordId);

		final var patronRequest = definePatronRequest(PICKUP_TRANSIT, "3635625",
			clusterRecordId);

		defineCancelledSupplierRequest(patronRequest);

		// Act
		final var applicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable after item has been dispatched",
			applicable, is(false));
	}

	@Test
	void shouldTolerateNullPatronRequestStatus() {
		// Arrange
		final var sourceRecordId = "6775354";
		final var clusterRecordId = defineClusterRecordWithSingleBib(sourceRecordId);

		final var patronRequest = definePatronRequest(null, "3635625",
			clusterRecordId);

		// Act
		final var applicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable for request with no status",
			applicable, is(false));
	}

	@Test
	void shouldFailWhenPatronIsNotAssociatedWithHostLms() {
		// Arrange
		final var borrowingLocalRequestId = "6726357";

		final var patron = patronFixture.definePatron("264535", "home-library",
			null, null);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(NOT_SUPPLIED_CURRENT_SUPPLIER)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.localRequestId(borrowingLocalRequestId)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		defineCancelledSupplierRequest(patronRequest);

		// Act
		final var error = assertThrows(RuntimeException.class,
			() -> resolveNextSupplier(patronRequest));

		// Assert
		assertThat(error, allOf(
			notNullValue(),
			hasMessage("Patron is not associated with a Host LMS")
		));

		sierraPatronsAPIFixture.verifyNoDeleteHoldRequestMade(borrowingLocalRequestId);
	}

	@Test
	void shouldSelectNewSupplierWhenAnItemFromDifferentSupplierIsSelectable() {
		enableReResolution();

		final var sourceRecordId = "798472";
		final var clusterRecordId = defineClusterRecordWithSingleBib(sourceRecordId);

		final var borrowingLocalRequestId = "3635625";
		final var patronRequest = definePatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
			borrowingLocalRequestId, clusterRecordId);

		final var supplierRequest = saveSupplierRequest(patronRequest,
			previouslySupplyingHostLms.getCode(), previouslySupplyingAgency);

		final var newItemId = "1000002";
		final var newItemBarcode = "6565750674";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			SierraItem.builder()
				.id(newItemId)
				.barcode(newItemBarcode)
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("999")
				.locationCode("ab6")
				.locationName("King 6th Floor")
				.suppressed(false)
				.deleted(false)
				.build()));

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			supplyingHostLms.getCode(), "ab6",  supplyingAgency.getCode());

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			supplyingHostLms.getCode(), 999, 999, "BKM");

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(RESOLVED),
			hasResolutionCount(2),
			hasActiveWorkflow(STANDARD_WORKFLOW)
		));

		final var newSupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat("New supplier request should have been created", newSupplierRequest, allOf(
			notNullValue(),
			hasLocalItemId(newItemId),
			hasLocalItemBarcode(newItemBarcode)
		));

		assertThat("Previous supplier request should no longer exist",
			supplierRequestsFixture.exists(supplierRequest.getId()), is(false));

		final var listOfInactiveSupplierRequests = inactiveSupplierRequestsFixture.findAllFor(updatedPatronRequest);
		final var oneInactiveSupplierRequest = listOfInactiveSupplierRequests.get(0);

		assertThat(oneInactiveSupplierRequest, notNullValue());
		assertThat("Inactive supplier request should exist and match previous supplier request",
			oneInactiveSupplierRequest.getLocalId(), is(supplierRequest.getLocalId()));

		final var onlyAuditEntry = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat(onlyAuditEntry, isResolutionAuditEntry(newItemId, supplyingHostLms.getCode()));
	}

	@Test
	void shouldExcludeItemsFromAgenciesThatHavePreviouslySupplied() {
		/*
		 This is a rather artificial set up (usually a Host LMS only maps to a single agency)

		 To demonstrate that all agencies that had previously had been asked to supply are excluded
		 multiple agencies have to be involved:
				* initially asked to supply (first, is now inactive)
				* previously asked to supply (second, is still active when the transition is triggered)

		 The sole newly supplying host LMS provides items from all of these agencies

		 It is intended to avoid some of the complexity of setting up multiple Host LMS,
		 source records and mock expectations

		It checks for unsuccessful re-resolution because that should be more stable than
		defining an included item because that might pass incorrectly due to item ranking.
		This is a trade-off because it's also possible that an item is excluded due to missing configuration

		 A possible future improvement could be to expand the configurability
		 of the dummy Host LMS to make it easier to define more complicated host LMS client outputs
		*/

		enableReResolution();

		final var sourceRecordId = "236455";
		final var clusterRecordId = defineClusterRecordWithSingleBib(sourceRecordId);

		final var borrowingLocalRequestId = "9873653";

		final var patronRequest = definePatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
			borrowingLocalRequestId, clusterRecordId);

		final var initialSupplyingAgency = agencyFixture.defineAgency(
			"initial-supplying-agency", "Initial Supplying Agency", previouslySupplyingHostLms);

		inactiveSupplierRequestsFixture.save(InactiveSupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.localItemId("7916922")
			.hostLmsCode(supplyingHostLms.getCode())
			.resolvedAgency(initialSupplyingAgency)
			.build());

		final var supplierRequest = saveSupplierRequest(patronRequest,
			supplyingHostLms.getCode(), previouslySupplyingAgency);

		final var initialSupplierLocationCode = "initial-supplying-location";
		final var previousSupplierLocationCode = "previous-supplying-location";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			SierraItem.builder()
				.id("2015843")
				.barcode("26553255")
				.statusCode("-")
				.itemType("999")
				.locationCode(previousSupplierLocationCode)
				.locationName("Same agency as previous supplier")
				.suppressed(false)
				.deleted(false)
				.build(),
			SierraItem.builder()
				.id("6737264")
				.barcode("1084525")
				.statusCode("-")
				.itemType("999")
				.locationCode(initialSupplierLocationCode)
				.locationName("Same agency as initial supplier")
				.suppressed(false)
				.deleted(false)
				.build()
		));

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			supplyingHostLms.getCode(), previousSupplierLocationCode, previouslySupplyingAgency.getCode());

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			supplyingHostLms.getCode(), initialSupplierLocationCode, initialSupplyingAgency.getCode());

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			previouslySupplyingHostLms.getCode(), 999, 999, "BKM");

		sierraPatronsAPIFixture.mockDeleteHold(borrowingLocalRequestId);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasResolutionCount(1)
		));

		assertThat("Previous supplier request should still exist",
			supplierRequestsFixture.exists(supplierRequest.getId()), is(true));

		assertThat("There should still be only 1 inactive supplier request",
			inactiveSupplierRequestsFixture.findAllFor(patronRequest), hasSize(1));

		assertThat(patronRequestsFixture.findAuditEntries(patronRequest),
			hasItem(isNoSelectableItemResolutionAudit("Re-resolution")));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(borrowingLocalRequestId);
	}

	@Test
	void shouldChangeWorkflowTypeWhenAnLocalToBorrowerItemIsSelected() {
		/*
		 This is a rather artificial set up (usually a Host LMS only maps to a single agency)

		 To demonstrate that the workflow should change because a local item has now been selected
		 (or vice versa) then item provided by the mock Host LMS needs to be artificially mapped
		 to a different agency, even
		*/

		final var consortium = consortiumFixture.createConsortium();

		consortiumFixture.enableSetting(consortium, RE_RESOLUTION);
		consortiumFixture.enableSetting(consortium, OWN_LIBRARY_BORROWING);

		final var sourceRecordId = "5746627";
		final var clusterRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(borrowingHostLms.getCode());

		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(randomUUID(), sourceSystemId,
			sourceRecordId, clusterRecord);

		final var borrowingLocalRequestId = "3725585";

		final var patronRequest = definePatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
			borrowingLocalRequestId, clusterRecordId);

		final var supplierRequest = saveSupplierRequest(patronRequest,
			previouslySupplyingHostLms.getCode(), previouslySupplyingAgency);

		final var newItemId = "673764";
		final var newItemBarcode = "2856468";

		final var borrowingAgencyLocation = "borrowing-agency-code";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			SierraItem.builder()
				.id(newItemId)
				.barcode(newItemBarcode)
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("999")
				.locationCode(borrowingAgencyLocation)
				.locationName("Borrowing Agency Location")
				.suppressed(false)
				.deleted(false)
				.build()));

		// Map item location to borrowing agency to demonstrate local workflow
		referenceValueMappingFixture.defineLocationToAgencyMapping(
			borrowingHostLms.getCode(), borrowingAgencyLocation,  borrowingAgency.getCode());

		// Is based upon the Host LMS from the agency mapping, so has to be borrowing agency
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			borrowingHostLms.getCode(), 999, 999, "BKM");

		// Temporary to allow test to progress
		sierraPatronsAPIFixture.mockDeleteHold(borrowingLocalRequestId);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(RESOLVED),
			hasResolutionCount(2),
			hasActiveWorkflow(LOCAL_WORKFLOW)
		));

		final var newSupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat("New supplier request should have been created", newSupplierRequest, allOf(
			notNullValue(),
			hasLocalItemId(newItemId),
			hasLocalItemBarcode(newItemBarcode)
		));

		assertThat("Previous supplier request should no longer exist",
			supplierRequestsFixture.exists(supplierRequest.getId()), is(false));

		final var listOfInactiveSupplierRequests = inactiveSupplierRequestsFixture.findAllFor(updatedPatronRequest);
		final var oneInactiveSupplierRequest = listOfInactiveSupplierRequests.get(0);

		assertThat(oneInactiveSupplierRequest, notNullValue());
		assertThat("Inactive supplier request should exist and match previous supplier request",
			oneInactiveSupplierRequest.getLocalId(), is(supplierRequest.getLocalId()));

		final var auditEntries = patronRequestsFixture.findAuditEntries(patronRequest);

		assertThat(auditEntries, hasItem(isResolutionAuditEntry(newItemId, borrowingHostLms.getCode())));
	}

	@Test
	void shouldCancelBorrowingRequestWhenNoItemSelectableDuringReResolution() {
		enableReResolution();

		final var sourceRecordId = "798475";
		final var clusterRecordId = defineClusterRecordWithSingleBib(sourceRecordId);

		final var hostLms = hostLmsFixture.findByCode(supplyingHostLms.code);
		final var borrowingLocalRequestId = "3635625";
		final var patronRequest = definePatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
			borrowingLocalRequestId, clusterRecordId);

		final var supplierRequest = saveSupplierRequest(patronRequest, hostLms.getCode(),
			previouslySupplyingAgency);

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			SierraItem.builder()
				.id("1000003")
				.barcode("6565750674")
				.callNumber("BL221 .C48")
				.statusCode("-")
				.dueDate(Instant.parse("2021-02-25T12:00:00Z")) // This item is not selectable
				.itemType("999")
				.locationCode("ab6")
				.locationName("King 6th Floor")
				.suppressed(false)
				.deleted(false)
				.build()));

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			supplyingHostLms.code, "ab6",  supplyingAgency.getCode());

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			supplyingHostLms.code, 999, 999, "BKM");

		sierraPatronsAPIFixture.mockDeleteHold(borrowingLocalRequestId);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasResolutionCount(1)
		));

		assertThat("Previous supplier request should still exist",
			supplierRequestsFixture.exists(supplierRequest.getId()), is(true));

		assertThat("There should be no inactive supplier requests",
			inactiveSupplierRequestsFixture.findAllFor(updatedPatronRequest), is(emptyIterable()));

		assertThat(patronRequestsFixture.findAuditEntries(patronRequest),
			hasItem(isNoSelectableItemResolutionAudit("Re-resolution")));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(borrowingLocalRequestId);
	}

	@Test
	void shouldCancelRequestWhenItemWasManuallySelected() {
		enableReResolution();

		final var sourceRecordId = "798475";
		final var clusterRecordId = defineClusterRecordWithSingleBib(sourceRecordId);

		final var hostLms = hostLmsFixture.findByCode(supplyingHostLms.code);

		final var borrowingLocalRequestId = "3635625";

		final var patron = patronFixture.definePatron("365636", "home-library",
			borrowingHostLms, borrowingAgency);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(NOT_SUPPLIED_CURRENT_SUPPLIER)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.patronHostlmsCode(borrowingHostLms.getCode())
			.bibClusterId(clusterRecordId)
			.resolutionCount(1)
			.isManuallySelectedItem(true)
			// By the time re-resolution is triggered, the manual selection information
			// has been replaced by the local borrowing request information
			.localRequestId(borrowingLocalRequestId)
			// This is artificial for a Sierra borrowing system
			// However, it is possible for a FOLIO borrowing system,
			// because DCB does not find out the virtual item information from mod-dcb
			// Which would trigger a validation failure for the manual selection info
			// If we progressed resolution during this transition
			.localItemId(null)
			.localItemHostlmsCode(borrowingAgency.getCode())
			.localItemAgencyCode(borrowingAgency.getCode())
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var supplierRequest = saveSupplierRequest(patronRequest, hostLms.getCode(),
			previouslySupplyingAgency);

		sierraItemsAPIFixture.zeroItemsResponseForBibId(sourceRecordId);

		sierraPatronsAPIFixture.mockDeleteHold(borrowingLocalRequestId);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasResolutionCount(1)
		));

		assertThat("Previous supplier request should still exist",
			supplierRequestsFixture.exists(supplierRequest.getId()), is(true));

		assertThat("There should be no inactive supplier requests",
			inactiveSupplierRequestsFixture.findAllFor(updatedPatronRequest), is(emptyIterable()));

		assertThat(patronRequestsFixture.findAuditEntries(patronRequest), contains(
			isNotRequiredAuditEntry("Item manually selected")));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(borrowingLocalRequestId);
	}

	@Test
	void shouldFailWhenMoreThanOneConsortiumIsDefined() {
		enableReResolution();
		enableReResolution();

		final var clusterRecordId = randomUUID();
		final var borrowingLocalRequestId = "3635625";
		final var patronRequest = definePatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
			borrowingLocalRequestId, clusterRecordId);

		// Act
		final var error = assertThrows(MultipleConsortiumException.class,
			() -> resolveNextSupplier(patronRequest));

		// Assert
		assertThat(error, allOf(
			notNullValue(),
			hasMessage("Multiple Consortium found when only one was expected. Found: 2")
		));
	}

	private PatronRequest resolveNextSupplier(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> {
				if (!resolveNextSupplierTransition.isApplicableFor(ctx)) {
					return Mono.error(new RuntimeException("Resolve next supplier is not applicable for request"));
				}

				return resolveNextSupplierTransition.attempt(ctx);
			})
			.thenReturn(patronRequest));
	}

	private Boolean isApplicable(PatronRequest patronRequest) {
		return singleValueFrom(
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.map(ctx -> resolveNextSupplierTransition.isApplicableFor(ctx)));
	}

	private static Matcher<PatronRequestAudit> isNotRequiredAuditEntry(String expectedReason) {
		return allOf(
			notNullValue(),
			hasBriefDescription("Re-resolution not required"),
			hasAuditDataDetail(expectedReason)
		);
	}

	private static Matcher<PatronRequestAudit> isResolutionAuditEntry(
		String expectedNewItemId, String expectedSupplyingHostLmsCode) {

		return allOf(
			hasBriefDescription("Re-resolution selected an item with local ID \"%s\" from Host LMS \"%s\""
				.formatted(expectedNewItemId, expectedSupplyingHostLmsCode)),
			hasAuditDataProperty("selectedItem"),
			hasAuditDataProperty("filteredItems"),
			hasAuditDataProperty("sortedItems"),
			hasAuditDataProperty("allItems")
		);
	}

	private PatronRequest definePatronRequest(PatronRequest.Status status,
		String localRequestId, UUID clusterRecordId) {

		final var pickupLocation = locationFixture.createPickupLocation(borrowingAgency);

		final var pickupLocationId = PropertyAccessUtils.getValueOrNull(
			pickupLocation, Location::getId, UUID::toString);

		final var patron = patronFixture.definePatron("365636", "home-library",
			borrowingHostLms, borrowingAgency);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(status)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.patronHostlmsCode(borrowingHostLms.getCode())
			.localRequestId(localRequestId)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode(pickupLocationId)
			.resolutionCount(1)
			.activeWorkflow(STANDARD_WORKFLOW)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		return patronRequest;
	}

	private SupplierRequest defineCancelledSupplierRequest(PatronRequest patronRequest) {
		return supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode("next-supplier-supplying-host-lms")
			.localItemId("48375735")
			.localStatus(HOLD_CANCELLED)
			.build());
	}

	private UUID defineClusterRecordWithSingleBib(String sourceRecordId) {
		final var clusterRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(supplyingHostLms.getCode());

		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(randomUUID(), sourceSystemId,
			sourceRecordId, clusterRecord);

		return clusterRecordId;
	}

	private SupplierRequest saveSupplierRequest(PatronRequest patronRequest,
		String hostLmsCode, DataAgency previousSupplyingAgency) {

		return supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.patronRequest(patronRequest)
				.localItemId("7916922")
				.localBibId("563653")
				.localItemLocationCode(NEWLY_SUPPLYING_AGENCY_CODE)
				.localItemBarcode("9849123490")
				.hostLmsCode(hostLmsCode)
				.isActive(true)
				.localItemLocationCode("supplying-location")
				.resolvedAgency(previousSupplyingAgency)
				.build());
	}

	private void enableReResolution() {
		consortiumFixture.enableSetting(RE_RESOLUTION);
	}

	private void disableReResolution() {
		consortiumFixture.disableSetting(RE_RESOLUTION);
	}
}

package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.FunctionalSettingType.RE_RESOLUTION;
import static org.olf.dcb.core.model.PatronRequest.Status.NOT_SUPPLIED_CURRENT_SUPPLIER;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasBriefDescription;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasNoResolutionCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasResolutionCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemBarcode;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemId;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.InactiveSupplierRequestsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

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

	private static final String SUPPLYING_AGENCY_CODE = "supplying-agency";
	private static final String PREVIOUSLY_SUPPLYING_AGENCY_CODE = "previous-agency";

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
		inactiveSupplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		consortiumFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();

		borrowingAgency = agencyFixture.defineAgency("borrowing-agency",
			"Borrowing Agency", borrowingHostLms);

		supplyingAgency = agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE,
			"Supplying Agency", supplyingHostLms);

		previouslySupplyingAgency = agencyFixture.defineAgency(PREVIOUSLY_SUPPLYING_AGENCY_CODE,
			"Previously Supplying Agency", previouslySupplyingHostLms);
	}

	@Test
	void shouldProgressRequestWhenSupplierHasCancelled() {
		// Arrange
		final var borrowingLocalRequestId = "3635625";

		final var patronRequest = definePatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
			borrowingLocalRequestId);

		defineSupplierRequest(patronRequest, HOLD_CANCELLED);

		sierraPatronsAPIFixture.mockDeleteHold(borrowingLocalRequestId);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasNoResolutionCount()
		));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(borrowingLocalRequestId);
	}

	@Test
	void shouldCancelLocalBorrowingRequestWhenReResolutionIsNotSupported() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(RE_RESOLUTION, true);

		final var borrowingLocalRequestId = "3635625";

		final var patronRequest = definePatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
			borrowingLocalRequestId);

		defineSupplierRequest(patronRequest, HOLD_CANCELLED);

		sierraPatronsAPIFixture.mockDeleteHold(borrowingLocalRequestId);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasNoResolutionCount()
		));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(borrowingLocalRequestId);
	}

	@ParameterizedTest
	@ValueSource(strings = {HOLD_MISSING, HOLD_CANCELLED})
	void shouldNotCancelBorrowingRequestWhenMissingOrCancelled(String localRequestStatus) {
		// Arrange
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

		defineSupplierRequest(patronRequest, HOLD_CANCELLED);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasNoResolutionCount()
		));

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

		defineSupplierRequest(patronRequest, HOLD_CANCELLED);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasNoResolutionCount()
		));

		assertThat(patronRequestsFixture.findOnlyAuditEntry(patronRequest), allOf(
			notNullValue(),
			hasBriefDescription("Could not cancel local borrowing request because no local ID is known (ID: \"%s\")"
				.formatted(patronRequest.getId()))
		));

		sierraPatronsAPIFixture.verifyNoDeleteHoldRequestMade();
	}

	@Test
	void shouldNotApplyWhenItemHasBeenDispatchedForPickup() {
		// Arrange
		final var patronRequest = definePatronRequest(PICKUP_TRANSIT, "3635625");

		defineSupplierRequest(patronRequest, HOLD_CANCELLED);

		// Act
		final var applicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable after item has been dispatched",
			applicable, is(false));
	}

	@Test
	void shouldTolerateNullPatronRequestStatus() {
		// Arrange
		final var patronRequest = definePatronRequest(null, "3635625");

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

		defineSupplierRequest(patronRequest, HOLD_CANCELLED);

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
		consortiumFixture.createConsortiumWithFunctionalSetting(RE_RESOLUTION, true);

		final var clusterRecordId = randomUUID();
		final var sourceRecordId = "798472";
		defineClusterRecordWithSingleBib(clusterRecordId, sourceRecordId);

		final var borrowingLocalRequestId = "3635625";
		final var patronRequest = defineReResolutionPatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
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
			hasResolutionCount(2)
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
	}

	@Test
	void shouldExcludeItemsFromSameAgencyAsPreviouslySupplied() {
		consortiumFixture.createConsortiumWithFunctionalSetting(RE_RESOLUTION, true);

		final var clusterRecordId = randomUUID();
		final var sourceRecordId = "236455";
		defineClusterRecordWithSingleBib(clusterRecordId, sourceRecordId);
		final var borrowingLocalRequestId = "9873653";

		final var patronRequest = defineReResolutionPatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
			borrowingLocalRequestId, clusterRecordId);

		saveSupplierRequest(patronRequest, supplyingHostLms.getCode(), previouslySupplyingAgency);

		final var itemLocationCode = "same-as-previous-supplier";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			SierraItem.builder()
				.id("2015843")
				.barcode("26553255")
				.statusCode("-")
				.itemType("999")
				.locationCode(itemLocationCode)
				.locationName("Same agency as previous supplier")
				.suppressed(false)
				.deleted(false)
				.build()));

		// This is a little artificial as the item comes from a different Host LMS
		// but the location refers to the previous supplying agency
		referenceValueMappingFixture.defineLocationToAgencyMapping(
			supplyingHostLms.getCode(), itemLocationCode, previouslySupplyingAgency.getCode());

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			previouslySupplyingHostLms.getCode(), 999, 999, "BKM");

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasResolutionCount(2)
		));
	}

	@Test
	void shouldCancelBorrowingRequestWhenNoItemSelectableDuringReResolution() {
		final var savedConsortium = consortiumFixture.createConsortiumWithFunctionalSetting(RE_RESOLUTION, true);

		final var clusterRecordId = randomUUID();
		final var sourceRecordId = "798475";
		defineClusterRecordWithSingleBib(clusterRecordId, sourceRecordId);
		final var hostLms = hostLmsFixture.findByCode(supplyingHostLms.code);
		final var borrowingLocalRequestId = "3635625";
		final var patronRequest = defineReResolutionPatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
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

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasResolutionCount(2)
		));

		final var existingSupplierRequest = supplierRequestsFixture.exists(supplierRequest.getId());

		assertThat(savedConsortium, notNullValue());
		assertThat(existingSupplierRequest, notNullValue());
		assertThat("Previous supplier request should not exist", existingSupplierRequest, is(false));

		final var listOfInactiveSupplierRequests = inactiveSupplierRequestsFixture.findAllFor(updatedPatronRequest);
		final var oneInactiveSupplierRequest = listOfInactiveSupplierRequests.get(0);

		assertThat(oneInactiveSupplierRequest, notNullValue());
		assertThat("Inactive supplier request should exist and match previous supplier request",
			oneInactiveSupplierRequest.getLocalId(), is(supplierRequest.getLocalId()));
	}

	@Test
	void shouldFailWhenMoreThanOneConsortiumIsDefined() {
		consortiumFixture.createConsortiumWithFunctionalSetting(RE_RESOLUTION, true);
		consortiumFixture.createConsortiumWithFunctionalSetting(RE_RESOLUTION, true);

		final var clusterRecordId = randomUUID();
		final var borrowingLocalRequestId = "3635625";
		final var patronRequest = defineReResolutionPatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
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

	private PatronRequest definePatronRequest(PatronRequest.Status status,
		String localRequestId) {

		final var patron = patronFixture.definePatron("365636", "home-library",
			borrowingHostLms, borrowingAgency);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(status)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.localRequestId(localRequestId)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		return patronRequest;
	}

	private PatronRequest defineReResolutionPatronRequest(
		PatronRequest.Status status, String localRequestId, UUID clusterRecordId) {

		final var patron = patronFixture.definePatron("365636", "home-library",
			borrowingHostLms, borrowingAgency);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(status)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.patronHostlmsCode(borrowingHostLms.code)
			.localRequestId(localRequestId)
			.bibClusterId(clusterRecordId)
			.resolutionCount(1)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		return patronRequest;
	}

	private SupplierRequest defineSupplierRequest(PatronRequest patronRequest, String localStatus) {
		return supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode("next-supplier-supplying-host-lms")
			.localItemId("48375735")
			.localStatus(localStatus)
			.build());
	}

	private void defineClusterRecordWithSingleBib(UUID clusterRecordId, String sourceRecordId) {
		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(supplyingHostLms.code);

		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(randomUUID(), sourceSystemId,
			sourceRecordId, clusterRecord);
	}

	private SupplierRequest saveSupplierRequest(PatronRequest patronRequest,
		String hostLmsCode, DataAgency previousSupplyingAgency) {

		return supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest
				.builder()
				.id(randomUUID())
				.patronRequest(patronRequest)
				.localItemId("7916922")
				.localBibId("563653")
				.localItemLocationCode(SUPPLYING_AGENCY_CODE)
				.localItemBarcode("9849123490")
				.hostLmsCode(hostLmsCode)
				.isActive(true)
				.localItemLocationCode("supplying-location")
				.resolvedAgency(previousSupplyingAgency)
				.build());
	}
}

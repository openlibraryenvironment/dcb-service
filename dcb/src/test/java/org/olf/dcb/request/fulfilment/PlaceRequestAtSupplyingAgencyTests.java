package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.WorkflowConstants.LOCAL_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.briefDescriptionContains;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataDetail;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasFromStatus;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasNestedAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasToStatus;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasErrorMessage;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasId;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemBarcode;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasJsonResponseBodyProperty;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestMethod;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestUrl;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;
import static org.olf.dcb.test.matchers.interaction.ProblemMatchers.hasDetail;
import static org.olf.dcb.test.matchers.interaction.ProblemMatchers.hasTitle;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.request.workflow.PlacePatronRequestAtSupplyingAgencyStateTransition;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PlaceRequestAtSupplyingAgencyTests {
	private static final String HOST_LMS_CODE = "supplying-agency-service-tests";

	private static final String SUPPLYING_AGENCY_CODE = "supplying-agency";
	private static final String BORROWING_AGENCY_CODE = "borrowing-agency";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private PlacePatronRequestAtSupplyingAgencyStateTransition placePatronRequestAtSupplyingAgencyStateTransition;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private PatronService patronService;
	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private PatronRequestWorkflowService patronRequestWorkflowService;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;
	private DataAgency supplyingAgency;

	@BeforeEach
	public void beforeEach(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://supplying-agency-service-tests.com";
		final String KEY = "supplying-agency-service-key";
		final String SECRET = "supplying-agency-service-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();

		final var sierraHostLms = hostLmsFixture.createSierraHostLms(HOST_LMS_CODE,
			KEY, SECRET, BASE_URL, "title");

		supplyingAgency = agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE,
			"Supplying Agency", sierraHostLms);

		// Any agency associated with a pickup location MUST also be associated with a host LMS
		agencyFixture.defineAgency(BORROWING_AGENCY_CODE, "Borrowing Agency", sierraHostLms);

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		// add patron type mappings
		savePatronTypeMappings();
	}

	@DisplayName("patron is known to supplier and places patron request with the unexpected patron type")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsKnownToSupplierWithAnUnexpectedPtype() {
		// Arrange
		final var localId = "872321";
		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		final var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.getCode());

		// NOTE: test behaviour means calling the same patronsQueryFoundResponse method with the same local id will cause confusion
		// a workaround used here is to use different local ids to differentiate the mock requests/responses
		final var WORKAROUND_LOCAL_ID = "1000003";
		sierraPatronsAPIFixture.patronsQueryFoundResponse("872321@supplying-agency", WORKAROUND_LOCAL_ID);
		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(WORKAROUND_LOCAL_ID,
			SierraPatronsAPIFixture.Patron.builder()
				.id(1000002)
				.patronType(22)
				.names(List.of("Joe Bloggs"))
				.homeLibraryCode("testbbb")
				.build());

		// The unexpected patron type will trigger a request to update the virtual patron
		sierraPatronsAPIFixture.updatePatron("1000002");
		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse("1000002",
			SierraPatronsAPIFixture.Patron.builder()
				.id(1000002)
				.patronType(15)
				.homeLibraryCode("testccc")
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		final var localItemId = "45736543";

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest("1000002", "b", 563653);

		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold("1000002",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864904",
			"Consortial Hold. tno="+patronRequest.getId(), localItemId);

		sierraItemsAPIFixture.mockGetItemById(localItemId,
			SierraItem.builder()
				.id(localItemId)
				.barcode("67324231")
				.statusCode("-")
				.build());

		// Act
		final var placedPatronRequest = placeAtSupplyingAgency(patronRequest);

		// Assert
		patronRequestWasPlaced(placedPatronRequest, patronRequestId);

		final var updatedSupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		// assertThat(updatedSupplierRequest, allOf(
		// 	notNullValue(),
		// 	hasLocalItemBarcode("67324231")
		// ));

		sierraPatronsAPIFixture.verifyPatronQueryRequestMade("872321@%s".formatted(SUPPLYING_AGENCY_CODE));
		sierraPatronsAPIFixture.verifyCreatePatronRequestNotMade("872321@%s".formatted(SUPPLYING_AGENCY_CODE));
		sierraPatronsAPIFixture.verifyUpdatePatronRequestMade("1000002");

		sierraPatronsAPIFixture.verifyPlaceHoldRequestMade("1000002", "b",
			563653, BORROWING_AGENCY_CODE,
			"Consortial Hold. tno=" + patronRequest.getId()+" \nFor 8675309012@%s\n Pickup MISSING-PICKUP-LIB@MISSING-PICKUP-LOCATION"
				.formatted(SUPPLYING_AGENCY_CODE));
	}

	@DisplayName("patron is known to supplier and places patron request with the expected patron type")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsKnownToSupplierWithTheExpectedPtype() {
		// Arrange
		final var localId = "32453";
		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		final var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.getCode());

		sierraPatronsAPIFixture.patronsQueryFoundResponse("32453@%s".formatted(SUPPLYING_AGENCY_CODE), "1000002");
		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse("1000002",
			SierraPatronsAPIFixture.Patron.builder()
				.id(1000002)
				.patronType(15)
				.homeLibraryCode("testccc")
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		final var localItemId = "36746267";

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest("1000002", "b", 563653);

		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold("1000002",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864904",
			"Consortial Hold. tno="+patronRequest.getId(), localItemId);

		sierraItemsAPIFixture.mockGetItemById(localItemId,
			SierraItem.builder()
				.id(localItemId)
				.barcode("67324231")
				.statusCode("-")
				// needs to align with NumericRangeMapping
				.itemType("1")
				.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
				.build());

		// Act
		final var placedPatronRequest = placeAtSupplyingAgency(patronRequest);

		// Assert
		patronRequestWasPlaced(placedPatronRequest, patronRequestId);

		final var updatedSupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat(updatedSupplierRequest, allOf(
			notNullValue(),
			hasLocalItemBarcode("67324231")
		));

		sierraPatronsAPIFixture.verifyPatronQueryRequestMade("32453@%s".formatted(SUPPLYING_AGENCY_CODE));
		sierraPatronsAPIFixture.verifyCreatePatronRequestNotMade("32453@%s".formatted(SUPPLYING_AGENCY_CODE));
		sierraPatronsAPIFixture.verifyUpdatePatronRequestNotMade("1000002");

		sierraPatronsAPIFixture.verifyPlaceHoldRequestMade("1000002", "b",
			563653, BORROWING_AGENCY_CODE,
			"Consortial Hold. tno=%s \nFor 8675309012@%s\n Pickup MISSING-PICKUP-LIB@MISSING-PICKUP-LOCATION".formatted(
				patronRequest.getId(), SUPPLYING_AGENCY_CODE));
	}

	@DisplayName("patron is not known to supplier and places patron request")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsNotKnownToSupplier() {
		// Arrange
		final var localId = "546730";
		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		final var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.getCode());

		sierraPatronsAPIFixture.patronsQueryNotFoundResponse("546730@%s".formatted(SUPPLYING_AGENCY_CODE));
		sierraPatronsAPIFixture.postPatronResponse("546730@%s".formatted(SUPPLYING_AGENCY_CODE), 1000003);

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest("1000003", "b", 563653);

		final var localItemId = "67384453";

		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold("1000003",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864905",
			"Consortial Hold. tno=" + patronRequest.getId(), localItemId);

		sierraItemsAPIFixture.mockGetItemById(localItemId,
			SierraItem.builder()
				.id(localItemId)
				.barcode("67324231")
				.statusCode("-")
				.build());

		// Act
		final var placedPatronRequest = placeAtSupplyingAgency(patronRequest);

		final var updatedSupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		 assertThat(updatedSupplierRequest, allOf(
		 	notNullValue(),
		 	hasLocalItemBarcode("67324231")
		 ));

		// Assert
		patronRequestWasPlaced(placedPatronRequest, patronRequestId);

		sierraPatronsAPIFixture.verifyPatronQueryRequestMade("546730@%s".formatted(
			SUPPLYING_AGENCY_CODE));
		sierraPatronsAPIFixture.verifyCreatePatronRequestMade(
			"546730@%s".formatted(SUPPLYING_AGENCY_CODE));

		sierraPatronsAPIFixture.verifyPlaceHoldRequestMade("1000003", "b",
			563653, BORROWING_AGENCY_CODE, "Consortial Hold. tno=%s \nFor 8675309012@%s\n Pickup MISSING-PICKUP-LIB@MISSING-PICKUP-LOCATION"
				.formatted(patronRequest.getId(), SUPPLYING_AGENCY_CODE));
	}

	@DisplayName("Do not attempt to place at supplying library when workflow is local")
	@Test
	void shouldNotAttemptToPlaceRequestAtSupplyingAgencyWhenWorkflowIsLocal() {
		// Arrange
		final var localId = "546730";
		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		final var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		patronRequest.setActiveWorkflow(LOCAL_WORKFLOW);
		saveSupplierRequest(patronRequest, hostLms.getCode());

		// Act
		final var exception = assertThrows(RuntimeException.class, () -> isApplicableFor(patronRequest));

		// Assert
		assertThat(exception.getMessage(), containsString("Place request at supplying agency is not applicable for request"));
	}

	@DisplayName("Should fail when supplying agency's Host LMS sends unexpected response whilst placing request")
	@Test
	void shouldFailWhenPlacingRequestAtSupplyingAgencyReturnsUnexpectedResponse() {
		// Arrange
		final var localId = "931824";
		final var localPatronId = 1000001;
		final var localItemId = "7916922";

		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		final var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.getCode());

		sierraPatronsAPIFixture.patronsQueryNotFoundResponse("931824@%s".formatted(SUPPLYING_AGENCY_CODE));
		sierraPatronsAPIFixture.postPatronResponse("931824@%s".formatted(SUPPLYING_AGENCY_CODE),
			localPatronId);

		sierraPatronsAPIFixture.patronHoldRequestErrorResponse(
			Integer.toString(localPatronId), "b");

		// Act
		final var problem = assertThrows(ThrowableProblem.class,
			() -> placeAtSupplyingAgency(patronRequest));

		// Assert

		final var expectedDetail = "Unexpected response from: POST /iii/sierra-api/v6/patrons/%d/holds/requests"
			.formatted(localPatronId);

		assertThat(problem, allOf(
			notNullValue(),
			hasTitle("Unable to place SUPPLIER hold request for pr=%s Lpatron=%d Litemid=%s Lit=null Lpt=15 system=supplying-agency-service-tests"
				.formatted(patronRequestId, localPatronId, localItemId)),
			// These are included from the underlying unexpected response problem created by the client
			hasDetail(expectedDetail),
			hasRequestMethod("POST"),
			hasRequestUrl("https://supplying-agency-service-tests.com/iii/sierra-api/v6/patrons/%d/holds/requests"
				.formatted(localPatronId)),
			hasResponseStatusCode(500),
			hasJsonResponseBodyProperty("code", 109),
			hasJsonResponseBodyProperty("description", "Invalid configuration"),
			hasJsonResponseBodyProperty("httpStatus", 500),
			hasJsonResponseBodyProperty("name", "Internal server error"),
			hasJsonResponseBodyProperty("specificCode", 0)
		));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		// Only matches on part of the message because it's too long for the database column
		final var partOfTruncatedErrorMessage = "Unable to place SUPPLIER hold request";

		assertThat(fetchedPatronRequest, allOf(
			hasStatus(ERROR),
			hasErrorMessage(partOfTruncatedErrorMessage)
		));

		final var audits = patronRequestsFixture.findAuditEntries(fetchedPatronRequest);

		assertThat("There should be one matching audit entry",
			audits, hasItem(allOf(
				briefDescriptionContains(partOfTruncatedErrorMessage),
				hasFromStatus(RESOLVED),
				hasToStatus(ERROR),
				hasAuditDataDetail(expectedDetail),
				hasAuditDataProperty("responseStatusCode", 500),
				hasNestedAuditDataProperty("responseBody", "code", 109),
				hasNestedAuditDataProperty("responseBody", "description", "Invalid configuration"),
				hasNestedAuditDataProperty("responseBody", "httpStatus", 500),
				hasNestedAuditDataProperty("responseBody", "name", "Internal server error"),
				hasNestedAuditDataProperty("responseBody", "specificCode", 0)
			))
		);
	}

	private void patronRequestWasPlaced(PatronRequest patronRequest, UUID expectedId) {
		log.debug("Patron request from transition: {}", patronRequest);

		assertThat(patronRequest, allOf(
			hasId(expectedId),
			hasStatus(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
		));
	}

	private UUID createClusterRecord() {
		return clusterRecordFixture.createClusterRecord(randomUUID(), null).getId();
	}

	private Patron createPatron(String localId, DataHostLms hostLms) {
		if (supplyingAgency == null) {
			throw new RuntimeException("Fixtures have not properly initialised data agency supplying-agency");
		}

		final Patron patron = patronFixture.savePatron("123456");
		patronFixture.saveIdentity(patron, hostLms, localId, true, "1", "123456",
			supplyingAgency);
		patronFixture.saveIdentity(patron, hostLms, localId, false, "1", null, null);
		patron.setPatronIdentities(patronService.findAllPatronIdentitiesByPatron(patron).collectList().block());
		return patron;	
	}

	private PatronRequest savePatronRequest(UUID patronRequestId, Patron patron,
		UUID clusterRecordId) {

		final var requestingIdentity = patron.getPatronIdentities().get(1);

		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.requestingIdentity(requestingIdentity)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode("ABC123")
			.status(RESOLVED)
			.activeWorkflow(STANDARD_WORKFLOW)
			.build();

		return patronRequestsFixture.savePatronRequest(patronRequest);
	}

	private void saveSupplierRequest(PatronRequest patronRequest, String hostLmsCode) {
		supplierRequestsFixture.saveSupplierRequest(
			randomUUID(), patronRequest, "563653", "7916922",
			SUPPLYING_AGENCY_CODE, "9849123490", hostLmsCode, "supplying-location");
	}

	private PatronRequest placeAtSupplyingAgency(PatronRequest patronRequest) {
		return requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(attemptTransition())
			.thenReturn(patronRequest)
			.block();
	}

	private PatronRequest isApplicableFor(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> {
				if (!placePatronRequestAtSupplyingAgencyStateTransition.isApplicableFor(ctx)) {
					return Mono.error(new RuntimeException("Place request at supplying agency is not applicable for request"));
				}

				return Mono.just(ctx.getPatronRequest())
					.flatMap(patronRequestWorkflowService.attemptTransitionWithErrorTransformer(
						placePatronRequestAtSupplyingAgencyStateTransition, ctx));
			})
			.thenReturn(patronRequest));
	}

	private Function<RequestWorkflowContext, Mono<RequestWorkflowContext>> attemptTransition() {
		return ctx -> Mono.just(ctx.getPatronRequest())
			.flatMap(patronRequestWorkflowService.attemptTransitionWithErrorTransformer(
				placePatronRequestAtSupplyingAgencyStateTransition, ctx));
	}

	private void savePatronTypeMappings() {
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE, 1, 1, "DCB", "SQUIGGLE");
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,10,15, "DCB", "SQUIGGLE");
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,20,25, "DCB", "SQUIGGLE");

		referenceValueMappingFixture.definePatronTypeMapping("DCB", "SQUIGGLE", HOST_LMS_CODE, "15");

		referenceValueMappingFixture.defineLocationToAgencyMapping("ABC123", BORROWING_AGENCY_CODE);
	}
}

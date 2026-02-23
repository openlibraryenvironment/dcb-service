package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.core.model.WorkflowConstants.LOCAL_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
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
import org.olf.dcb.test.TransitionFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PlaceRequestAtOwnLibraryStateTransitionTests {
	private static final String BORROWING_HOST_LMS_CODE = "next-supplier-borrowing-tests";
	private static final String SUPPLYING_HOST_LMS_CODE = "next-supplier-tests";

	private static final String SUPPLYING_AGENCY_CODE = "supplying-agency";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private LocationFixture locationFixture;
	@Inject
	private ConsortiumFixture consortiumFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private TransitionFixture transitionFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private PlaceRequestAtOwnLibraryStateTransition placeRequestAtOwnLibraryStateTransition;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private DataHostLms borrowingHostLms;
	private DataAgency borrowingAgency;

	private DataHostLms supplyingHostLms;

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

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient);
	}

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		locationFixture.deleteAll();
		consortiumFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();

		borrowingAgency = agencyFixture.defineAgency("borrowing-agency",
			"Borrowing Agency", borrowingHostLms);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE,
			"Supplying Agency", supplyingHostLms);
	}

	@Test
	void shouldBeApplicableToPlaceAtOwnLibraryWhenWorkflowIsLocal() {
		// Arrange
		final var patronRequest = definePatronRequest(LOCAL_WORKFLOW);

		// Act
		final var applicable = transitionFixture.isApplicable(
			placeRequestAtOwnLibraryStateTransition, patronRequest);

		// Assert
		assertThat("Should be applicable for local request",
			applicable, is(true));
	}

	@Test
	void shouldNotBeApplicableToPlaceAtOwnLibraryWhenWorkflowIsPickupAnywhere() {
		// Arrange
		final var patronRequest = definePatronRequest(PICKUP_ANYWHERE_WORKFLOW);

		// Act
		final var applicable = transitionFixture.isApplicable(
			placeRequestAtOwnLibraryStateTransition, patronRequest);

		// Assert
		assertThat("Should not be applicable for pickup anywhere request",
			applicable, is(false));
	}

	@Test
	void shouldBeApplicableToPlaceAtOwnLibraryWhenWorkflowIsStandard() {
		// Arrange
		final var patronRequest = definePatronRequest(STANDARD_WORKFLOW);

		// Act
		final var applicable = transitionFixture.isApplicable(
			placeRequestAtOwnLibraryStateTransition, patronRequest);

		// Assert
		assertThat("Should not be applicable for standard request",
			applicable, is(false));
	}

	/*
	We want to ensure errors are thrown when api requests fail so that they can be handled
	The workflow service will audit errors thrown and put the request in an error state
	 */
	@Test
	void UnsuccessfullyAttemptToPlaceAnOwnLibraryRequest() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, bibRecordId);

		final var hostLms = hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(bibRecordId, sourceSystemId, "798472", clusterRecord);

		final var patron = patronFixture.savePatron("972321");

		patronFixture.saveIdentity(patron, hostLms, "972321", true, "-", "972321", null);

		final var pickupLocation = locationFixture.createPickupLocation(borrowingAgency);

		final var pickupLocationId = getValueOrNull(pickupLocation, Location::getId, UUID::toString);

		final var patronRequestId = randomUUID();

		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode(pickupLocationId)
			.status(RESOLVED)
			.activeWorkflow(LOCAL_WORKFLOW)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest
			.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.localItemId("647245")
			.localBibId("647245")
			.localItemLocationCode(BORROWING_HOST_LMS_CODE)
			.localItemBarcode("9849123490")
			.hostLmsCode(hostLms.code)
			.isActive(true)
			.localItemLocationCode(BORROWING_HOST_LMS_CODE)
			.resolvedAgency(borrowingAgency)
			.build());

		sierraPatronsAPIFixture.patronHoldRequestErrorResponse("972321", "b");

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			supplyingHostLms.getCode(), 999, 999, "BKM");

		// Act
		final var problem = assertThrows(ThrowableProblem.class,
			() -> placeRequestAtOwnLibrary(patronRequest));

		// Assert
		final var expectedMessage = "Unexpected response from: POST /iii/sierra-api/v6/patrons/972321/holds/requests";

		assertThat(problem, allOf(
			hasMessage(expectedMessage),
			hasProperty("parameters", hasEntry("responseStatusCode", 404)),
			hasProperty("parameters", hasEntry("responseBody", "No body"))
		));
	}

	private void placeRequestAtOwnLibrary(PatronRequest patronRequest) {
		transitionFixture.attemptIfApplicable(placeRequestAtOwnLibraryStateTransition,
			patronRequest);
	}

	private PatronRequest definePatronRequest(String activeWorkflow) {
		final var patron = patronFixture.definePatron("365636", "home-library",
			borrowingHostLms, borrowingAgency);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(PatronRequest.Status.RESOLVED)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.activeWorkflow(activeWorkflow)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		return patronRequest;
	}
}

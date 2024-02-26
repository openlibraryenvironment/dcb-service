package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import org.olf.dcb.core.model.PatronRequest.Status;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;
import org.olf.dcb.request.resolution.UnableToResolvePatronRequest;
import org.olf.dcb.request.workflow.PatronRequestResolutionStateTransition;
import org.olf.dcb.test.*;

import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatronRequestResolutionTests {

	private final String HOST_LMS_CODE = "resolution-local-system";

	private static final Logger log = LoggerFactory.getLogger(PatronRequestResolutionTests.class);

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
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private DataHostLms hostLms;

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mockServer) {

		log.info("beforeAll\n\n");

		final String HOST_LMS_BASE_URL = "https://resolution-tests.com";
		final String HOST_LMS_TOKEN = "resolution-system-token";
		final String HOST_LMS_KEY = "resolution-system-key";
		final String HOST_LMS_SECRET = "resolution-system-secret";

		SierraTestUtils.mockFor(mockServer, HOST_LMS_BASE_URL)
			.setValidCredentials(HOST_LMS_KEY, HOST_LMS_SECRET, HOST_LMS_TOKEN, 60);

		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();

		hostLmsFixture.deleteAll();

		hostLms = hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, HOST_LMS_KEY,
			HOST_LMS_SECRET, HOST_LMS_BASE_URL, "item");
	}

	@BeforeEach
	void beforeEach() {
		log.info("beforeEach\n\n");
		clusterRecordFixture.deleteAll();
		// RequestWorkflowContextHelper will complain if the requested pickup location cannot be mapped into an agency
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();

		referenceValueMappingFixture.defineLocationToAgencyMapping(HOST_LMS_CODE,"ABC123","ab8");
		agencyFixture.defineAgency("ab8", "ab8", hostLms);
	}

	@Test
	void shouldResolveVerifiedRequestWhenFirstItemOfMultipleIsChosen(
		MockServerClient mockServerClient) {

		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, hostLms.getId(),
			"465675", clusterRecord);

		final var sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		sierraItemsAPIFixture.twoItemsResponseForBibId("465675");

		final var patron = patronFixture.savePatron("465636");
		patronFixture.saveIdentity(patron, hostLms, "872321", true, "-", "465636", null);

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(HOST_LMS_CODE)
			.pickupLocationCode("ABC123")
			.status(PATRON_VERIFIED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		singleValueFrom(
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.flatMap(ctx -> patronRequestResolutionStateTransition.attempt(ctx))
		);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should be resolved",
			fetchedPatronRequest.getStatus(), is(RESOLVED));

		final var onlySupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat("Should be for expected host LMS",
			onlySupplierRequest.getHostLmsCode(), is(HOST_LMS_CODE));

		assertThat("Should have expected local item ID",
			onlySupplierRequest.getLocalItemId(), is("1000002"));

		assertThat("Should have expected local bib ID",
			onlySupplierRequest.getLocalBibId(), is("465675"));

		assertThat("Should have expected local item barcode",
			onlySupplierRequest.getLocalItemBarcode(), is("6565750674"));

		assertThat("Should have expected local item location code",
			onlySupplierRequest.getLocalItemLocationCode(), is("ab6"));

		assertThat("Should not have local ID",
			onlySupplierRequest.getLocalId(), is(nullValue()));

		assertThat("Should not have local status",
			onlySupplierRequest.getLocalStatus(), is(nullValue()));

		assertSuccessfulTransitionAudit(fetchedPatronRequest, RESOLVED);
	}

	@Test
	void shouldResolveVerifiedRequestToNoAvailableItemsWhenNoItemCanBeChosen(
		MockServerClient mockServerClient) {

		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, hostLms.getId(),
			"245375", clusterRecord);

		final var sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		sierraItemsAPIFixture.zeroItemsResponseForBibId("245375");

		final var patron = patronFixture.savePatron("294385");
		patronFixture.saveIdentity(patron, hostLms, "872321", true,"-", "294385", null);

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(HOST_LMS_CODE)
			.pickupLocationCode("ABC123")
			.status(PATRON_VERIFIED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		singleValueFrom(
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.flatMap(ctx -> patronRequestResolutionStateTransition.attempt(ctx))
		);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have no items available at any agency",
			fetchedPatronRequest.getStatus(), is(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY));

		assertThat("Should not find any supplier requests",
			supplierRequestsFixture.findAllFor(patronRequest), hasSize(0));

		assertSuccessfulTransitionAudit(fetchedPatronRequest, NO_ITEMS_AVAILABLE_AT_ANY_AGENCY);
	}

	@Test
	void shouldFailToResolveVerifiedRequestWhenClusterRecordCannotBeFound() {

		log.info("shouldFailToResolveVerifiedRequestWhenClusterRecordCannotBeFound - entering\n\n");

		// Arrange
		final var patron = patronFixture.savePatron("757646");
		patronFixture.saveIdentity(patron, hostLms, "86848", true, "-", "757646", null);

		final var clusterRecordId = randomUUID();

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCodeContext(HOST_LMS_CODE)
			.pickupLocationCode("ABC123")
			.status(PATRON_VERIFIED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var exception = assertThrows(CannotFindClusterRecordException.class,
			() -> singleValueFrom(
				requestWorkflowContextHelper.fromPatronRequest(patronRequest)
						.flatMap(ctx -> patronRequestResolutionStateTransition.attempt(ctx))
				));

		// Assert
		assertThat("Exception should not be null", exception, is(notNullValue()));
		assertThat("Exception should have a message",
			exception.getMessage(), is("Cannot find cluster record for: " + clusterRecordId));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should be in error status",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is("Cannot find cluster record for: " + clusterRecordId));

		assertThat("Should not find any supplier requests",
			supplierRequestsFixture.findAllFor(patronRequest), hasSize(0));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest,
			"Cannot find cluster record for: " + clusterRecordId);

		log.info("shouldFailToResolveVerifiedRequestWhenClusterRecordCannotBeFound - exiting\n\n");
	}

	@Test
	void shouldFailToResolveVerifiedRequestWhenNoBibsForClusterRecord() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), null);

		final var patron = patronFixture.savePatron("757646");
		patronFixture.saveIdentity(patron, hostLms, "86848", true, "-", "757646", null);

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(HOST_LMS_CODE)
			.pickupLocationCode("ABC123")
			.status(PATRON_VERIFIED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var exception = assertThrows(UnableToResolvePatronRequest.class,
			() -> singleValueFrom(
				requestWorkflowContextHelper.fromPatronRequest(patronRequest)
						.flatMap(ctx -> patronRequestResolutionStateTransition.attempt(ctx))
				));

		// Assert
		assertThat("Exception should not be null", exception, is(notNullValue()));
		assertThat("Exception should have a message",
			exception.getMessage(), is("Cluster record: \"" + clusterRecord.getId() + "\" has no bibs"));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should be in error status",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Should not find any supplier requests",
			supplierRequestsFixture.findAllFor(patronRequest), hasSize(0));
	}

	public void assertSuccessfulTransitionAudit(PatronRequest patronRequest, Status expectedToStatus) {
		final var fetchedAudit = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat("Patron Request audit should NOT have brief description",
			fetchedAudit.getBriefDescription(),
			is(nullValue()));

		assertThat("Patron Request audit should have from state",
			fetchedAudit.getFromStatus(), is(PATRON_VERIFIED));

		assertThat("Patron Request audit should have to state",
			fetchedAudit.getToStatus(), is(expectedToStatus));
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

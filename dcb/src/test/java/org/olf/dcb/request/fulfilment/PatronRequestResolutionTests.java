package org.olf.dcb.request.fulfilment;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasErrorMessage;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalBibId;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemBarcode;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemId;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemLocationCode;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasNoLocalId;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasNoLocalItemStatus;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasNoLocalStatus;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;
import org.olf.dcb.request.resolution.UnableToResolvePatronRequest;
import org.olf.dcb.request.workflow.PatronRequestResolutionStateTransition;
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
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PatronRequestResolutionTests {
	private final String HOST_LMS_CODE = "resolution-local-system";

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
	private SierraItemsAPIFixture sierraItemsAPIFixture;

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

		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();

		hostLmsFixture.deleteAll();

		hostLms = hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, HOST_LMS_KEY,
			HOST_LMS_SECRET, HOST_LMS_BASE_URL, "item");

		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);
	}

	@BeforeEach
	void beforeEach() {
		log.info("beforeEach\n\n");

		clusterRecordFixture.deleteAll();
		// RequestWorkflowContextHelper will complain if the requested pickup location cannot be mapped into an agency
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();

		referenceValueMappingFixture.defineLocationToAgencyMapping(HOST_LMS_CODE,"ABC123","ab8");
		referenceValueMappingFixture.defineLocationToAgencyMapping(HOST_LMS_CODE,"ab6","ab8");

		agencyFixture.defineAgency("ab8", "ab8", hostLms);
	}

	@Test
	void shouldChooseFirstAvailableItem() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, hostLms.getId(),
			"465675", clusterRecord);

		sierraItemsAPIFixture.itemsForBibId("465675", List.of(
			SierraItem.builder()
				.id("1000001")
				.barcode("30800005238487")
				.locationCode("ab6")
				.statusCode("-")
				.dueDate(Instant.parse("2021-02-25T12:00:00Z"))
				.build(),
			SierraItem.builder()
				.id("1000002")
				.barcode("6565750674")
				.locationCode("ab6")
				.statusCode("-")
				.build()
		));

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
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat(fetchedPatronRequest, hasStatus(RESOLVED));

		final var onlySupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat(onlySupplierRequest, allOf(
			notNullValue(),
			hasProperty("hostLmsCode", is(HOST_LMS_CODE)),
			hasLocalItemId("1000002"),
			hasLocalItemBarcode("6565750674"),
			hasLocalBibId("465675"),
			hasLocalItemLocationCode("ab6"),
			hasNoLocalItemStatus(),
			hasNoLocalId(),
			hasNoLocalStatus()
		));

		assertSuccessfulTransitionAudit(fetchedPatronRequest, RESOLVED);
	}

	@Test
	void shouldChooseItemEvenWhenLocationCannotBeMappedToAgency() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, hostLms.getId(),
			"673634", clusterRecord);

		sierraItemsAPIFixture.itemsForBibId("673634", List.of(
			SierraItem.builder()
				.id("2656456")
				.barcode("6736553266")
				.locationCode("unknown-location")
				.statusCode("-")
				.build()
		));

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
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat(fetchedPatronRequest, hasStatus(RESOLVED));

		final var onlySupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat(onlySupplierRequest, allOf(
			notNullValue(),
			hasProperty("hostLmsCode", is(HOST_LMS_CODE)),
			hasLocalItemId("2656456"),
			hasLocalItemBarcode("6736553266"),
			hasLocalBibId("673634"),
			hasLocalItemLocationCode("unknown-location"),
			hasNoLocalItemStatus(),
			hasNoLocalId(),
			hasNoLocalStatus()
		));

		assertSuccessfulTransitionAudit(fetchedPatronRequest, RESOLVED);
	}

	@Test
	void shouldResolveVerifiedRequestToNoAvailableItemsWhenNoItemCanBeChosen() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, hostLms.getId(),
			"245375", clusterRecord);

		sierraItemsAPIFixture.itemsForBibId("245375", emptyList());

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
		resolve(patronRequest);

		// Assert
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat(fetchedPatronRequest, hasStatus(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY));

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
			hasErrorMessage(expectedErrorMessage)
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
			hasErrorMessage(expectedErrorMessage)
		));

		assertThat("Should not find any supplier requests",
			supplierRequestsFixture.findAllFor(patronRequest), hasSize(0));
	}

	private void resolve(PatronRequest patronRequest) {singleValueFrom(
		requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> patronRequestResolutionStateTransition.attempt(ctx)));
	}

	public void assertSuccessfulTransitionAudit(PatronRequest patronRequest, Status expectedToStatus) {
		assertThat("Patron Request should have state", patronRequest.getStatus(), is(expectedToStatus));
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

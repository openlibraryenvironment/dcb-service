package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasBriefDescription;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasFromStatus;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasNoBriefDescription;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasToStatus;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasLocalPatronType;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasResolvedAgency;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.workflow.ValidatePatronTransition;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValidatePatronTests {
	private static final String HOST_LMS_CODE = "validate-patron-transition-tests";

	@Inject
	SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private ValidatePatronTransition validatePatronTransition;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private PatronService patronService;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://validate-patron-transition-tests.com";
		final String KEY = "validate-patron-transition-key";
		final String SECRET = "validate-patron-transition-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.deleteAll();

		final var hostLms = hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, "item");

		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse("467295",
			SierraPatronsAPIFixture.Patron.builder()
				.id(1000002)
				.patronType(15)
				.homeLibraryCode("testccc")
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		// mock for no home library code
		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse("248303",
			SierraPatronsAPIFixture.Patron.builder()
				.id(1000002)
				.patronType(15)
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		referenceValueMappingFixture.deleteAll();

		agencyFixture.deleteAll();
		agencyFixture.saveAgency(DataAgency.builder()
			.id(UUID.randomUUID())
			.code("AGENCY1")
			.name("Test AGENCY1")
			.hostLms(hostLms)
			.build());
	}

	@Test
	void shouldUpdateCachedPatronTypeOnTypeChange() {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "467295";
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms, "123456");

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			"validate-patron-transition-tests", 10, 25, "DCB", "15");

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			"validate-patron-transition-tests", "testccc", "AGENCY1");

		var patronRequest = savePatronRequest(patronRequestId, patron);

		// Act
		final var validatedPatronRequest = validatePatronTransition.attempt(patronRequest).block();

		// Assert
		assertThat(validatedPatronRequest, hasLocalPatronType("15"));

		//assertSuccessfulTransitionAudit(patronRequest);
	}

	@Test
	void shouldUseDefaultAgencyFallbackWhenNoHomeLibrary() {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "248303";
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms, null);

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			"validate-patron-transition-tests", 10, 25, "DCB", "15");

		final var agency = agencyFixture.saveAgency(DataAgency.builder()
			.id(UUID.randomUUID())
			.code("default-agency-code")
			.name("Default Agency")
			.hostLms(hostLms)
			.build());

		var patronRequest = savePatronRequest(patronRequestId, patron);

		// Act
		final var validatedPatronRequest = validatePatronTransition.attempt(patronRequest).block();

		// Assert
		assertThat(validatedPatronRequest, is(notNullValue()));
		assertThat(validatedPatronRequest, hasResolvedAgency(agency));
		//assertSuccessfulTransitionAudit(patronRequest);
	}

	@Test
	void shouldFailWhenSierraRespondsWithNotFound(MockServerClient mockServerClient) {
		// Arrange
		final var LOCAL_ID = "672954";
		final var patronRequestId = randomUUID();

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(LOCAL_ID, hostLms, "123456");

		var patronRequest = savePatronRequest(patronRequestId, patron);

		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		sierraPatronsAPIFixture.noRecordsFoundWhenGettingPatronByLocalId("672954");

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> validatePatronTransition.attempt(patronRequest).block());

		// Assert
		final var expectedMessage = "Patron \"" + LOCAL_ID + "\" is not recognised in \"" + HOST_LMS_CODE + "\"";

		assertThat(exception.getMessage(), is(expectedMessage));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is(expectedMessage));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedMessage);
	}

	@Test
	void shouldFailWhenSierraRespondsWithServerError(MockServerClient mockServerClient) {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "236462";
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms, "123456");

		var patronRequest = savePatronRequest(patronRequestId, patron);

		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		sierraPatronsAPIFixture.badRequestWhenGettingPatronByLocalId("236462");

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> validatePatronTransition.attempt(patronRequest).block());

		// Assert
		final var expectedMessage = "Bad JSON/XML Syntax: Please check that the JSON fields/values are of the expected JSON data types - [130 / 0]";

		assertThat(exception.getMessage(), is(expectedMessage));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is(expectedMessage));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedMessage);
	}

	@Test
	void shouldFailWhenNoPatronTypeMappingIsDefined(MockServerClient mockServerClient) {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "783742";

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);

		final var patron = createPatron(localId, hostLms, "123456");

		var patronRequest = savePatronRequest(patronRequestId, patron);

		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localId,
			SierraPatronsAPIFixture.Patron.builder()
				.id(1000002)
				.patronType(15)
				.homeLibraryCode("testccc")
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> validatePatronTransition.attempt(patronRequest).block());

		// Assert
		final var expectedError = "Unable to map patronType validate-patron-transition-tests:15 To DCB context";

		assertThat(exception.getMessage(), is(expectedError));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is(expectedError));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedError);
	}

	private Patron createPatron(String localId, DataHostLms hostLms, String homeLibraryCode) {
		final Patron patron = patronFixture.savePatron(homeLibraryCode);

		patronFixture.saveIdentity(patron, hostLms, localId, true, "-", homeLibraryCode, null);

		patron.setPatronIdentities(patronService.findAllPatronIdentitiesByPatron(patron).collectList().block());

		return patron;
	}

	private PatronRequest savePatronRequest(UUID patronRequestId, Patron patron) {
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.status(SUBMITTED_TO_DCB)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		return patronRequest;
	}

	private void assertSuccessfulTransitionAudit(PatronRequest patronRequest) {
		final var fetchedAudit = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat(fetchedAudit, allOf(
			hasNoBriefDescription(),
			hasFromStatus(SUBMITTED_TO_DCB),
			hasToStatus(PATRON_VERIFIED)
		));
	}

	private void assertUnsuccessfulTransitionAudit(PatronRequest patronRequest, String description) {
		final var fetchedAudit = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat(fetchedAudit, allOf(
			hasBriefDescription(description),
			hasFromStatus(PATRON_VERIFIED),
			hasToStatus(ERROR)
		));
	}
}

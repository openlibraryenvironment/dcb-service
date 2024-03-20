package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasErrorMessage;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasLocalPatronType;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasResolvedAgency;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasJsonResponseBodyProperty;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasMessageForRequest;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasRequestMethodParameter;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasResponseStatusCodeParameter;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.workflow.ValidatePatronTransition;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
public class ValidatePatronTests {
	private static final String BORROWING_HOST_LMS_CODE = "validate-patron-transition-tests";
	private static final String AGENCY_CODE = "example-agency";
	private static final String HOME_LIBRARY_CODE = "home-library-code";

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
	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://validate-patron-transition-tests.com";
		final String KEY = "validate-patron-transition-key";
		final String SECRET = "validate-patron-transition-secret";

		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		final var hostLms = hostLmsFixture.createSierraHostLms(
			BORROWING_HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, "item");

		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse("467295",
			SierraPatronsAPIFixture.Patron.builder()
				.id(1000002)
				.patronType(15)
				.homeLibraryCode(HOME_LIBRARY_CODE)
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

		agencyFixture.defineAgency(AGENCY_CODE, "Example Agency", hostLms);
	}

	@Test
	void shouldUpdateCachedPatronTypeOnTypeChange() {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "467295";
		final var hostLms = hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms, "123456");

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			"validate-patron-transition-tests", 10, 25, "DCB", "15");

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			"validate-patron-transition-tests", HOME_LIBRARY_CODE, AGENCY_CODE);

		var patronRequest = savePatronRequest(patronRequestId, patron);

		// Act
		final var validatedPatronRequest =
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.flatMap(ctx -> validatePatronTransition.attempt(ctx) )
				.thenReturn(patronRequest)
				.block();

		// Assert
		assertThat(validatedPatronRequest, hasLocalPatronType("15"));

		assertSuccessfulTransitionAudit(patronRequest);
	}

	@Test
	void shouldUseDefaultAgencyFallbackWhenNoHomeLibrary() {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "248303";

		final var borrowingHostLms = hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE);

		final var patron = createPatron(localId, borrowingHostLms, null);

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			"validate-patron-transition-tests", 10, 25, "DCB", "15");

		final var agency = agencyFixture.defineAgency("default-agency-code",
			"Default Agency", borrowingHostLms);

		var patronRequest = savePatronRequest(patronRequestId, patron);

		// Act
		final var validatedPatronRequest = requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> validatePatronTransition.attempt(ctx))
			.thenReturn(patronRequest)
			.block();

		// Assert
		assertThat(validatedPatronRequest, is(notNullValue()));
		assertThat(validatedPatronRequest, hasResolvedAgency(agency));
		assertSuccessfulTransitionAudit(patronRequest);
	}

	@Test
	void shouldFailWhenSierraRespondsWithNotFound(MockServerClient mockServerClient) {
		// Arrange
		final var LOCAL_ID = "672954";
		final var patronRequestId = randomUUID();

		final var hostLms = hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE);
		final var patron = createPatron(LOCAL_ID, hostLms, "123456");

		var patronRequest = savePatronRequest(patronRequestId, patron);

		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		sierraPatronsAPIFixture.noRecordsFoundWhenGettingPatronByLocalId("672954");

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.flatMap( ctx -> validatePatronTransition.attempt(ctx))
				.block());

		// Assert
		final var expectedMessage = "Patron \"" + LOCAL_ID + "\" is not recognised in \"" + BORROWING_HOST_LMS_CODE + "\"";

		assertThat(exception.getMessage(), is(expectedMessage));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is(expectedMessage));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest);
	}

	@Test
	void shouldFailWhenSierraRespondsWithServerError(MockServerClient mockServerClient) {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "236462";
		final var hostLms = hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms, "123456");

		var patronRequest = savePatronRequest(patronRequestId, patron);

		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		sierraPatronsAPIFixture.badRequestWhenGettingPatronByLocalId("236462");

		// Act
		final var problem = assertThrows(ThrowableProblem.class,
			() -> requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.flatMap( ctx -> validatePatronTransition.attempt(ctx))
				.block());

		// Assert
		assertThat(problem, allOf(
			hasMessageForRequest("GET", "/iii/sierra-api/v6/patrons/236462"),
			hasResponseStatusCodeParameter(400),
			hasJsonResponseBodyProperty("name","Bad JSON/XML Syntax"),
			hasJsonResponseBodyProperty("description",
				"Please check that the JSON fields/values are of the expected JSON data types"),
			hasJsonResponseBodyProperty("code", 130),
			hasJsonResponseBodyProperty("specificCode", 0),
			hasRequestMethodParameter("GET")
		));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		final var expectedMessage = "Unexpected response from: GET /iii/sierra-api/v6/patrons/236462";

		assertThat(fetchedPatronRequest, allOf(
			hasStatus(ERROR),
			hasErrorMessage(expectedMessage)
		));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest);
	}

	@Test
	void shouldFailWhenNoPatronTypeMappingIsDefined(MockServerClient mockServerClient) {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "783742";

		final var hostLms = hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE);

		final var patron = createPatron(localId, hostLms, "123456");

		var patronRequest = savePatronRequest(patronRequestId, patron);

		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localId,
			SierraPatronsAPIFixture.Patron.builder()
				.id(1000002)
				.patronType(15)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.flatMap(ctx -> validatePatronTransition.attempt(ctx))
				.block());

		// Assert
		final var expectedError = "Unable to map patronType validate-patron-transition-tests:15 To DCB context";

		assertThat(exception.getMessage(), is(expectedError));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is(expectedError));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest);
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
		assertThat(patronRequest.getStatus(), is(PATRON_VERIFIED) );
	}

	private void assertUnsuccessfulTransitionAudit(PatronRequest patronRequest) {
		assertThat(patronRequest.getStatus(), is(ERROR) );
	}
}

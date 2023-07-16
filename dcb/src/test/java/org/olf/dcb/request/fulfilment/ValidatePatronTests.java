package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.workflow.ValidatePatronTransition;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValidatePatronTests {
	private static final String HOST_LMS_CODE = "validate-patron-transition-tests";

	@Inject
	ResourceLoader loader;
	@Inject
	private ValidatePatronTransition validatePatronTransition;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://validate-patron-transition-tests.com";
		final String KEY = "validate-patron-transition-key";
		final String SECRET = "validate-patron-transition-secret";

		SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.deleteAllHostLMS();
		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);

		final var sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock, loader);

		sierraPatronsAPIFixture.getPatronByLocalId("467295");
	}

	@Test
	void shouldUpdateCachedPatronTypeOnTypeChange() {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "467295";
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);

		var patronRequest = savePatronRequest(patronRequestId, patron);

		// Act
		final var validatedPatron = validatePatronTransition.attempt(patronRequest).block();

		// Assert
		final var patronType = validatedPatron.getRequestingIdentity().getLocalPtype();

		assertThat(patronType, is("15"));
		assertSuccessfulTransitionAudit(patronRequest);
	}

	@Test
	void shouldFailWhenSierraRespondsWithNotFound(MockServerClient mockServerClient) {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "672954";
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);

		var patronRequest = savePatronRequest(patronRequestId, patron);

		final var sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mockServerClient, loader);

		sierraPatronsAPIFixture.noRecordsFoundWhenGettingPatronByLocalId("672954");

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> validatePatronTransition.attempt(patronRequest).block());

		// Assert
		assertThat(exception.getMessage(), is("No patron found"));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(Status.ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is("No patron found"));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, "No patron found");
	}

	public void assertSuccessfulTransitionAudit(PatronRequest patronRequest) {

		final var fetchedAudit = patronRequestsFixture.findAuditByPatronRequest(patronRequest).blockFirst();

		assertThat("Patron Request audit should NOT have brief description",
			fetchedAudit.getBriefDescription(),
			is(nullValue()));

		assertThat("Patron Request audit should have from state",
			fetchedAudit.getFromStatus(), is(Status.SUBMITTED_TO_DCB));

		assertThat("Patron Request audit should have to state",
			fetchedAudit.getToStatus(), is(Status.PATRON_VERIFIED));
	}

	public void assertUnsuccessfulTransitionAudit(PatronRequest patronRequest, String description) {

		final var fetchedAudit = patronRequestsFixture.findAuditByPatronRequest(patronRequest).blockFirst();

		assertThat("Patron Request audit should have brief description",
			fetchedAudit.getBriefDescription(),
			is(description));

		assertThat("Patron Request audit should have from state",
			fetchedAudit.getFromStatus(), is(Status.PATRON_VERIFIED));

		assertThat("Patron Request audit should have to state",
			fetchedAudit.getToStatus(), is(Status.ERROR));
	}

	@Test
	void shouldFailWhenSierraRespondsWithServerError(MockServerClient mockServerClient) {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "236462";
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);

		var patronRequest = savePatronRequest(patronRequestId, patron);

		final var sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mockServerClient, loader);

		sierraPatronsAPIFixture.serverErrorWhenGettingPatronByLocalId("236462");

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> validatePatronTransition.attempt(patronRequest).block());

		// Assert
		assertThat(exception.getMessage(), is("Internal Server Error"));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(Status.ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is("Internal Server Error"));
	}

	private Patron createPatron(String localId, DataHostLms hostLms) {
		final Patron patron = patronFixture.savePatron("123456");

		patronFixture.saveIdentity(patron, hostLms, localId, true, "-");

		patron.setPatronIdentities(patronFixture.findIdentities(patron));

		return patron;
	}

	private PatronRequest savePatronRequest(UUID patronRequestId, Patron patron) {
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.status(Status.SUBMITTED_TO_DCB)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		return patronRequest;
	}
}

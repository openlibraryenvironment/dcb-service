package org.olf.dcb.request.fulfilment;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.ValidatePatronTransition;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValidatePatronTransitionTests {

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
		sierraPatronsAPIFixture.getPatronByLocalIdError("672954");
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
	}

	@Test
	void shouldThrowRuntimeErrorWhenSierraRespondsWithNoPatron() {
		// Arrange
		final var patronRequestId = randomUUID();
		final var localId = "672954";
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);

		var patronRequest = savePatronRequest(patronRequestId, patron);

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> validatePatronTransition.attempt(patronRequest).block());

		// Assert
		assertThat(exception.getMessage(), is("No patron found"));
		assertThat(exception.getLocalizedMessage(), is("No patron found"));
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
			.statusCode(SUBMITTED_TO_DCB)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		return patronRequest;
	}
}

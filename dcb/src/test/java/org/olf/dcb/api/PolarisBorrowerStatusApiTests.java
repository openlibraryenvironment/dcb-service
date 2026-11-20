package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.clients.ChecksFailure.Check.hasCode;
import static org.olf.dcb.test.clients.ChecksFailure.Check.hasUserMessage;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.polaris.MockPolarisFixture;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronCirculationBlocksResult;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;
import org.olf.dcb.test.clients.ChecksFailure;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * A patron of a Polaris library asks for a title, and Polaris cannot say whether they may borrow.
 * Asked for a barcode it no longer has, as after a barcode change, PAPI answers the blocks call
 * with -1; DCB used to read that as "not blocked" and let the request through.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PolarisBorrowerStatusApiTests {
	private static final String HOST = "polaris-borrower-status";
	private static final String BASE_URL = "https://" + HOST;
	private static final String HOST_LMS_CODE = "polaris-borrower-status";
	private static final String STATUS_UNCHECKED = "PATRON_STATUS_UNCHECKED";
	private static final String PATRON_MESSAGE = "We couldn't check your library account just now, so your request "
		+ "has not been placed. Please try again in a few minutes. If this keeps happening, contact your library: "
		+ "they can check your account.";

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private PatronRequestApiClient patronRequestApiClient;

	private MockPolarisFixture polaris;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		final var key = "polaris-borrower-status-key";
		final var secret = "polaris-borrower-status-secret";

		hostLmsFixture.createPolarisHostLms(HOST_LMS_CODE, key, secret, BASE_URL, "TEST", key, secret);

		polaris = new MockPolarisFixture(HOST, mockServerClient, testResourceLoaderProvider);

		// As a library's own patron codes are mapped when it joins; Polaris is asked for blocks after this
		referenceValueMappingFixture.deleteAll();
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE, 3, 3, "DCB", "UNDERGRADUATE");
	}

	@AfterAll
	void afterAll() {
		patronRequestApiClient.removeTokenFromValidTokens();
	}

	@BeforeEach
	void beforeEach() {
		polaris.mockPapiStaffAuthentication();
		polaris.mockAppServicesStaffAuthentication();
	}

	@Test
	void aPatronPolarisAnswersMinusOneForIsToldToTryAgainThenAskTheirLibrary() {
		final var barcode = borrowerKnownToPolaris("4100001", PatronCirculationBlocksResult.builder()
			.papiErrorCode(-1)
			.errorMessage("General Failure")
			.build());

		final var refusal = placeRefused("4100001");

		assertThat(refusal.getFailedChecks(), hasItem(allOf(
			hasCode(STATUS_UNCHECKED),
			hasUserMessage(PATRON_MESSAGE),
			hasProperty("description", allOf(containsString(HOST_LMS_CODE), not(containsString(barcode)))))));
	}

	@Test
	void aPatronWhoseBlocksAnswerDoesNotSayWhetherTheyMayBorrowIsRefusedTheSameWay() {
		borrowerKnownToPolaris("4100002", PatronCirculationBlocksResult.builder().build());

		assertThat(placeRefused("4100002").getFailedChecks(),
			hasItem(allOf(hasCode(STATUS_UNCHECKED), hasUserMessage(PATRON_MESSAGE))));
	}

	@Test
	void aPatronPolarisLetsBorrowIsNotRefusedForTheirStatus() {
		borrowerKnownToPolaris("4100003", PatronCirculationBlocksResult.builder()
			.canPatronCirculate(true)
			.build());

		// Other checks may still refuse this request, for example its unknown pickup location
		final List<ChecksFailure.Check> failed = placeOrRefused("4100003");

		assertThat(failed, everyItem(not(hasCode(STATUS_UNCHECKED))));
	}

	private String borrowerKnownToPolaris(String localId, PatronCirculationBlocksResult blocks) {
		final var barcode = "2" + localId;

		polaris.mockGetPatron(localId, 3, barcode, 39);

		polaris.mockGetPatronCirculationBlocks(barcode, blocks);

		return barcode;
	}

	private ChecksFailure placeRefused(String localId) {
		final var error = assertThrows(HttpClientResponseException.class, () -> place(localId));

		assertThat(error.getStatus(), is(BAD_REQUEST));

		return error.getResponse().getBody(ChecksFailure.class).orElseThrow();
	}

	private List<ChecksFailure.Check> placeOrRefused(String localId) {
		try {
			place(localId);
			return List.of();
		}
		catch (HttpClientResponseException error) {
			return error.getResponse().getBody(ChecksFailure.class)
				.map(ChecksFailure::getFailedChecks)
				.filter(Objects::nonNull)
				.orElse(List.of());
		}
	}

	private void place(String localId) {
		patronRequestApiClient.placePatronRequest(UUID.randomUUID(), localId, "unknown-pickup-location",
			HOST_LMS_CODE, "home-library");
	}
}

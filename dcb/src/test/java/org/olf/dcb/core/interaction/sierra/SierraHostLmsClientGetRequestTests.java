package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_PLACED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasRequestedItemId;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasNoRequestedItemId;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraCodeTuple;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraHostLmsClientGetRequestTests {
	private static final String HOST_LMS_CODE = "sierra-place-hold-tests";
	private final String BASE_URL = "https://get-request-tests.com";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String KEY = "supplying-agency-service-key";
		final String SECRET = "supplying-agency-service-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "title");

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
	}

	@Test
	void titleLevelRequestIsConsideredPlaced() {
		// Arrange
		final var localRequestId = "62183415";

		sierraPatronsAPIFixture.mockGetHoldById(localRequestId, SierraPatronHold.builder()
			.id("%s/iii/sierra-api/v6/patrons/holds/%s".formatted(BASE_URL, localRequestId))
			.patron("%s/iii/sierra-api/v6/patrons/%s".formatted(BASE_URL, "5729178"))
			.recordType("b")
			.record("%s/iii/sierra-api/v6/items/%s".formatted(BASE_URL, "2735538"))
			.status(SierraCodeTuple.builder()
				.code("0")
				.build())
			.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var foundRequest = singleValueFrom(client.getRequest(localRequestId));

		// Assert
		assertThat(foundRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus(HOLD_PLACED),
			hasNoRequestedItemId()
		));
	}

	@Test
	void itemLevelRequestIsConsideredPlaced() {
		// Arrange
		final var localRequestId = "4653851";
		final var localItemId = "7258531";

		sierraPatronsAPIFixture.mockGetHoldById(localRequestId, SierraPatronHold.builder()
			.id("%s/iii/sierra-api/v6/patrons/holds/%s".formatted(BASE_URL, localRequestId))
			.patron("%s/iii/sierra-api/v6/patrons/%s".formatted(BASE_URL, "5729178"))
			.recordType("i")
			.record("%s/iii/sierra-api/v6/items/%s".formatted(BASE_URL, localItemId))
			.status(SierraCodeTuple.builder()
				.code("0")
				.build())
			.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var foundRequest = singleValueFrom(client.getRequest(localRequestId));

		// Assert
		assertThat(foundRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus(HOLD_PLACED),
			hasRequestedItemId(localItemId)
		));
	}
}

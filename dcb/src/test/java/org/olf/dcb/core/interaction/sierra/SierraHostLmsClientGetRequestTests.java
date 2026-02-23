package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_PLACED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasNoLocalId;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasNoRequestedItemBarcode;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasNoRequestedItemId;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasNoStatus;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasRequestedItemBarcode;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasRequestedItemId;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsRequest;
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
	private static final String HOST = "get-request-tests.com";
	private static final String BASE_URL = "https://" + HOST;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;

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

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient, HOST);
		sierraItemsAPIFixture = sierraApiFixtureProvider.items(mockServerClient, HOST);
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

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		final var foundRequest = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(foundRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus(HOLD_PLACED),
			hasNoRequestedItemId(),
			hasNoRequestedItemBarcode()
		));
	}

	@Test
	void itemLevelRequestIsConsideredConfirmed() {
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

		sierraItemsAPIFixture.mockGetItemById(localItemId,
			SierraItem.builder()
				.id(localItemId)
				.barcode("26368890")
				.statusCode("-")
				.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		final var foundRequest = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(foundRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus(HOLD_CONFIRMED),
			hasRequestedItemId(localItemId),
			hasRequestedItemBarcode("26368890")
		));
	}

	@Test
	void shouldTolerateEmptyHoldResponse() {
		// Arrange
		final var localRequestId = "374762143";

		sierraPatronsAPIFixture.mockGetHoldById(localRequestId, SierraPatronHold.builder().build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		final var foundRequest = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(foundRequest, allOf(
			notNullValue(),
			hasNoLocalId(),
			hasNoStatus(),
			hasNoRequestedItemId(),
			hasNoRequestedItemBarcode()
		));
	}

	@Test
	void shouldTolerateEmptyItemResponse() {
		// Arrange
		final var localRequestId = "374762143";
		final var localItemId = "4653553";

		sierraPatronsAPIFixture.mockGetHoldById(localRequestId, SierraPatronHold.builder()
			.id("%s/iii/sierra-api/v6/patrons/holds/%s".formatted(BASE_URL, localRequestId))
			.patron("%s/iii/sierra-api/v6/patrons/%s".formatted(BASE_URL, "5729178"))
			.recordType("i")
			.record("%s/iii/sierra-api/v6/items/%s".formatted(BASE_URL, localItemId))
			.status(SierraCodeTuple.builder()
				.code("0")
				.build())
			.build());

		sierraItemsAPIFixture.mockGetItemById(localItemId, SierraItem.builder().build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		final var foundRequest = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(foundRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus(HOLD_CONFIRMED),
			hasRequestedItemId(localItemId),
			hasNoRequestedItemBarcode()
		));
	}

	@Test
	void shouldTolerateItemNotFoundResponse() {
		// Arrange
		final var localRequestId = "27746533";
		final var localItemId = "8472421";

		sierraPatronsAPIFixture.mockGetHoldById(localRequestId, SierraPatronHold.builder()
			.id("%s/iii/sierra-api/v6/patrons/holds/%s".formatted(BASE_URL, localRequestId))
			.patron("%s/iii/sierra-api/v6/patrons/%s".formatted(BASE_URL, "5729178"))
			.recordType("i")
			.record("%s/iii/sierra-api/v6/items/%s".formatted(BASE_URL, localItemId))
			.status(SierraCodeTuple.builder()
				.code("0")
				.build())
			.build());

		sierraItemsAPIFixture.mockGetItemByIdReturnNoRecordsFound(localItemId);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var hostLmsRequest = HostLmsRequest.builder().localId(localRequestId).build();

		final var foundRequest = singleValueFrom(client.getRequest(hostLmsRequest));

		// Assert
		assertThat(foundRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus(HOLD_CONFIRMED),
			hasRequestedItemId(localItemId),
			hasNoRequestedItemBarcode()
		));
	}
}

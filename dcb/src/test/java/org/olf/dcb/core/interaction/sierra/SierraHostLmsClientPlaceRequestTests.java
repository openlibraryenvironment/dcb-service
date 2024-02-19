package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasRequestedItemBarcode;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasRequestedItemId;
import static org.olf.dcb.test.matchers.ThrowableMatchers.messageContains;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraHostLmsClientPlaceRequestTests {
	private static final String HOST_LMS_CODE = "sierra-place-hold-tests";

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
		final String BASE_URL = "https://supplying-agency-service-tests.com";
		final String KEY = "supplying-agency-service-key";
		final String SECRET = "supplying-agency-service-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "title");

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);
	}

	@Test
	void canPlaceATitleLevelHoldRequestAtSupplyingAgency() {
		// Arrange
		final var patronRequestId = UUID.randomUUID().toString();

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest("1000002", "b", 4093753);

		sierraPatronsAPIFixture.patronHoldResponse("1000002",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864904",
			"Consortial Hold. tno=" + patronRequestId, "6747235");

		sierraItemsAPIFixture.mockGetItemById("6747235",
			SierraItem.builder()
				.id("6747235")
				.barcode("38275735")
				.statusCode("-")
				.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var placedRequest = singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localBibId("4093753")
					.localPatronId("1000002")
					.patronRequestId(patronRequestId)
					.build()));

		// Assert
		assertThat(placedRequest, allOf(
			is(notNullValue()),
			hasLocalId("864904"),
			hasRequestedItemId("6747235"),
			hasRequestedItemBarcode("38275735"),
			hasLocalStatus("PLACED")
		));
	}

	@Test
	void cannotPlaceRequestWhenHoldPolicyIsInvalid() {
		// Arrange
		hostLmsFixture.createSierraHostLms("invalid-hold-policy",
			"key", "secret", "https://sierra-place-request-tests.com", "invalid");

		// Act
		final var client = hostLmsFixture.createClient("invalid-hold-policy");

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder().build())));

		// Assert
		assertThat(exception, messageContains("Invalid hold policy for Host LMS"));
	}
}

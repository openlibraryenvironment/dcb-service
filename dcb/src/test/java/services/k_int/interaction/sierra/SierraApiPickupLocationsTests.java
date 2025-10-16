package services.k_int.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPickupLocationsAPIFixture;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.http.client.HttpClient;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.configuration.PickupLocationInfo;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraApiPickupLocationsTests {
	private static final String HOST_LMS_CODE = "sierra-locations-api-tests";

	@Inject
	private HttpClient client;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	private SierraPickupLocationsAPIFixture sierraPickupLocationsFixture;
	
	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://pickup-locations-api-tests.com";
		final String KEY = "pickup-locations-key";
		final String SECRET = "pickup-locations-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraPickupLocationsFixture = sierraApiFixtureProvider.pickupLocationsFor(mockServerClient);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
	}

	@Test
	void shouldBeAbleToFetchPickupLocations() {
		// Arrange
		sierraPickupLocationsFixture.successfulResponseWhenGettingPickupLocations();

		final var sierraApiClient = hostLmsFixture.createLowLevelSierraClient(HOST_LMS_CODE);

		// Act
		var pickupLocations = singleValueFrom(sierraApiClient.pickupLocations());

		// Assert
		assertThat("Pickup locations should not be null", pickupLocations, is(notNullValue()));
		assertThat("Should have 3 pickup locations", pickupLocations, hasSize(3));

		// Codes from the sandbox have trailing spaces
		assertThat("Should have expected locations", pickupLocations, hasItems(
			hasPickupLocation("Almaden Branch", "10   "),
			hasPickupLocation("Alviso Branch", "18   "),
			hasPickupLocation("Bascom Branch", "24   ")));
	}

	private static Matcher<PickupLocationInfo> hasPickupLocation(String name, String code) {
		return allOf(
			hasProperty("name", is(name)),
			hasProperty("code", is(code))
		);
	}
}

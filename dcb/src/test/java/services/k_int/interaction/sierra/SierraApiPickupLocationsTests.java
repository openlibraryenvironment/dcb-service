package services.k_int.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import io.micronaut.context.annotation.Property;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraPickupLocationsAPIFixture;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.configuration.PickupLocationInfo;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SierraApiPickupLocationsTests {
	private static final String HOST_LMS_CODE = "sierra-locations-api-tests";

	@Inject
	private HttpClient client;
	@Inject
	private ResourceLoader loader;
	@Inject
	private HostLmsFixture hostLmsFixture;

	private SierraPickupLocationsAPIFixture sierraPickupLocationsFixture;

	@BeforeAll
	void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://pickup-locations-api-tests.com";
		final String KEY = "pickup-locations-key";
		final String SECRET = "pickup-locations-secret";

		SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraPickupLocationsFixture = new SierraPickupLocationsAPIFixture(mock, loader);

		hostLmsFixture.deleteAllHostLMS();

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);
	}

	@Test
	void shouldBeAbleToFetchPickupLocations() {
		// Arrange
		sierraPickupLocationsFixture.successfulResponseWhenGettingPickupLocations();

		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

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
			hasProperty("code", is(code)));
	}
}

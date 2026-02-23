package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasHttpVersion;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasJsonResponseBodyProperty;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasMessageForRequest;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestMethod;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestUrl;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsRenewal;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraHostLmsClientRenewalTests {
	private static final String CIRCULATING_HOST_LMS_CODE = "sierra-item-circulating";

	@Inject private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject private HostLmsFixture hostLmsFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;
	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://renewal-api-tests.com";
		final String KEY = "renewal-key";
		final String SECRET = "renewal-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraItemsAPIFixture = sierraApiFixtureProvider.items(mockServerClient, null);
		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient, null);

		final var sierraLoginFixture = sierraApiFixtureProvider.login(mockServerClient, null);

		sierraLoginFixture.failLoginsForAnyOtherCredentials(KEY, SECRET);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(CIRCULATING_HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
	}

	@Test
	void shouldFetchItemCheckouts() {
		// Arrange
		final var localItemId = "10942942";
		final var localPatronId = "1182843";
		final var localItemBarcode = "98030205213515";
		final var localPatronBarcode = "9821734";

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(localItemId).localPatronId(localPatronId)
			.localItemBarcode(localItemBarcode).localPatronBarcode(localPatronBarcode).build();

		final var checkoutID = sierraItemsAPIFixture.checkoutsForItem(localItemId);

		sierraPatronsAPIFixture.mockRenewalSuccess( checkoutID );

		// Act
		final var client = hostLmsFixture.createClient(CIRCULATING_HOST_LMS_CODE);

		final var response = singleValueFrom(client.renew(hostLmsRenewal));

		// Assert
		assertThat(response, is(CoreMatchers.notNullValue()));
		assertThat(response, allOf(
			hasProperty("localItemId", is("10942942")),
			hasProperty("localPatronId", is("1182843")),
			hasProperty("localItemBarcode", is("98030205213515")),
			hasProperty("localPatronBarcode", is("9821734"))
		));
	}

	@Test
	void shouldReturnProblemWhenNoCheckoutRecordsAreFound() {
		// Arrange
		final var localItemId = "10942942";
		final var localPatronId = "1182843";
		final var localItemBarcode = "98030205213515";
		final var localPatronBarcode = "9821734";

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(localItemId).localPatronId(localPatronId)
			.localItemBarcode(localItemBarcode).localPatronBarcode(localPatronBarcode).build();

		sierraItemsAPIFixture.checkoutsForItemWithNoPatronEntries(localItemId);

		// Act
		final var client = hostLmsFixture.createClient(CIRCULATING_HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.renew(hostLmsRenewal)));

		// Assert
		assertThat(problem, allOf(
			hasProperty("title", is("Checkout ID not found for renewal")),
			hasProperty("detail", is("No checkout records returned"))
		));
	}

	@Test
	void shouldReturnProblemWhenNoCheckoutsMatchPatronId() {
		// Arrange
		final var localItemId = "10942942";
		// ensuring the local patron id does not match the mock response
		final var localPatronId = "34273984";
		final var localItemBarcode = "98030205213515";
		final var localPatronBarcode = "9821734";

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(localItemId).localPatronId(localPatronId)
			.localItemBarcode(localItemBarcode).localPatronBarcode(localPatronBarcode).build();

		sierraItemsAPIFixture.checkoutsForItem(localItemId);

		// Act
		final var client = hostLmsFixture.createClient(CIRCULATING_HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.renew(hostLmsRenewal)));

		// Assert
		assertThat(problem, allOf(
			hasProperty("title", is("Checkout ID not found for renewal")),
			hasProperty("detail", is("No checkouts matching local patron id found"))
		));
	}

	@Test
	void shouldReturnProblemWhenMultipleCheckoutsMatchPatronId() {
		// Arrange
		final var localItemId = "10942942";
		final var localPatronId = "1182843";
		final var localItemBarcode = "98030205213515";
		final var localPatronBarcode = "9821734";

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(localItemId).localPatronId(localPatronId)
			.localItemBarcode(localItemBarcode).localPatronBarcode(localPatronBarcode).build();

		sierraItemsAPIFixture.checkoutsForItemWithMultiplePatronEntries(localItemId);

		// Act
		final var client = hostLmsFixture.createClient(CIRCULATING_HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.renew(hostLmsRenewal)));

		// Assert
		assertThat(problem, allOf(
			hasProperty("title", is("Checkout ID not found for renewal")),
			hasProperty("detail", is("Multiple checkouts matching local patron id found"))
		));
	}

	@Test
	void shouldReturnProblemWhenGetCheckoutsFails() {
		// Arrange
		final var localItemId = "10942942";
		final var localPatronId = "1182843";
		final var localItemBarcode = "98030205213515";
		final var localPatronBarcode = "9821734";

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(localItemId).localPatronId(localPatronId)
			.localItemBarcode(localItemBarcode).localPatronBarcode(localPatronBarcode).build();

		sierraItemsAPIFixture.checkoutsForItemWithNoRecordsFound(localItemId);

		// Act
		final var client = hostLmsFixture.createClient(CIRCULATING_HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.renew(hostLmsRenewal)));

		// Assert
		assertThat(problem, allOf(
			hasMessageForRequest("GET", "/iii/sierra-api/v6/items/10942942/checkouts"),
			hasResponseStatusCode(404),
			hasJsonResponseBodyProperty("code", 107),
			hasJsonResponseBodyProperty("httpStatus", 404),
			hasJsonResponseBodyProperty("name", "Record not found"),
			hasJsonResponseBodyProperty("specificCode", 0),
			hasRequestMethod("GET"),
			hasRequestUrl("https://renewal-api-tests.com/iii/sierra-api/v6/items/10942942/checkouts"),
			hasHttpVersion("HTTP_1_1")
		));
	}

	@Test
	void shouldReturnProblemWhenPostRenewalFails() {
		// Arrange
		final var localItemId = "10942942";
		final var localPatronId = "1182843";
		final var localItemBarcode = "98030205213515";
		final var localPatronBarcode = "9821734";

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(localItemId).localPatronId(localPatronId)
			.localItemBarcode(localItemBarcode).localPatronBarcode(localPatronBarcode).build();

		final var checkoutID = sierraItemsAPIFixture.checkoutsForItem(localItemId);

		sierraPatronsAPIFixture.mockRenewalNoRecordsFound( checkoutID );

		// Act
		final var client = hostLmsFixture.createClient(CIRCULATING_HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.renew(hostLmsRenewal)));

		// Assert
		assertThat(problem, allOf(
			hasMessageForRequest("POST", "/iii/sierra-api/v6/patrons/checkouts/1811242/renewal"),
			hasResponseStatusCode(404),
			hasJsonResponseBodyProperty("code", 107),
			hasJsonResponseBodyProperty("httpStatus", 404),
			hasJsonResponseBodyProperty("name", "Record not found"),
			hasJsonResponseBodyProperty("specificCode", 0),
			hasRequestMethod("POST"),
			hasRequestUrl("https://renewal-api-tests.com/iii/sierra-api/v6/patrons/checkouts/1811242/renewal"),
			hasHttpVersion("HTTP_1_1")
		));
	}
}

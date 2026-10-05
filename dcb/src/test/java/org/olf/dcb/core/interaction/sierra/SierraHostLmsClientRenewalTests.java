package org.olf.dcb.core.interaction.sierra;

import static java.util.Collections.emptyList;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.sierra.Paths.itemCheckoutPath;
import static org.olf.dcb.core.interaction.sierra.Paths.itemPath;
import static org.olf.dcb.core.interaction.sierra.Paths.patronCheckoutPath;
import static org.olf.dcb.core.interaction.sierra.Paths.patronPath;
import static org.olf.dcb.core.interaction.sierra.Paths.renewalPath;
import static org.olf.dcb.test.IdentifierGenerator.generateNumericLocalIdAsString;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasHttpVersion;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasJsonResponseBodyProperty;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasMessageForRequest;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestMethod;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestUrl;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsRenewal;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.CheckoutEntry;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraHostLmsClientRenewalTests {
	private static final String CIRCULATING_HOST_LMS_CODE = "sierra-item-circulating";
	private static final String BASE_URL = "https://renewal-api-tests.com";

	@Inject private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject private HostLmsFixture hostLmsFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;
	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String KEY = "renewal-key";
		final String SECRET = "renewal-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 3600);

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
		final var itemId = generateNumericLocalIdAsString();
		final var patronId = generateNumericLocalIdAsString();
		final var itemBarcode = generateNumericLocalIdAsString();
		final var patronBarcode = generateNumericLocalIdAsString();

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(itemId)
			.localPatronId(patronId)
			.localItemBarcode(itemBarcode)
			.localPatronBarcode(patronBarcode)
			.build();

		final var checkoutId = generateNumericLocalIdAsString();

		final var checkout = CheckoutEntry.builder()
			.id(toSierraUrl(patronCheckoutPath(checkoutId)))
			.patron(toSierraUrl(patronPath(patronId)))
			.item(toSierraUrl(itemPath(itemId)))
			.barcode(itemBarcode)
			.build();

		sierraItemsAPIFixture.checkoutsForItem(itemId, checkout);
		sierraPatronsAPIFixture.mockRenewalSuccess(checkoutId, checkout);

		// Act
		final var client = hostLmsFixture.createClient(CIRCULATING_HOST_LMS_CODE);

		final var response = singleValueFrom(client.renew(hostLmsRenewal));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, allOf(
			hasProperty("localItemId", is(itemId)),
			hasProperty("localPatronId", is(patronId)),
			hasProperty("localItemBarcode", is(itemBarcode)),
			hasProperty("localPatronBarcode", is(patronBarcode))
		));
	}

	@Test
	void shouldReturnProblemWhenNoCheckoutRecordsAreFound() {
		// Arrange
		final var itemId = generateNumericLocalIdAsString();
		final var patronId = generateNumericLocalIdAsString();
		final var itemBarcode = generateNumericLocalIdAsString();
		final var patronBarcode = generateNumericLocalIdAsString();

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(itemId)
			.localPatronId(patronId)
			.localItemBarcode(itemBarcode)
			.localPatronBarcode(patronBarcode)
			.build();

		sierraItemsAPIFixture.checkoutsForItem(itemId, emptyList());

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
		final var itemId = generateNumericLocalIdAsString();
		final var patronId = generateNumericLocalIdAsString();
		final var itemBarcode = generateNumericLocalIdAsString();
		final var patronBarcode = generateNumericLocalIdAsString();

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(itemId)
			.localPatronId(patronId)
			.localItemBarcode(itemBarcode)
			.localPatronBarcode(patronBarcode)
			.build();

		// With different checkout and patron IDs
		sierraItemsAPIFixture.checkoutsForItem(itemId,
			CheckoutEntry.builder()
				.id(generateNumericLocalIdAsString())
				.patron(generateNumericLocalIdAsString())
				.build());

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
		final var itemId = generateNumericLocalIdAsString();
		final var patronId = generateNumericLocalIdAsString();
		final var itemBarcode = generateNumericLocalIdAsString();
		final var patronBarcode = generateNumericLocalIdAsString();

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(itemId)
			.localPatronId(patronId)
			.localItemBarcode(itemBarcode)
			.localPatronBarcode(patronBarcode)
			.build();

		// With different checkout and item IDs
		sierraItemsAPIFixture.checkoutsForItem(itemId, List.of(
			CheckoutEntry.builder()
				.id(generateNumericLocalIdAsString())
				.item(generateNumericLocalIdAsString())
				.patron(patronId)
				.build(),
			CheckoutEntry.builder()
				.id(generateNumericLocalIdAsString())
				.item(generateNumericLocalIdAsString())
				.patron(patronId)
				.build()
		));

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
		final var itemId = generateNumericLocalIdAsString();
		final var patronId = generateNumericLocalIdAsString();
		final var itemBarcode = generateNumericLocalIdAsString();
		final var patronBarcode = generateNumericLocalIdAsString();

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(itemId)
			.localPatronId(patronId)
			.localItemBarcode(itemBarcode)
			.localPatronBarcode(patronBarcode)
			.build();

		sierraItemsAPIFixture.checkoutsForItemWithNoRecordsFound(itemId);

		// Act
		final var client = hostLmsFixture.createClient(CIRCULATING_HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.renew(hostLmsRenewal)));

		// Assert
		assertThat(problem, allOf(
			hasMessageForRequest("GET", itemCheckoutPath(itemId)),
			hasResponseStatusCode(404),
			hasJsonResponseBodyProperty("code", 107),
			hasJsonResponseBodyProperty("httpStatus", 404),
			hasJsonResponseBodyProperty("name", "Record not found"),
			hasJsonResponseBodyProperty("specificCode", 0),
			hasRequestMethod("GET"),
			hasRequestUrl(toSierraUrl(itemCheckoutPath(itemId))),
			hasHttpVersion("HTTP_1_1")
		));
	}

	@Test
	void shouldReturnProblemWhenPostRenewalFails() {
		// Arrange
		final var itemId = generateNumericLocalIdAsString();
		final var patronId = generateNumericLocalIdAsString();
		final var itemBarcode = generateNumericLocalIdAsString();
		final var patronBarcode = generateNumericLocalIdAsString();

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localItemId(itemId)
			.localPatronId(patronId)
			.localItemBarcode(itemBarcode)
			.localPatronBarcode(patronBarcode)
			.build();

		final var checkoutId = generateNumericLocalIdAsString();

		sierraItemsAPIFixture.checkoutsForItem(itemId,
			CheckoutEntry.builder()
				.id(checkoutId)
				.patron(patronId)
				.build());

		sierraPatronsAPIFixture.mockRenewalNoRecordsFound(checkoutId);

		// Act
		final var client = hostLmsFixture.createClient(CIRCULATING_HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.renew(hostLmsRenewal)));

		// Assert
		assertThat(problem, allOf(
			hasMessageForRequest("POST", renewalPath(checkoutId)),
			hasResponseStatusCode(404),
			hasJsonResponseBodyProperty("code", 107),
			hasJsonResponseBodyProperty("httpStatus", 404),
			hasJsonResponseBodyProperty("name", "Record not found"),
			hasJsonResponseBodyProperty("specificCode", 0),
			hasRequestMethod("POST"),
			hasRequestUrl(toSierraUrl(renewalPath(checkoutId))),
			hasHttpVersion("HTTP_1_1")
		));
	}

	private static String toSierraUrl(String subPath) {
		return BASE_URL + subPath;
	}
}

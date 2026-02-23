package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasNoRequestedItemBarcode;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasNoRequestedItemId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasRequestedItemBarcode;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasRequestedItemId;
import static org.olf.dcb.test.matchers.ThrowableMatchers.messageContains;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestBody;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestMethod;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestUrl;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.zalando.problem.ThrowableProblem;

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

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient, null);
		sierraItemsAPIFixture = sierraApiFixtureProvider.items(mockServerClient, null);
	}

	@Test
	void canPlaceATitleLevelHoldRequestAtSupplyingAgency() {
		// Arrange
		final var patronRequestId = UUID.randomUUID().toString();

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest("1000002", "b", 4093753);

		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold("1000002",
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
			hasLocalStatus("CONFIRMED")
		));
	}

	@Test
	void shouldTolerateTitleRequestTakingTimeToBecomeItemLevel() {
		// Arrange
		final var patronRequestId = UUID.randomUUID().toString();
		final var localPatronId = "285374";
		final Integer localBibId = 4093753;

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest(localPatronId, "b",
			localBibId);

		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleBibHold(localPatronId,
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864904",
			"Consortial Hold. tno=" + patronRequestId, "4573643");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var placedRequest = singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
			PlaceHoldRequestParameters.builder()
				.localBibId(localBibId.toString())
				.localPatronId(localPatronId)
				.patronRequestId(patronRequestId)
				.build()));

		// Assert
		assertThat(placedRequest, allOf(
			is(notNullValue()),
			hasLocalId("864904"),
			hasLocalStatus("PLACED"),
			hasNoRequestedItemId(),
			hasNoRequestedItemBarcode()
		));
	}

	@Test
	void shouldTolerateItemNotFoundAfterTitleRequestChangesToItemRequest() {
		// Arrange
		final var patronRequestId = UUID.randomUUID().toString();
		final var localPatronId = "8058274";
		final Integer localBibId = 4093753;
		final var localItemId = "6721574";

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest(localPatronId, "b", localBibId);

		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold(localPatronId,
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864904",
			"Consortial Hold. tno=" + patronRequestId, localItemId);

		sierraItemsAPIFixture.mockGetItemByIdReturnNoRecordsFound(localItemId);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var placedRequest = singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
			PlaceHoldRequestParameters.builder()
				.localBibId(localBibId.toString())
				.localPatronId(localPatronId)
				.patronRequestId(patronRequestId)
				.build()));

		// Assert
		assertThat(placedRequest, allOf(
			is(notNullValue()),
			hasLocalId("864904"),
			hasRequestedItemId(localItemId),
			hasNoRequestedItemBarcode(),
			hasLocalStatus("CONFIRMED")
		));
	}

	@Test
	void shouldHandleSierraXCircThisRecordIsNotAvailableError() {
		// Arrange
		final var patronRequestId = UUID.randomUUID().toString();
		final var localPatronId = "567215";
		final var localBibId = 23423423;
		final var localItemId = "6721574";

		sierraPatronsAPIFixture.thisRecordIsNotAvailableResponse(localPatronId, "b");

		sierraItemsAPIFixture.mockGetItemById("23423423",
			SierraItem.builder()
				.id(localItemId)
				.statusCode("-")
				.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localBibId(Integer.toString(localBibId))
					.localPatronId(localPatronId)
					.patronRequestId(patronRequestId)
					.build())));

		// Assert
		assertThat(problem, allOf(
			hasRequestMethod("POST"),
			hasRequestUrl("https://supplying-agency-service-tests.com/iii/sierra-api/v6/patrons/%s/holds/requests".formatted(localPatronId)),
			hasRequestBody(is("PatronHoldPost(recordType=b, recordNumber=23423423, pickupLocation=null, " +
				"neededBy=null, numberOfCopies=null, note=null)"))
		));

		assertThat(problem.toString(), containsString("sierra-place-hold-tests XCirc Error: This record is not available"));
		assertThat(problem.toString(), containsString("responseBody"));
		assertThat(problem.toString(), containsString("additionalData"));
		assertThat(problem.toString(), containsString("id=6721574"));
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

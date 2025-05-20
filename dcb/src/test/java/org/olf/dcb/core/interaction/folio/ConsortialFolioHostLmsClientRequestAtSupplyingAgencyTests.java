package org.olf.dcb.core.interaction.folio;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasJsonResponseBodyProperty;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasMessageForHostLms;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;
import static org.olf.dcb.test.matchers.interaction.ProblemMatchers.hasDetail;
import static org.olf.dcb.test.matchers.interaction.ProblemMatchers.hasTitle;
import static services.k_int.utils.UUIDUtils.dnsUUID;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.CannotPlaceRequestProblem;
import org.olf.dcb.core.interaction.HostLmsRenewal;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
class ConsortialFolioHostLmsClientRequestAtSupplyingAgencyTests {
	private static final String SUPPLYING_HOST_LMS_CODE = "folio-supplying-host-lms";
	private static final String PICKUP_HOST_LMS_CODE = "pickup-host-lms";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockFolioFixture mockFolioFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();
		agencyFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(SUPPLYING_HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		hostLmsFixture.createFolioHostLms(PICKUP_HOST_LMS_CODE,
			"https://fake-pickup-folio", "", "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);
	}

	
	@Test
	void shouldPlaceRequestSuccessfully() {
		// Arrange
		final var itemId = randomUUID().toString();
		final var itemBarcode = "68526614";

		final var patronId = randomUUID().toString();
		final var patronBarcode = "67129553";

		mockFolioFixture.mockCreateTransaction(CreateTransactionResponse.builder()
			.status("CREATED")
			.build());

		final var pickupAgency = definePickupAgency();
		final var pickupLibrary = definePickupLibrary();
		final var pickupLocation = definePickupLocation();

		// Act
		final var client = hostLmsFixture.createClient(SUPPLYING_HOST_LMS_CODE);

		final var placedRequest = singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localItemId(itemId)
					.localItemBarcode(itemBarcode)
					.localPatronId(patronId)
					.localPatronBarcode(patronBarcode)
					.localPatronType("undergrad")
					.pickupAgency(pickupAgency)
					.pickupLocation(pickupLocation)
					.pickupLibrary(pickupLibrary)
					.build()));

		// Assert
		assertThat("Placed request is not null", placedRequest, is(notNullValue()));
		assertThat("Should be transaction ID but cannot be explicit",
			placedRequest, hasLocalId());

		assertThat(placedRequest, hasLocalStatus(HOLD_CONFIRMED));

		mockFolioFixture.verifyCreateTransaction(CreateTransactionRequest.builder()
			.role("LENDER")
			.item(CreateTransactionRequest.Item.builder()
				.id(itemId)
				.barcode(itemBarcode)
				.build())
			.patron(CreateTransactionRequest.Patron.builder()
				.id(patronId)
				.barcode(patronBarcode)
				.group("\"undergrad\"")
				.build())
			.pickup(CreateTransactionRequest.Pickup.builder()
				.servicePointId(dnsUUID("FolioServicePoint:" + pickupAgency.getCode()).toString())
				.servicePointName("PrintLabel")
				.libraryCode("LibAbbrName")
				.build())
			.build());
	}

	@Test
	void shouldFailWhenTransactionCreationReturnsValidationError() {
		// Arrange
		mockFolioFixture.mockCreateTransaction(response()
			.withStatusCode(422)
			.withBody(json(ConsortialFolioHostLmsClient.ValidationError.builder()
				.errors(List.of(ConsortialFolioHostLmsClient.ValidationError.Error.builder()
					.message("Something went wrong")
					.type("-1")
					.code("VALIDATION_ERROR")
					.build()))
				.build())));

		final var pickupAgency = definePickupAgency();

		// Act
		final var client = hostLmsFixture.createClient(SUPPLYING_HOST_LMS_CODE);

		final var problem = assertThrows(CannotPlaceRequestProblem.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localItemId(randomUUID().toString())
					.localItemBarcode("6736583")
					.localPatronId(randomUUID().toString())
					.localPatronBarcode("8847474")
					.localPatronType("undergrad")
					.pickupAgency(pickupAgency)
					.build())));

		// Assert
		assertThat(problem, allOf(
			hasTitle("Cannot Place Request in Host LMS \"%s\"".formatted(SUPPLYING_HOST_LMS_CODE)),
			hasDetail("Something went wrong"),
			hasResponseStatusCode(422)
		));
	}

	@Test
	void shouldFailWhenTransactionCreationReturnsNotFoundError() {
		// Arrange
		mockFolioFixture.mockCreateTransaction(response()
			.withStatusCode(404)
			.withBody(json(ConsortialFolioHostLmsClient.ValidationError.builder()
				.errors(List.of(ConsortialFolioHostLmsClient.ValidationError.Error.builder()
					.message("Patron group not found with name unknown group")
					.type("-1")
					.code("NOT_FOUND_ERROR")
					.build()))
				.build())));

		final var pickupAgency = definePickupAgency();

		// Act
		final var client = hostLmsFixture.createClient(SUPPLYING_HOST_LMS_CODE);

		final var problem = assertThrows(CannotPlaceRequestProblem.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localItemId(randomUUID().toString())
					.localItemBarcode("4759385")
					.localPatronId(randomUUID().toString())
					.localPatronBarcode("2365865")
					.localPatronType("undergrad")
					.pickupAgency(pickupAgency)
					.build())));

		log.debug("Problem Parameters {}", problem.getParameters());

		// Assert
		assertThat(problem, allOf(
			hasTitle("Cannot Place Request in Host LMS \"%s\"".formatted(SUPPLYING_HOST_LMS_CODE)),
			hasDetail("Patron group not found with name unknown group"),
			hasResponseStatusCode(404)
		));
	}

	@Test
	void shouldFailWhenTransactionCreationReturnsUnauthorised() {
		// Arrange
		mockFolioFixture.mockCreateTransaction(response()
			.withStatusCode(401)
			.withBody(json(ConsortialFolioHostLmsClient.TransactionUnauthorisedResponse.builder()
				.timestamp("2024-01-17T17:07:26.251+00:00")
				.status(401)
				.error("Unauthorized")
				// The transaction ID in this path is an example
				// it won't be the same as generated by the code running for this test
				.path("/dcbService/transactions/9fc11ffd-3c07-4b75-a6db-045127c43dc1")
				.build())));

		final var pickupAgency = definePickupAgency();

		// Act
		final var client = hostLmsFixture.createClient(SUPPLYING_HOST_LMS_CODE);

		final var exception = assertThrows(InvalidApiKeyException.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localItemId(randomUUID().toString())
					.localItemBarcode("7837315")
					.localPatronId(randomUUID().toString())
					.localPatronBarcode("5486193")
					.localPatronType("undergrad")
					.pickupAgency(pickupAgency)
					.build())));

		// Assert
		assertThat(exception, hasProperty("cause",
			instanceOf(HttpClientResponseException.class)));
	}

	@Test
	void shouldFailWhenTransactionCreationReturnsUnexpectedHttpResponse() {
		// Arrange
		mockFolioFixture.mockCreateTransaction(response()
			.withStatusCode(400)
			// This is a made up body that is only intended to demonstrate how it's captured
			.withBody(json(Map.of("message", "something went wrong"))));

		final var pickupAgency = definePickupAgency();

		// Act
		final var client = hostLmsFixture.createClient(SUPPLYING_HOST_LMS_CODE);

		final var problem = assertThrows(UnexpectedHttpResponseProblem.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localItemId(randomUUID().toString())
					.localItemBarcode("46397196")
					.localPatronId(randomUUID().toString())
					.localPatronBarcode("9265614")
					.localPatronType("undergrad")
					.pickupAgency(pickupAgency)
					.build())));

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms(SUPPLYING_HOST_LMS_CODE),
			hasResponseStatusCode(400),
			hasJsonResponseBodyProperty("message", "something went wrong")
		));
	}

	@Test
	void shouldRenewItemSuccessfully() {
		// Arrange
		final var localTransactionId = randomUUID().toString();

		mockFolioFixture.mockRenewTransaction(localTransactionId, response()
			.withStatusCode(200)
			.withBody(json(TransactionStatus.builder()
				.status("ITEM_CHECKED_OUT")
				.build())));

		final var localItemId = randomUUID().toString();
		final var localItemBarcode = "1357921";
		final var localPatronId = randomUUID().toString();
		final var localPatronBarcode = "6543219";

		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localRequestId(localTransactionId)
			.localItemId(localItemId)
			.localPatronId(localPatronId)
			.localItemBarcode(localItemBarcode)
			.localPatronBarcode(localPatronBarcode)
			.build();

		// Act
		final var client = hostLmsFixture.createClient(SUPPLYING_HOST_LMS_CODE);

		final var response = singleValueFrom(client.renew(hostLmsRenewal));

		// Assert
		mockFolioFixture.verifyRenewTransaction(localTransactionId);

		assertThat("Should echo back renewal parameters", response, allOf(
			notNullValue(),
			hasProperty("localItemId", is(localItemId)),
			hasProperty("localItemBarcode", is(localItemBarcode)),
			hasProperty("localPatronId", is(localPatronId)),
			hasProperty("localPatronBarcode", is(localPatronBarcode))
		));
	}

	@Test
	void shouldFailToRenewWhenNoRequestIdProvided() {
		// Arrange
		final var hostLmsRenewal = HostLmsRenewal.builder()
			.localRequestId(null)
			.localItemId(randomUUID().toString())
			.localItemBarcode("0583763")
			.localPatronId(randomUUID().toString())
			.localPatronBarcode("20457275")
			.build();

		// Act
		final var client = hostLmsFixture.createClient(SUPPLYING_HOST_LMS_CODE);

		final var error = assertThrows(RuntimeException.class,
			() -> singleValueFrom(client.renew(hostLmsRenewal)));

		// Assert
		assertThat(error, allOf(
			notNullValue(),
			hasMessage("Cannot renew transaction without a transaction ID")
		));
	}

	@Test
	void shouldFailRenewalWhenUnexpectedHttpResponseIsReturned() {
		final var localTransactionId = randomUUID().toString();

		mockFolioFixture.mockRenewTransaction(localTransactionId, response()
			.withStatusCode(400)
			// This is a made up body that is only intended to demonstrate how it's captured
			.withBody(json(Map.of("message", "something went wrong"))));

		// Act
		final var client = hostLmsFixture.createClient(SUPPLYING_HOST_LMS_CODE);

		final var problem = assertThrows(UnexpectedHttpResponseProblem.class,
			() -> singleValueFrom(client.renew(HostLmsRenewal.builder()
				.localRequestId(localTransactionId)
				.localItemId(randomUUID().toString())
				.localItemBarcode("19473645")
				.localPatronId(randomUUID().toString())
				.localPatronBarcode("57663652")
				.build())));

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms(SUPPLYING_HOST_LMS_CODE),
			hasResponseStatusCode(400),
			hasJsonResponseBodyProperty("message", "something went wrong")
		));
	}

	private DataAgency definePickupAgency() {
		return agencyFixture.defineAgency("pickup-agency",
			"Pickup Agency", hostLmsFixture.findByCode(PICKUP_HOST_LMS_CODE));
	}

	private Library definePickupLibrary() {
		return Library.builder()
			.abbreviatedName("LibAbbrName")
			.build();
	}

	private Location definePickupLocation() {
		return Location.builder()
			.printLabel("PrintLabel")
			.build();

	}

}


package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.core.interaction.HostLmsHold.HOLD_PLACED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static services.k_int.utils.UUIDUtils.dnsUUID;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.CannotPlaceRequestException;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientRequestAtSupplyingAgencyTests {
	private static final String HOST_LMS_CODE = "folio-supplying-request-tests";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockFolioFixture mockFolioFixture;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();
		agencyFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);

		client = hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	@Test
	void shouldPlaceRequestSuccessfully() {
		// Arrange
		final var itemId = UUID.randomUUID().toString();
		final var itemBarcode = "68526614";

		final var patronId = UUID.randomUUID().toString();
		final var patronBarcode = "67129553";

		mockFolioFixture.mockCreateTransaction(CreateTransactionResponse.builder()
			.status("CREATED")
			.build());

		final var pickupAgency = agencyFixture.defineAgency("pickup-agency", "Pickup Agency");

		// Act
		final var placedRequest = singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localItemId(itemId)
					.localItemBarcode(itemBarcode)
					.localPatronId(patronId)
					.localPatronBarcode(patronBarcode)
					.pickupAgency(pickupAgency)
					.build()));

		// Assert
		assertThat("Placed request is not null", placedRequest, is(notNullValue()));
		assertThat("Should be transaction ID but cannot be explicit",
			placedRequest, hasLocalId());

		assertThat(placedRequest, hasLocalStatus(HOLD_PLACED));

		mockFolioFixture.verifyCreateTransaction(CreateTransactionRequest.builder()
			.role("LENDER")
			.item(CreateTransactionRequest.Item.builder()
				.id(itemId)
				.barcode(itemBarcode)
				.build())
			.patron(CreateTransactionRequest.Patron.builder()
				.id(patronId)
				.barcode(patronBarcode)
				.build())
			.pickup(CreateTransactionRequest.Pickup.builder()
				.servicePointId(dnsUUID("FolioServicePoint:" + pickupAgency.getCode()).toString())
				.servicePointName("Pickup Agency")
				.libraryCode("pickup-agency")
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

		final var pickupAgency = agencyFixture.defineAgency("pickup-agency", "Pickup Agency");

		// Act
		final var exception = assertThrows(CannotPlaceRequestException.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localItemId(UUID.randomUUID().toString())
					.localItemBarcode("6736583")
					.localPatronId(UUID.randomUUID().toString())
					.localPatronBarcode("8847474")
					.pickupAgency(pickupAgency)
					.build())));

		// Assert
		assertThat(exception, hasMessage("Something went wrong"));
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

		final var pickupAgency = agencyFixture.defineAgency("pickup-agency", "Pickup Agency");

		// Act
		final var exception = assertThrows(CannotPlaceRequestException.class,
			() -> singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.localItemId(UUID.randomUUID().toString())
					.localItemBarcode("4759385")
					.localPatronId(UUID.randomUUID().toString())
					.localPatronBarcode("2365865")
					.pickupAgency(pickupAgency)
					.build())));

		// Assert
		assertThat(exception, hasMessage("Patron group not found with name unknown group"));
	}
}

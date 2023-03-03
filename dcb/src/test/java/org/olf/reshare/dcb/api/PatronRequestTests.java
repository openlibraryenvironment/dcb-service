package org.olf.reshare.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.OK;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import org.olf.reshare.dcb.test.DcbTest;
import org.olf.reshare.dcb.test.PatronRequestsDataAccess;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import net.minidev.json.JSONObject;

@DcbTest
class PatronRequestTests {
	@Inject
	@Client("/")
	HttpClient client;

	@Inject
	PatronRequestRepository patronRequestRepository;

	@Inject
	SupplierRequestRepository supplierRequestRepository;

	@Inject
	PatronRequestsDataAccess patronRequestsDataAccess;

	@Inject
	PatronRequestApiClient patronRequestApiClient;

	@Inject
	AdminApiClient adminApiClient;

	@BeforeEach
	void beforeEach() {
		patronRequestsDataAccess.deleteAllPatronRequests();
	}

	@Test
	void canPlacePatronRequest() {
		final var bibClusterId = UUID.randomUUID();

		final var response = patronRequestApiClient.placePatronRequest(bibClusterId,
			"jane-smith", "RGX12", "ABC123");

		assertThat(response.getStatus(), is(OK));

		final var placedPatronRequest = response.body();

		assertThat(placedPatronRequest, is(notNullValue()));

		assertThat(placedPatronRequest.id(), is(notNullValue()));

		assertThat(placedPatronRequest.citation(), is(notNullValue()));
		assertThat(placedPatronRequest.citation().bibClusterId(), is(bibClusterId));

		assertThat(placedPatronRequest.requestor(), is(notNullValue()));
		assertThat(placedPatronRequest.requestor().identifier(), is("jane-smith"));

		assertThat(placedPatronRequest.requestor().agency(), is(notNullValue()));
		assertThat(placedPatronRequest.requestor().agency().code(), is("RGX12"));

		assertThat(placedPatronRequest.pickupLocation(), is(notNullValue()));
		assertThat(placedPatronRequest.pickupLocation().code(), is("ABC123"));
	}

	@Test
	void canGetPlacedPatronRequestViaAdminAPI() {
		final var bibClusterId = UUID.randomUUID();

		final var placeRequestResponse = patronRequestApiClient.placePatronRequest(
			bibClusterId, "alice-stevens", "BVN45", "HTF56");

		assertThat(placeRequestResponse.getStatus(), is(OK));

		final var id = requireNonNull(placeRequestResponse.body()).id();

		var patronRequest = adminApiClient.getPatronRequestViaAdminApi(id);

		assertThat(patronRequest, is(notNullValue()));

		assertThat(patronRequest.id(), is(id));

		assertThat(patronRequest.citation(), is(notNullValue()));
		assertThat(patronRequest.citation().bibClusterId(), is(bibClusterId));

		assertThat(patronRequest.requestor(), is(notNullValue()));
		assertThat(patronRequest.requestor().identifier(), is("alice-stevens"));

		assertThat(patronRequest.requestor().agency(), is(notNullValue()));
		assertThat(patronRequest.requestor().agency().code(), is("BVN45"));

		assertThat(patronRequest.pickupLocation(), is(notNullValue()));
		assertThat(patronRequest.pickupLocation().code(), is("HTF56"));
	}

	@Test
	void cannotPlaceRequestWhenNoInformationIsProvided() {
		// These are separate variables to only have single invocation in assertThrows
		final var blockingClient = client.toBlocking();
		final var request = HttpRequest.POST("/patrons/requests/place",
			new JSONObject());

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> blockingClient.exchange(request));

		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(BAD_REQUEST));
	}
}

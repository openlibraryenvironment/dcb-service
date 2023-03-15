package org.olf.reshare.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.OK;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
	void canPlacePatronRequestViaApiAndGetPatronRequestViaAdminApi() {
		final var bibClusterId = UUID.randomUUID();
		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(bibClusterId,
			"jane-smith", "RGX12", "ABC123");

		assertThat(placedRequestResponse.getStatus(), is(OK));

		final var patronRequestId = requireNonNull(placedRequestResponse.body()).id();

		await().atMost(1, SECONDS)
			.until(() -> adminApiClient.getPatronRequestViaAdminApi(patronRequestId), isResolved());

		final var fetchedPatronRequest = adminApiClient.getPatronRequestViaAdminApi(patronRequestId);

		assertThat(fetchedPatronRequest, is(notNullValue()));
		assertThat(fetchedPatronRequest.id(), is(patronRequestId));
		assertThat(fetchedPatronRequest.citation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.citation().bibClusterId(), is(bibClusterId));
		assertThat(fetchedPatronRequest.requestor(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().identifier(), is("jane-smith"));
		assertThat(fetchedPatronRequest.requestor().agency(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().agency().code(), is("RGX12"));
		assertThat(fetchedPatronRequest.pickupLocation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.pickupLocation().code(), is("ABC123"));
		assertThat(fetchedPatronRequest.status(), is(notNullValue()));
		assertThat(fetchedPatronRequest.status().code(), is(RESOLVED));

		// supplier request
		assertThat(fetchedPatronRequest.supplierRequests(), is(notNullValue()));
		assertThat(fetchedPatronRequest.supplierRequests(), hasSize(1));

		final var onlySupplierRequest = fetchedPatronRequest.supplierRequests().get(0);

		assertThat(onlySupplierRequest, is(notNullValue()));
		assertThat(onlySupplierRequest.id(), is(notNullValue()));
		assertThat(onlySupplierRequest.agency().code(), is("fake agency"));
		assertThat(onlySupplierRequest.item().id(), is(notNullValue()));
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

	private static Matcher<Object> isResolved() {
		return hasProperty("statusCode", is("RESOLVED"));
	}
}

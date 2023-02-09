package org.olf.reshare.dcb;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.api.datavalidation.PatronRequestCommand;
import org.olf.reshare.dcb.core.model.*;
import org.olf.reshare.dcb.core.model.builders.*;
import org.olf.reshare.dcb.test.DcbTest;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@DcbTest
class PatronRequestTest {


	public static final Logger log = LoggerFactory.getLogger(PatronRequestTest.class);
	@Inject
	@Client("/")
	HttpClient client;

	@Test
	void canPlacePatronRequest() {

		// build payload
		PatronRequestCommand patronRequestCommand = new PatronRequestCommand(null, null, null, null);

		// place request
		HttpRequest<?> placeRequestRequest = HttpRequest.POST("/patrons/requests/place", patronRequestCommand);
		HttpResponse<PatronRequest> placeRequestResponse = client.toBlocking()
			.exchange(placeRequestRequest, PatronRequest.class);

		log.debug("Placed test patron request: {}", placeRequestResponse);

		assertNotNull(placeRequestResponse);
		assertEquals(placeRequestResponse.getStatus(), BAD_REQUEST);


		// check we can fetch with id
//		UUID PR_ID = placeRequestResponse.body().getId();
//		HttpRequest<?> getPatronRequestRequest = HttpRequest.GET("/admin/patrons/requests/" + PR_ID);
//		HttpResponse<PatronRequest> getPatronRequestResponse = client.toBlocking()
//				.exchange(getPatronRequestRequest, PatronRequest.class);
//
//		log.debug("Fetching test patron request: {}", getPatronRequestResponse);
//
//		assertNotNull(getPatronRequestResponse);
//		assertEquals(getPatronRequestResponse.body().getId(), PR_ID);
	}

//	@Test
//	void mustProvideValidPayloadToPlaceRequest () {
//
//		// build payload
//		PatronRequest patronRequest = new PatronRequestBuilder()
//			.createPatronRequest();
//
//		final HttpClientResponseException exception = Assertions.assertThrows(HttpClientResponseException.class, () -> {
//			// place request & throw 401
//			HttpRequest<?> placeRequestRequest = HttpRequest.POST("/patrons/requests/place", patronRequest);
//			client.toBlocking().exchange(placeRequestRequest, PatronRequest.class);;
//		});
//		Assertions.assertEquals(BAD_REQUEST, exception.getStatus());
//	}
}

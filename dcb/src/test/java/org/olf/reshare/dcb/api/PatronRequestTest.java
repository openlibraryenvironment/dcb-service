package org.olf.reshare.dcb.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.api.datavalidation.AgencyCommand;
import org.olf.reshare.dcb.core.api.datavalidation.CitationCommand;
import org.olf.reshare.dcb.core.api.datavalidation.PatronRequestCommand;
import org.olf.reshare.dcb.core.api.datavalidation.PickupLocationCommand;
import org.olf.reshare.dcb.core.api.datavalidation.RequestorCommand;
import org.olf.reshare.dcb.test.DcbTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

@DcbTest
class PatronRequestTest {


	public static final Logger log = LoggerFactory.getLogger(PatronRequestTest.class);
	@Inject
	@Client("/")
	HttpClient client;

	@Test
	public void testPlacePatronRequestValidation() {

		HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
			PatronRequestCommand patronRequestCommand = new PatronRequestCommand(null, null, null, null);
			client.toBlocking().exchange(HttpRequest.POST("/patrons/requests/place", patronRequestCommand ));
		});

		HttpResponse<?> response = e.getResponse();
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());


		CitationCommand citationCommand = new CitationCommand();
		citationCommand.setBibClusterId("ad0cd9fe-9bef-4942-8a16-a9fa677e7146");

		RequestorCommand requestorCommand = new RequestorCommand();
		requestorCommand.setIdentifiier("jane-smith");
		AgencyCommand agencyCommand = new AgencyCommand();
		agencyCommand.setCode("RGX12");
		requestorCommand.setAgency(agencyCommand);

		PickupLocationCommand pickupLocationCommand = new PickupLocationCommand();
		pickupLocationCommand.setCode("ABC123");

		PatronRequestCommand patronRequestCommand = new PatronRequestCommand();
		patronRequestCommand.setCitation( citationCommand );
		patronRequestCommand.setRequestor( requestorCommand );
		patronRequestCommand.setPickupLocation( pickupLocationCommand );

		HttpResponse<PatronRequestCommand> rp = client.toBlocking().exchange(HttpRequest.POST("/patrons/requests/place", patronRequestCommand));
		assertEquals(HttpStatus.OK, rp.getStatus());
	}

}

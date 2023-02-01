package org.olf.reshare.dcb.sierra;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import services.k_int.interaction.gokb.GokbApiClient;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.SierraError;
import services.k_int.interaction.sierra.bibs.BibParams;
import services.k_int.test.mockserver.MockServerMicronautTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@MockServerMicronautTest
public class SirraErrorTests {

	@Inject
	SierraApiClient client;

	@Test
	public void testSierraError () {

		SierraError exception = new SierraError();

		exception.setDescription("description");
		exception.setName("name");
		exception.setSpecificCode(400);
		exception.setCode(400);

		assertNotNull(exception);
		Assertions.assertEquals(exception.getMessage(), "name: description - [400 / 400]");
		Assertions.assertEquals(exception.toString(), "name: description - [400 / 400]");
	}

//	@Test
//	public void test401Error () {
//
//		// Throw the error
//		final HttpClientResponseException exception = Assertions.assertThrows(HttpClientResponseException.class, () -> {
//			Mono.from( client.bibs(null, null, null, null, null, null, null, null, null) ).block();
//		});
//		Assertions.assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
//	}


}

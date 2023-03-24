package services.k_int.interaction.sierra;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;
import reactor.core.publisher.Mono;
import services.k_int.interaction.auth.AuthToken;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
@MockServerMicronautTest
public class SierraApiLoginTests {
	@Inject
	SierraApiClient client;
	@Inject
	ResourceLoader loader;
	private final String MOCK_ROOT = "classpath:mock-responses/sierra/login";

	@Test
	public void testLoginTokenType ( MockServerClient mock ) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("POST")
				.withPath("/iii/sierra-api/v6/token")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-login.json")
						.orElseThrow()
						.readAllBytes()))));


		var response = Mono.from( client.login("key", "secret") ).block();
		System.out.println(response);
		assertNotNull(response);
		assertEquals(response.type().toLowerCase(), "bearer");
	}

	@Test
	public void testLoginTokenExpiration ( MockServerClient mock ) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("POST")
				.withPath("/iii/sierra-api/v6/token")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-login.json")
						.orElseThrow()
						.readAllBytes()))));

		var response = Mono.from( client.login("key", "secret") ).block();
		assertNotNull(response);
		assertEquals(response.getClass(), AuthToken.class);
		assertFalse( response.isExpired() );
		assertTrue( response.expires().isAfter(Instant.MIN) );
	}

	@Test
	public void testLoginTokenUnique ( MockServerClient mock ) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("POST")
				.withPath("/iii/sierra-api/v6/token")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-login.json")
						.orElseThrow()
						.readAllBytes()))));

		var token1 = Mono.from( client.login("key", "secret") ).block();
		var token2 = Mono.from( client.login("key", "secret") ).block();

		assertNotNull(token1);
		assertNotNull(token2);
		assertEquals(token1.getClass(), AuthToken.class);
		assertEquals(token2.getClass(), AuthToken.class);
		assertNotSame(token1, token2);
	}
}

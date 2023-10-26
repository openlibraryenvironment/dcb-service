package org.olf.dcb;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.OK;
import static io.micronaut.http.MediaType.TEXT_PLAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.clients.LoginClient;

import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import lombok.SneakyThrows;

@DcbTest
class JwtAuthenticationTest {

	@Inject
	@Client("/")
	HttpClient client;

	@Inject
	LoginClient loginClient;

	@Test
	void accessingASecuredUrlWithoutAuthenticatingReturnsUnauthorized() {
		HttpClientResponseException e = assertThrows(
			HttpClientResponseException.class, () -> {
				client.toBlocking().exchange(HttpRequest.GET("/").accept(TEXT_PLAIN));
			});

		assertEquals(BAD_REQUEST, e.getStatus());
	}

	@SneakyThrows
	@Test
	void uponSuccessfulAuthenticationAJsonWebTokenIsIssuedToTheUser() {
		final var bearerAccessRefreshToken = loginClient.login("user", "password");

		assertEquals("user", bearerAccessRefreshToken.getUsername());
		assertNotNull(bearerAccessRefreshToken.getAccessToken());
		assertTrue(JWTParser
			.parse(bearerAccessRefreshToken.getAccessToken()) instanceof SignedJWT);

		String accessToken = bearerAccessRefreshToken.getAccessToken();
		HttpRequest<?> requestWithAuthorization = HttpRequest.GET("/")
			.accept(TEXT_PLAIN).bearerAuth(accessToken);
		HttpResponse<String> response = client.toBlocking()
			.exchange(requestWithAuthorization, String.class);

		assertEquals(OK, response.getStatus());
		assertEquals("user", response.body());
	}
}

package org.olf.reshare.dcb;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.OK;
import static io.micronaut.http.MediaType.TEXT_PLAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.ParseException;

import org.junit.jupiter.api.Test;

import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.OK;
import static io.micronaut.http.MediaType.TEXT_PLAIN;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken;
import jakarta.inject.Inject;

@DcbTest
class JwtAuthenticationTest {

	@Inject
	@Client("/")
	HttpClient client;

	@Test
	void accessingASecuredUrlWithoutAuthenticatingReturnsUnauthorized () {
		HttpClientResponseException e = assertThrows(
		    HttpClientResponseException.class, () -> {
			    client.toBlocking().exchange(HttpRequest.GET("/").accept(TEXT_PLAIN));
		    });

		assertEquals(BAD_REQUEST, e.getStatus());
	}

	@Test
	void uponSuccessfulAuthenticationAJsonWebTokenIsIssuedToTheUser ()
	    throws ParseException {
		UsernamePasswordCredentials creds = new UsernamePasswordCredentials("user",
		    "password");
		HttpRequest<?> request = HttpRequest.POST("/login", creds);
		HttpResponse<BearerAccessRefreshToken> rsp = client.toBlocking()
		    .exchange(request, BearerAccessRefreshToken.class);
		assertEquals(OK, rsp.getStatus());

		BearerAccessRefreshToken bearerAccessRefreshToken = rsp.body();
		assertEquals("user", bearerAccessRefreshToken.getUsername());
		assertNotNull(bearerAccessRefreshToken.getAccessToken());
		assertTrue(JWTParser
		    .parse(bearerAccessRefreshToken.getAccessToken()) instanceof SignedJWT);

		String accessToken = bearerAccessRefreshToken.getAccessToken();
		HttpRequest<?> requestWithAuthorization = HttpRequest.GET("/")
		    .accept(TEXT_PLAIN).bearerAuth(accessToken);
		HttpResponse<String> response = client.toBlocking()
		    .exchange(requestWithAuthorization, String.class);

		assertEquals(OK, rsp.getStatus());
		assertEquals("user", response.body());
	}
}

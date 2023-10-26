package org.olf.dcb.test.clients;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;
import jakarta.inject.Singleton;

@Singleton
public class LoginClient {
	private final HttpClient httpClient;

	public LoginClient(@Client("/") HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	public String getAccessToken() {
		final var credentials = new UsernamePasswordCredentials("admin", "password");

		final var loginRequest = HttpRequest.POST("/login", credentials);

		final var loginResponse = httpClient.toBlocking().exchange(loginRequest, BearerAccessRefreshToken.class);

		final var bearerAccessRefreshToken = loginResponse.body();

		return bearerAccessRefreshToken.getAccessToken();
	}
}

package org.olf.dcb.test.clients;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
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
		final var loginResponse = login("admin", "password");

		final var bearerAccessRefreshToken = loginResponse.body();

		return bearerAccessRefreshToken.getAccessToken();
	}

	public HttpResponse<BearerAccessRefreshToken> login(String username, String password) {
		final var credentials = new UsernamePasswordCredentials(username, password);

		final var loginRequest = HttpRequest.POST("/login", credentials);

		return httpClient.toBlocking().exchange(loginRequest, BearerAccessRefreshToken.class);
	}
}

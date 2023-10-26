package org.olf.dcb.test.clients;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;

public class LoginClient {
	public static String getAccessToken(BlockingHttpClient blockingClient) {
		final var credentials = new UsernamePasswordCredentials("admin", "password");

		final var loginRequest = HttpRequest.POST("/login", credentials);
		final var loginResponse = blockingClient.exchange(loginRequest, BearerAccessRefreshToken.class);
		final var bearerAccessRefreshToken = loginResponse.body();

		return bearerAccessRefreshToken.getAccessToken();
	}
}

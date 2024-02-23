package org.olf.dcb.test.clients;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;

@Client("/")
public interface LoginClient {
//	default String getAccessToken() {
//		return login("admin", "password").getAccessToken();
//	}
//
//	default BearerAccessRefreshToken login(String username, String password) {
//		final var credentials = new UsernamePasswordCredentials(username, password);
//
//		return login(credentials);
//	}
//
//	@Post("/login")
//	BearerAccessRefreshToken login(@Body UsernamePasswordCredentials credentials);
}

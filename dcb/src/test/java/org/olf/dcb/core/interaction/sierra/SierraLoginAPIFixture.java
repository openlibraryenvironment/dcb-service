package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.http.HttpHeaderValues.AUTHORIZATION_PREFIX_BASIC;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.UNAUTHORIZED_401;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

import java.util.Base64;
import java.util.Map;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.olf.dcb.test.TestResourceLoaderProvider;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SierraLoginAPIFixture {
	private final MockServerClient mockServer;
	private final SierraMockServerRequests sierraMockServerRequests;
	private final SierraMockServerResponses sierraMockServerResponses;

	public SierraLoginAPIFixture(MockServerClient mockServer, TestResourceLoaderProvider provider) {
		this(mockServer, new SierraMockServerRequests("/iii/sierra-api/v6/token"),
			new SierraMockServerResponses(provider.forBasePath("classpath:mock-responses/sierra/")));
	}

	public void successfulLoginFor(String key, String secret, String token) {
		// Default to only allowing 1 login attempt
		successfulLoginFor(key, secret, token, once());
	}

	public void successfulLoginFor(String key, String secret, String token, Times times) {
		mockServer.when(sierraMockServerRequests.post()
			.withHeader("Authorization", basicAuthCredentials(key, secret)), times)
			.respond(sierraMockServerResponses.jsonSuccess(json(
				// Uses map because property names with underscores
				// are harder to produce with serialisation
				Map.of(
					"access_token", token,
					"token_type", "Bearer",
					"expires_in", 3600))));
	}

	public void failLoginsForAnyOtherCredentials(String key, String secret) {
		// Any other login attempt should fail
		mockServer.when(sierraMockServerRequests.post()
			.withHeader(string("Authorization"), not(basicAuthCredentials(key, secret))))
			.respond(response().withStatusCode(UNAUTHORIZED_401.code()));
	}

	private static String basicAuthCredentials(String key, String secret) {
		final var basicAuthHash = Base64.getEncoder()
			.encodeToString((key + ':' + secret)
			.getBytes());

		return AUTHORIZATION_PREFIX_BASIC + ' ' + basicAuthHash;
	}
}

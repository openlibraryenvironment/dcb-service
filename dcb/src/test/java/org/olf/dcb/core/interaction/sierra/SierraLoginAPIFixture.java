package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.http.HttpHeaderValues.AUTHORIZATION_PREFIX_BASIC;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.olf.dcb.test.MockServerCommonResponses.unauthorised;

import java.util.Base64;
import java.util.Map;

import org.mockserver.matchers.Times;
import org.olf.dcb.test.MockServer;
import org.olf.dcb.test.MockServerCommonRequests;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SierraLoginAPIFixture {
	private static final String LOGIN_PATH = "/iii/sierra-api/v6/token";

	private final MockServer mockServer;
	private final MockServerCommonRequests mockServerCommonRequests;

	public void successfulLoginFor(String key, String secret, String token) {
		// Default to only allowing 1 login attempt
		successfulLoginFor(key, secret, token, once());
	}

	public void successfulLoginFor(String key, String secret, String token, Times times) {
		final var request = mockServerCommonRequests.post(LOGIN_PATH)
			.withHeader("Authorization", basicAuthCredentials(key, secret));

		// Uses map because property names with underscores
		// are harder to produce with serialisation
		Map<String, Object> responseBody = Map.of(
			"access_token", token,
			"token_type", "Bearer",
			"expires_in", 3600);

		mockServer.mock(request, responseBody, times);
	}

	public void failLoginsForAnyOtherCredentials(String key, String secret) {
		// Any other login attempt should fail
		mockServer.mock(mockServerCommonRequests.post(LOGIN_PATH)
			.withHeader(string("Authorization"), not(basicAuthCredentials(key, secret))), unauthorised());
	}

	private static String basicAuthCredentials(String key, String secret) {
		final var basicAuthHash = Base64.getEncoder()
			.encodeToString((key + ':' + secret)
			.getBytes());

		return AUTHORIZATION_PREFIX_BASIC + ' ' + basicAuthHash;
	}
}

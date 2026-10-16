package org.olf.dcb.security.provisioning.keycloak;

import static io.micronaut.http.MediaType.APPLICATION_FORM_URLENCODED;
import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.olf.dcb.security.provisioning.keycloak.KeycloakIdentityProviderClient.TokenResponse;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import reactor.core.publisher.Mono;

/**
 * One access token, refreshed a minute before it expires.
 *
 * <p>Deliberately a single cached {@link Mono} rather than a map: there is exactly one
 * service account, and a map keyed on anything here would be a cache with one entry and
 * room for somebody to add a second key without thinking about its bound.
 */
class KeycloakCredentialsTokenSource {
	private final HttpClient httpClient;
	private final URI tokenUri;
	private final String clientId;
	private final String clientSecret;

	private volatile Mono<String> current;

	KeycloakCredentialsTokenSource(HttpClient httpClient, URI tokenUri, String clientId,
		String clientSecret) {

		this.httpClient = httpClient;
		this.tokenUri = tokenUri;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	Mono<String> get() {
		// Read once into a local: the field can be replaced between the null check and
		// the return, and a caller receiving a half-built value would be a genuinely
		// horrible bug to find.
		final var cached = current;

		if (cached != null) {
			return cached;
		}

		final var fetched = fetch().cache(
			response -> Duration.ofSeconds(Math.max(response.expiresIn() - 60, 30)),
			error -> Duration.ZERO,
			() -> Duration.ZERO)
			.map(TokenResponse::accessToken);

		current = fetched;

		return fetched;
	}

	private Mono<TokenResponse> fetch() {
		final var form = Map.of(
			"grant_type", "client_credentials",
			"client_id", clientId,
			"client_secret", clientSecret);

		final var request = HttpRequest.POST(tokenUri, form)
			.contentType(APPLICATION_FORM_URLENCODED)
			.accept(APPLICATION_JSON);

		return Mono.from(httpClient.retrieve(request, TokenResponse.class))
			// The message must never carry the response body: a failed token exchange
			// can echo the request, and the request contains the client secret.
			.doOnError(error -> KeycloakIdentityProviderClient.log.error(
				"Could not obtain an identity provider access token: {}",
				error.getClass().getSimpleName()));
	}
}
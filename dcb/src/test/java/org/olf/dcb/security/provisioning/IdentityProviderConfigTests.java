package org.olf.dcb.security.provisioning;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micronaut.http.client.HttpClient;

/**
 * Misconfiguration is caught where the person who caused it will see it.
 *
 * These are constructed directly rather than through an application context. A context here
 * would pull the whole bean graph - including a datasource this has nothing to do with - and
 * the property under test would be buried inside a framework behaviour nobody had actually
 * checked. Construction is where the check lives, so construction is what is tested.
 */
class IdentityProviderConfigTests {

	/** The config as a deployment would supply it, with one field varied per test. */
	private static IdentityProviderConfig config(String clientId, String clientSecret) {
		return new IdentityProviderConfig() {
			@Override
			public Optional<String> getType() {
				return Optional.of("keycloak");
			}

			@Override
			public Optional<String> getBaseUrl() {
				return Optional.of("https://keycloak.invalid");
			}

			@Override
			public Optional<String> getRealm() {
				return Optional.of("dcb");
			}

			@Override
			public Optional<String> getClientId() {
				return Optional.ofNullable(clientId);
			}

			@Override
			public String getClientSecret() {
				return clientSecret;
			}

			@Override
			public Optional<String> getProjectId() {
				return Optional.empty();
			}
		};
	}

	@Test
	@DisplayName("A blank client secret fails at construction, not at the first account")
	void aBlankSecretFailsImmediately() {
		// The no-defaulted-secret rule, and the reason it matters here specifically: a
		// blank secret would let the application start, offer account provisioning, and
		// refuse every account with a 401 from Keycloak - weeks later, in an environment
		// somebody had already been told was working.
		for (final var secret : new String[] { "", "   ", null }) {
			final var failure = assertThrows(IllegalStateException.class,
				() -> new KeycloakIdentityProviderClient(stubHttpClient(),
					config("dcb-provisioning", secret)));

			assertThat(failure.getMessage(), containsString("client-secret"));
		}
	}

	@Test
	@DisplayName("A missing client id fails too, and says which property")
	void aMissingClientIdFailsImmediately() {
		final var failure = assertThrows(IllegalStateException.class,
			() -> new KeycloakIdentityProviderClient(stubHttpClient(),
				config(null, "not-a-real-secret")));

		assertThat(failure.getMessage(), containsString("client-id"));
	}

	@Test
	@DisplayName("A complete configuration builds, and names itself")
	void aCompleteConfigurationBuilds() {
		final var client = new KeycloakIdentityProviderClient(stubHttpClient(),
			config("dcb-provisioning", "not-a-real-secret"));

		// providerName is stored on every account row, so a later migration can tell rows
		// from different providers apart. It is a value, not a label.
		assertThat(client.providerName(), is("keycloak"));
	}

	/**
	 * Never called: every assertion above fails or succeeds during construction, before any
	 * request is made. Present because the constructor takes one.
	 */
	private static HttpClient stubHttpClient() {
		return null;
	}
}

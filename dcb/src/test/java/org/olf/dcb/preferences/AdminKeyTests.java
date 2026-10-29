package org.olf.dcb.preferences;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.security.authentication.Authentication;

/**
 * The anonymity property, tested at the only place it can be broken.
 *
 * <p>No application context: this is a pure function of a secret and a claim, and it
 * needs neither a database nor a container.
 */
class AdminKeyTests {

	private static final String SUBJECT = "8f14e45f-ce0a-4c1e-9e0f-1a2b3c4d5e6f";

	/**
	 * With no secret the feature is off, and OFF IS NOT WEAKER. There is no fallback
	 * derivation to fall back to, so a deployment in this state cannot produce a row that
	 * looks protected and is not — which is the harm doctrine's no-defaulted-secret rule
	 * names. Whether a deployment is ALLOWED to be in this state is
	 * PreferencesStartupCheck's decision; see PreferencesStartupCheckTests.
	 */
	@Test
	void withNoSecretNoKeyCanBeDerivedAtAll() {
		final var adminKey = new AdminKey(configWith(null));

		assertThat(adminKey.isAvailable(), is(false));

		final var error = assertThrows(PreferenceException.class,
			() -> adminKey.derive(SUBJECT));

		assertThat(error.status(), is(io.micronaut.http.HttpStatus.SERVICE_UNAVAILABLE));
		assertThat(error.code(), is("PREFERENCES_UNAVAILABLE"));
	}

	@Test
	void aBlankSecretIsTreatedAsNoSecret() {
		assertThat(new AdminKey(configWith("   ")).isAvailable(), is(false));
	}

	/** Explicitly off is off, whatever the environment happens to carry. */
	@Test
	void anExplicitlyDisabledDeploymentDerivesNothingEvenWithASecret() {
		final var config = configWith("a-secret");
		config.setEnabled(false);

		assertThat(new AdminKey(config).isAvailable(), is(false));
	}

	/**
	 * The whole point. If this ever fails, the preference tables identify their users.
	 */
	@Test
	void theKeyIsNeitherTheSubjectNorContainsIt() {
		final var key = new AdminKey(configWith("a-secret")).derive(SUBJECT);

		assertThat(key, is(not(SUBJECT)));
		assertThat(key, is(not(containsString(SUBJECT))));
	}

	@Test
	void theSameSubjectAlwaysDerivesTheSameKey() {
		final var adminKey = new AdminKey(configWith("a-secret"));

		assertThat(adminKey.derive(SUBJECT), is(adminKey.derive(SUBJECT)));
	}

	/**
	 * Deployment scoping. Two consortia running DCB cannot correlate their users by
	 * comparing preference tables, and rotating the secret orphans rows rather than
	 * silently rebinding them to the same people.
	 */
	@Test
	void differentSecretsDeriveDifferentKeysForTheSameSubject() {
		final var first = new AdminKey(configWith("first-secret")).derive(SUBJECT);
		final var second = new AdminKey(configWith("second-secret")).derive(SUBJECT);

		assertThat(first, is(not(second)));
	}

	@Test
	void differentSubjectsDeriveDifferentKeys() {
		final var adminKey = new AdminKey(configWith("a-secret"));

		assertThat(adminKey.derive(SUBJECT), is(not(adminKey.derive("somebody-else"))));
	}

	/**
	 * Both identity providers issue {@code sub}, so both work, and neither the username
	 * nor anything else about the token changes the answer.
	 */
	@Test
	void onlyTheSubjectDecidesTheKey() {
		final var adminKey = new AdminKey(configWith("a-secret"));

		final var keycloakStyle = Authentication.build("alice.smith", List.of("ADMIN"),
			Map.of("sub", SUBJECT, "preferred_username", "alice.smith",
				"iss", "https://keycloak.example/realms/dcb"));

		final var zitadelStyle = Authentication.build("bob.jones", List.of("CONSORTIUM_ADMIN"),
			Map.of("sub", SUBJECT, "preferred_username", "bob.jones",
				"iss", "https://dcb.zitadel.cloud"));

		assertThat("A different username, realm and role set, same subject: same key",
			adminKey.forCaller(keycloakStyle), is(adminKey.forCaller(zitadelStyle)));
	}

	/**
	 * The specific mistake this class exists to prevent.
	 * {@code GraphQLSecurityContextCustomizer} falls back to the username when there is
	 * no subject, deliberately, for the data change log. Reusing that fallback here would
	 * make the key an HMAC of a person's name.
	 */
	@Test
	void aTokenWithNoSubjectIsRejectedRatherThanKeyedOnTheUsername() {
		final var adminKey = new AdminKey(configWith("a-secret"));

		final var noSubject = Authentication.build("alice.smith", List.of("ADMIN"),
			Map.of("preferred_username", "alice.smith"));

		final var error = assertThrows(PreferenceException.class,
			() -> adminKey.forCaller(noSubject));

		assertThat(error.status(), is(io.micronaut.http.HttpStatus.BAD_REQUEST));
		assertThat("The rejection must not leak the username it refused to use",
			error.getMessage(), is(not(containsString("alice.smith"))));
	}

	@Test
	void aNonStringSubjectIsRejected() {
		final var adminKey = new AdminKey(configWith("a-secret"));

		final var numericSubject = Authentication.build("someone", List.of("ADMIN"),
			Map.of("sub", 12345));

		assertThrows(PreferenceException.class, () -> adminKey.forCaller(numericSubject));
	}

	private static PreferencesConfig configWith(String secret) {
		final var config = new PreferencesConfig();
		config.setSecret(secret);

		return config;
	}
}


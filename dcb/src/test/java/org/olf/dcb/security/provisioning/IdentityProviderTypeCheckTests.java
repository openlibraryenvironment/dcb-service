package org.olf.dcb.security.provisioning;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micronaut.context.annotation.Context;

/**
 * A provider type nobody implements must fail the deploy, not the first account.
 *
 * Constructed directly, for the reason given in {@link IdentityProviderConfigTests}: the
 * check lives in the constructor, so the constructor is what is tested.
 */
class IdentityProviderTypeCheckTests {

	@Test
	@DisplayName("Empty turns provisioning off, and says nothing about it")
	void emptyIsTheDocumentedWayToTurnItOff() {
		for (final var type : new String[] { "", "   ", null }) {
			assertDoesNotThrow(() -> new IdentityProviderTypeCheck(type));
		}
	}

	@Test
	@DisplayName("The one implemented type is accepted")
	void keycloakIsAccepted() {
		assertDoesNotThrow(() -> new IdentityProviderTypeCheck("keycloak"));
	}

	@Test
	@DisplayName("Any other value fails startup, naming the value and the way out")
	void anythingElseFailsStartup() {
		// Keycloak with a capital K is the likelier of the two in practice, and was
		// indistinguishable from provisioning being switched off on purpose.
		for (final var type : new String[] { "Keycloak", "KEYCLOAK", "zitadel", " keycloak" }) {
			final var failure = assertThrows(IllegalStateException.class,
				() -> new IdentityProviderTypeCheck(type));

			assertThat(failure.getMessage(), containsString(type));
			assertThat(failure.getMessage(), containsString("keycloak"));
			assertThat(failure.getMessage(), containsString("operational:identity-provider-setup.adoc"));
		}
	}

	/**
	 * Lazily-scoped, both of these fail at the first account instead of at the deploy -
	 * measured, and the whole point of the check.
	 */
	@Test
	@DisplayName("Both provisioning beans are eager, so a bad setting fails the deploy")
	void theBeansAreEager() {
		assertThat(IdentityProviderTypeCheck.class.getAnnotation(Context.class), notNullValue());
		assertThat(KeycloakIdentityProviderClient.class.getAnnotation(Context.class),
			notNullValue());
	}
}

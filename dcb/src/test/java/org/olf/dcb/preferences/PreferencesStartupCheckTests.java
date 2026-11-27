package org.olf.dcb.preferences;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/** Preferences are not core: no combination of settings may stop dcb-service starting. */
class PreferencesStartupCheckTests {
	@Test
	void aDeploymentWithNoSecretStartsWithPreferencesOff() {
		final var config = config(null, null);

		assertDoesNotThrow(() -> new PreferencesStartupCheck(config));
		assertThat(config.isOn(), is(false));
	}

	@Test
	void aDeploymentThatAsksForPreferencesWithoutASecretStillStarts() {
		final var config = config(null, true);

		assertDoesNotThrow(() -> new PreferencesStartupCheck(config));
		assertThat(config.isOn(), is(false));
	}

	@Test
	void aBlankSecretIsNotASecret() {
		assertThat(config("  ", null).isOn(), is(false));
	}

	@Test
	void aSecretTurnsPreferencesOn() {
		final var config = config("a-secret", null);

		assertDoesNotThrow(() -> new PreferencesStartupCheck(config));
		assertThat(config.isOn(), is(true));
	}

	@Test
	void aDeploymentThatDeclinesPreferencesHasThemOffWhateverTheSecret() {
		final var config = config("a-secret", false);

		assertDoesNotThrow(() -> new PreferencesStartupCheck(config));
		assertThat(config.isOn(), is(false));
	}

	private static PreferencesConfig config(String secret, Boolean enabled) {
		final var config = new PreferencesConfig();
		config.setSecret(secret);
		config.setEnabled(enabled);
		return config;
	}
}

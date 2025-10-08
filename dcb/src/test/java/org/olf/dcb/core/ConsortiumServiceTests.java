package org.olf.dcb.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.FunctionalSettingType.OWN_LIBRARY_BORROWING;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
class ConsortiumServiceTests {
	@Inject
	private ConsortiumService consortiumService;

	@Inject
	private ConsortiumFixture consortiumFixture;

	@BeforeEach
	public void beforeEach() {
		consortiumFixture.deleteAll();
	}

	@Test
	void settingShouldBeEnabledWhenPresentAndEnabled() {
		// Arrange
		consortiumFixture.enableSetting(OWN_LIBRARY_BORROWING);

		// Act
		final var enabled = settingIsEnabled(OWN_LIBRARY_BORROWING);

		// Assert
		assertThat(enabled, is(true));
	}

	@Test
	void settingShouldBeDisabledWhenPresentAndDisabled() {
		// Arrange
		final var consortium = consortiumFixture.createConsortium();

		consortiumFixture.createSetting(consortium, OWN_LIBRARY_BORROWING, false);

		// Act
		final var enabled = settingIsEnabled(OWN_LIBRARY_BORROWING);

		// Assert
		assertThat(enabled, is(false));
	}

	@Test
	void settingShouldBeDisabledWhenNotPresent() {
		// Arrange
		consortiumFixture.createConsortium();

		// Act
		final var enabled = settingIsEnabled(OWN_LIBRARY_BORROWING);

		// Assert
		assertThat(enabled, is(false));
	}

	@Test
	void settingShouldBeDisabledWhenEnabledIsNull() {
		// Arrange
		final var consortium = consortiumFixture.createConsortium();

		consortiumFixture.createSetting(consortium, OWN_LIBRARY_BORROWING, null);

		// Act
		final var enabled = settingIsEnabled(OWN_LIBRARY_BORROWING);

		// Assert
		assertThat(enabled, is(false));
	}

	@Test
	void settingShouldBeDisabledWhenNoConsortiaIsDefined() {
		// Act
		final var enabled = settingIsEnabled(OWN_LIBRARY_BORROWING);

		// Assert
		assertThat(enabled, is(false));
	}

	private Boolean settingIsEnabled(FunctionalSettingType settingType) {
		return singleValueFrom(consortiumService.isEnabled(settingType));
	}
}

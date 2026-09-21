package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.model.HostLms;

class AlmaClientConfigTests {
	@Test
	void shouldListEverySettingTheAlmaClientReads() {
		final var names = new AlmaClientConfig(mock(HostLms.class)).getSettings().stream()
			.map(HostLmsPropertyDefinition::getName)
			.toList();

		assertThat(names, containsInAnyOrder(
			"alma-url",
			"apikey",
			"sharing-library-code",
			"virtual-item-library-code",
			"virtual-item-location-code",
			"item-policy",
			"no-renew-item-policy",
			"pickup-circ-desk",
			"default-circ-desk-code",
			"user-identifier",
			"request-cancellation-reason"));
	}
}

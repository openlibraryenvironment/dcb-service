package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.koha.KohaClientConfig;
import org.olf.dcb.core.model.HostLms;

class VirtualPatronEmailTests {
	@Test
	void shouldPassOnAnAddress() {
		assertThat(VirtualPatronEmail.addressOrNull("ill-notices@consortium.example.org"),
			is("ill-notices@consortium.example.org"));
	}

	@Test
	void shouldNotPassOnAValueThatIsNotAnAddress() {
		assertThat(VirtualPatronEmail.addressOrNull("the ILL desk"), is(nullValue()));
		assertThat(VirtualPatronEmail.addressOrNull(42), is(nullValue()));
		assertThat(VirtualPatronEmail.addressOrNull(null), is(nullValue()));
	}

	// Saved through REST, which skips HostLmsConfigValidator
	@Test
	void shouldNotGiveKohaAMalformedMailbox() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getClientConfig()).thenReturn(Map.of("virtual-patron-email", "the ILL desk"));

		assertThat(new KohaClientConfig(hostLms).getVirtualPatronEmail(), is(nullValue()));
	}
}

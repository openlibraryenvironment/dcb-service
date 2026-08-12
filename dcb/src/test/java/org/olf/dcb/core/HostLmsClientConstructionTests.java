package org.olf.dcb.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;

/**
 * Every adapter must be constructible through the container from its stored
 * configuration, because that is the only way DCB ever builds one.
 * <p>
 * Adapters whose only test coverage constructs them directly with mocks can carry
 * unsatisfiable injection points indefinitely without anything noticing - the unit
 * tests pass and the adapter is simply dead at runtime.
 */
@DcbTest
class HostLmsClientConstructionTests {
	@Inject
	HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldConstructAKohaClient() {
		hostLmsFixture.createKohaHostLms("KOHA-CONSTRUCTION", "https://koha.example.com");

		final var client = hostLmsFixture.createClient("KOHA-CONSTRUCTION");

		assertThat(client, is(notNullValue()));
		assertThat(client.getHostLmsCode(), is("KOHA-CONSTRUCTION"));
		assertThat(client.getClientId(), is("https://koha.example.com/"));
	}

	@Test
	void shouldConstructAnAlmaClient() {
		hostLmsFixture.createAlmaHostLms("ALMA-CONSTRUCTION", "https://alma.example.com");

		final var client = hostLmsFixture.createClient("ALMA-CONSTRUCTION");

		assertThat(client, is(notNullValue()));
		assertThat(client.getHostLmsCode(), is("ALMA-CONSTRUCTION"));
		assertThat(client.getClientId(), is("https://alma.example.com/"));
	}

	@Test
	void shouldConstructASierraClient() {
		hostLmsFixture.createSierraHostLms("SIERRA-CONSTRUCTION", "key", "secret",
			"https://sierra.example.com", "item");

		final var client = hostLmsFixture.createClient("SIERRA-CONSTRUCTION");

		assertThat(client, is(notNullValue()));
		assertThat(client.getHostLmsCode(), is("SIERRA-CONSTRUCTION"));
	}

	@Test
	void shouldConstructADummyClient() {
		hostLmsFixture.createDummyHostLms("DUMMY-CONSTRUCTION");

		final HostLmsClient client = hostLmsFixture.createClient("DUMMY-CONSTRUCTION");

		assertThat(client, is(notNullValue()));
		assertThat(client.getClientId(), is("DUMMY-CONSTRUCTION"));
	}
}

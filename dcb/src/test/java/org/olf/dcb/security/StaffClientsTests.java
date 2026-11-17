package org.olf.dcb.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.security.authentication.Authentication;

class StaffClientsTests {
	@Test
	void shouldRefuseToStartWithNoStaffClient() {
		for (final var none : List.of(List.<String>of(), List.of(""), List.of("  ", ""))) {
			final var failure = assertThrows(IllegalStateException.class, () -> new StaffClients(none));

			assertThat(failure.getMessage(), containsString("duplicate-requests.enabled=false"));
		}

		assertThat(StaffClients.class.getAnnotation(io.micronaut.context.annotation.Context.class), notNullValue());
	}

	@Test
	void shouldRecogniseAStaffClientNamedWithSpaces() {
		final var staffClients = new StaffClients(List.of(" dcb-admin "));

		assertThat(staffClients.issuedTo(
			Authentication.build("caller", List.of(ADMINISTRATOR), Map.of("azp", "dcb-admin"))), is(true));
		assertThat(staffClients.issuedTo(
			Authentication.build("caller", List.of(ADMINISTRATOR), Map.of("azp", "ebsco-locate"))), is(false));
	}
}

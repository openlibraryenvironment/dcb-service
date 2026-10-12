package org.olf.dcb.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Inject;

@Property(name = "dcb.requests.staff-client-ids", value = "")
@DcbTest
class StaffClientsSetEmptyTests {
	@Inject
	private StaffClients staffClients;

	@Test
	void shouldRecogniseNoStaffClientWhenSetEmpty() {
		assertThat(staffClients.isConfigured(), is(false));
		assertThat(staffClients.issuedTo(
			Authentication.build("caller", List.of(ADMINISTRATOR), Map.of("azp", "dcb-admin"))), is(false));
	}
}

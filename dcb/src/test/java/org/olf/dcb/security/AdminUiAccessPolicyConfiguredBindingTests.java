package org.olf.dcb.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

@Property(name = "dcb.security.admin-ui.client-id", value = "dcb-admin")
@Property(name = "dcb.security.admin-ui.mode", value = "enforce")
@DcbTest
class AdminUiAccessPolicyConfiguredBindingTests {
	@Inject
	private AdminUiAccessPolicy policy;

	@Test
	void shouldBindAClientIdThatIsSet() {
		assertThat(policy.isConfigured(), is(true));
		assertThat(policy.isEnforcing(), is(true));
	}
}

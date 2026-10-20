package org.olf.dcb.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

// application.yml no longer carries the key, so this is the shipped default
@DcbTest
class AdminUiAccessPolicyBindingTests {
	@Inject
	private AdminUiAccessPolicy policy;

	@Test
	void shouldBeOffWhenTheClientIdIsAbsent() {
		assertThat(policy.isConfigured(), is(false));
		assertThat(policy.isEnforcing(), is(false));
	}
}

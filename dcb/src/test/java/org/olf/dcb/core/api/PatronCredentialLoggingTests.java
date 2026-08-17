package org.olf.dcb.core.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

class PatronCredentialLoggingTests {
	private static final String SECRET = "must-not-appear";

	@Test
	void v2CredentialsDoNotIncludeTheSecretInToString() {
		final var credentials = PatronAuthV2Controller.V2PatronCredentials.builder()
			.principal("agency/patron")
			.credentials(SECRET)
			.build();

		assertThat(credentials.toString(), not(containsString(SECRET)));
	}

	@Test
	void legacyCredentialsDoNotIncludeTheSecretInToString() {
		final var credentials = PatronAuthController.PatronCredentials.builder()
			.agencyCode("agency")
			.patronPrinciple("patron")
			.secret(SECRET)
			.build();

		assertThat(credentials.toString(), not(containsString(SECRET)));
	}
}

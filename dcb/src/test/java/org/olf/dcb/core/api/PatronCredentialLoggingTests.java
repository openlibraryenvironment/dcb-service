package org.olf.dcb.core.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

class PatronCredentialLoggingTests {
	private static final String SECRET = "must-not-appear";
	private static final String BARCODE = "BAR-MUST-NOT-APPEAR";
	private static final String ASSUMED = "ASSUMED-MUST-NOT-APPEAR";

	@Test
	void v2CredentialsDoNotIncludeTheSecretOrTheIdentitiesInToString() {
		final var credentials = PatronAuthV2Controller.V2PatronCredentials.builder()
			.principal("agency/" + BARCODE)
			.credentials(SECRET)
			.as(ASSUMED)
			.build();

		assertThat(credentials.toString(), allOf(
			not(containsString(SECRET)),
			not(containsString(BARCODE)),
			not(containsString(ASSUMED))));
	}

	@Test
	void legacyCredentialsDoNotIncludeTheSecretOrTheBarcodeInToString() {
		final var credentials = PatronAuthController.PatronCredentials.builder()
			.agencyCode("agency")
			.patronPrinciple(BARCODE)
			.secret(SECRET)
			.build();

		assertThat(credentials.toString(), allOf(
			not(containsString(SECRET)),
			not(containsString(BARCODE)),
			containsString("agency")));
	}
}

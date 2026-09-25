package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No Polaris credential-bearing type may print its own secret.
 *
 * All four are Lombok {@code @Data}, so toString() prints every field unless it is
 * excluded, and two log statements printed a whole token — the credentials reached a
 * session log on 2026-09-09. Deleting those statements fixed two callers; excluding the
 * fields fixes the classes, so the next caller cannot reintroduce it.
 *
 * Written before the exclusions and run against them: it caught that the first attempt
 * had annotated PatronAuthToken and left AuthToken, which is the one that leaked.
 *
 * StaffCredentials carries the staff password and is excluded too, but is not asserted
 * here: it is a private nested class, and widening it so a test can see it would be a
 * worse trade than the coverage is worth.
 */
class PolarisAuthTokenRedactionTests {

	private static final String TOKEN = "tok-must-not-appear";
	private static final String SECRET = "sec-must-not-appear";

	@Test
	@DisplayName("PAPI staff auth token redacts its access token and secret")
	void staffAuthTokenRedactsItsCredentials() {
		final var rendered = PAPIAuthFilter.AuthToken.builder()
			.papiErrorCode(0)
			.accessToken(TOKEN)
			.accessSecret(SECRET)
			.build()
			.toString();

		assertThat(rendered, redacts());

		// The field that diagnoses a bad staff auth must survive, or the exclusion has
		// been applied by deleting the diagnostic rather than the credential.
		assertThat(rendered, containsString("papiErrorCode"));
	}

	@Test
	@DisplayName("PAPI patron auth token redacts its access token and secret")
	void patronAuthTokenRedactsItsCredentials() {
		final var rendered = PAPIAuthFilter.PatronAuthToken.builder()
			.papiErrorCode(0)
			.accessToken(TOKEN)
			.accessSecret(SECRET)
			.build()
			.toString();

		assertThat(rendered, redacts());
	}

	@Test
	@DisplayName("Application Services auth token redacts its access token and secret")
	void applicationServicesAuthTokenRedactsItsCredentials() {
		final var rendered = ApplicationServicesAuthFilter.AuthToken.builder()
			.userDomain("example-domain")
			.accessToken(TOKEN)
			.accessSecret(SECRET)
			.build()
			.toString();

		assertThat(rendered, redacts());
		assertThat(rendered, containsString("example-domain"));
	}

	private static org.hamcrest.Matcher<String> redacts() {
		return allOf(not(containsString(TOKEN)), not(containsString(SECRET)));
	}
}

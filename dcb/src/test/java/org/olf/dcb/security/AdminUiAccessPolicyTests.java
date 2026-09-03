package org.olf.dcb.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The whole decision, table-driven, with no application context to start.
 *
 * Every row here is a way the rule could be wrong in production, and three of them are ways
 * it could be wrong in the direction that locks real people out of a working system.
 */
class AdminUiAccessPolicyTests {

	private static final String ADMIN_UI = "dcb-admin";
	private static final String DAFL = "dcb-admin-for-libraries";

	private static AdminUiAccessPolicy enforcing() {
		return new AdminUiAccessPolicy(ADMIN_UI, "ENFORCE");
	}

	private static AdminUiAccessPolicy warning() {
		return new AdminUiAccessPolicy(ADMIN_UI, "WARN");
	}

	private static Map<String, Object> from(String client) {
		return client == null ? Map.of() : Map.of("azp", client);
	}

	@Nested
	@DisplayName("Enforcing")
	class Enforcing {

		@Test
		@DisplayName("A library role driving DCB Admin's client is refused")
		void libraryRoleOnAdminClientIsRefused() {
			final var refusal = enforcing()
				.enforce("someone", List.of("LIBRARY_ADMIN"), from(ADMIN_UI));

			assertThat(refusal.isPresent(), is(true));
		}

		@Test
		@DisplayName("A read-only library role is refused for the same reason")
		void readOnlyLibraryRoleOnAdminClientIsRefused() {
			assertThat(enforcing().enforce("someone", List.of("LIBRARY_READ_ONLY"), from(ADMIN_UI))
				.isPresent(), is(true));
		}

		@Test
		@DisplayName("A consortium role driving DCB Admin's client is permitted")
		void consortiumRoleOnAdminClientIsPermitted() {
			assertThat(enforcing().enforce("someone", List.of("CONSORTIUM_ADMIN"), from(ADMIN_UI))
				.isEmpty(), is(true));

			assertThat(enforcing().enforce("someone", List.of("ADMIN"), from(ADMIN_UI))
				.isEmpty(), is(true));
		}

		@Test
		@DisplayName("Holding a library role as WELL as a consortium one is permitted")
		void aConsortiumAdminWhoIsAlsoALibraryAdminIsPermitted() {
			// Consortium staff are people at libraries. Reading this as "has a library
			// role, therefore barred" would lock out exactly the administrators the rule
			// exists to admit.
			assertThat(enforcing()
				.enforce("someone", List.of("LIBRARY_ADMIN", "CONSORTIUM_ADMIN"), from(ADMIN_UI))
				.isEmpty(), is(true));
		}

		@Test
		@DisplayName("A library role driving DAFL's client is untouched")
		void libraryRoleOnItsOwnClientIsPermitted() {
			// This rule is about one application, not about roles in general. DAFL users
			// hold LIBRARY_ADMIN by design and must keep working.
			assertThat(enforcing().enforce("someone", List.of("LIBRARY_ADMIN"), from(DAFL))
				.isEmpty(), is(true));
		}

		@Test
		@DisplayName("A token with no azp at all is permitted")
		void aTokenWithoutAnAuthorizedPartyIsPermitted() {
			// Legacy tokens and machine tokens must not be broken by a rule about a
			// browser application, and an absent claim is not evidence of DCB Admin.
			assertThat(enforcing().enforce("someone", List.of("LIBRARY_ADMIN"), from(null))
				.isEmpty(), is(true));

			assertThat(enforcing().enforce("someone", List.of("LIBRARY_ADMIN"), null)
				.isEmpty(), is(true));
		}

		@Test
		@DisplayName("A blank azp is treated as absent, not as a client named \"\"")
		void aBlankAuthorizedPartyIsAbsent() {
			assertThat(enforcing()
				.enforce("someone", List.of("LIBRARY_ADMIN"), Map.of("azp", "  "))
				.isEmpty(), is(true));
		}

		@Test
		@DisplayName("INTERNAL_API counts as consortium-wide")
		void internalApiIsNotBarred() {
			// CallerScope already treats it as unrestricted; barring it here would break
			// service-to-service calls for a reason that has nothing to do with them.
			assertThat(enforcing().enforce("service", List.of("INTERNAL_API"), from(ADMIN_UI))
				.isEmpty(), is(true));
		}

		@Test
		@DisplayName("A token with no roles at all, on the admin client, is refused")
		void noRolesIsRefused() {
			assertThat(enforcing().enforce("someone", null, from(ADMIN_UI)).isPresent(), is(true));
			assertThat(enforcing().enforce("someone", List.of(), from(ADMIN_UI)).isPresent(), is(true));
		}
	}

	@Nested
	@DisplayName("Warning")
	class Warning {

		@Test
		@DisplayName("Refuses nothing, so a rollout can be observed before it bites")
		void warnRefusesNothing() {
			assertThat(warning().enforce("someone", List.of("LIBRARY_ADMIN"), from(ADMIN_UI))
				.isEmpty(), is(true));
		}

		@Test
		@DisplayName("But still knows what it would have refused")
		void warnStillComputesTheVerdict() {
			// The distinction matters: if WARN also stopped computing the verdict there
			// would be nothing to drain from the log, and the phase would prove nothing.
			assertThat(warning().refusalReason(List.of("LIBRARY_ADMIN"), from(ADMIN_UI))
				.isPresent(), is(true));
		}
	}

	@Nested
	@DisplayName("Unconfigured")
	class Unconfigured {

		@Test
		@DisplayName("An empty client id disables the check entirely")
		void emptyClientIdPermitsEverything() {
			final var off = new AdminUiAccessPolicy("", "ENFORCE");

			assertThat(off.isConfigured(), is(false));
			assertThat(off.enforce("someone", List.of("LIBRARY_ADMIN"), from(ADMIN_UI))
				.isEmpty(), is(true));
		}

		@Test
		@DisplayName("So does a null one")
		void nullClientIdPermitsEverything() {
			final var off = new AdminUiAccessPolicy(null, "ENFORCE");

			assertThat(off.isConfigured(), is(false));
			assertThat(off.enforce("someone", List.of("LIBRARY_ADMIN"), from(ADMIN_UI))
				.isEmpty(), is(true));
		}

		@Test
		@DisplayName("A blank-but-present id is unconfigured, not a client named \"  \"")
		void blankClientIdIsUnconfigured() {
			assertThat(new AdminUiAccessPolicy("   ", "ENFORCE").isConfigured(), is(false));
		}
	}

	@Nested
	@DisplayName("The matrix, as measured against a running Keycloak")
	class Matrix {

		/**
		 * Every row here was observed end to end with the bar in ENFORCE and the two OIDC
		 * clients split, so this test is a record of real behaviour rather than a restatement
		 * of the implementation. The two "allowed" rows are the ones that catch the rule being
		 * over-tightened, which is the failure that locks real people out of a working system.
		 */
		@ParameterizedTest(name = "{0} on {1} -> {2}")
		@CsvSource({
			"CONSORTIUM_ADMIN,      dcb-admin,               allowed",
			"ADMIN,                 dcb-admin,               allowed",
			"LIBRARY_ADMIN,         dcb-admin,               refused",
			"LIBRARY_READ_ONLY,     dcb-admin,               refused",
			"LIBRARY_ADMIN,         dcb-admin-for-libraries, allowed",
			"LIBRARY_READ_ONLY,     dcb-admin-for-libraries, allowed",
			"CONSORTIUM_ADMIN,      dcb-admin-for-libraries, allowed",
		})
		void theMeasuredMatrix(String role, String client, String expected) {
			final var refusal = enforcing()
				.enforce("someone", List.of(role.trim()), from(client.trim()));

			assertThat(refusal.isEmpty() ? "allowed" : "refused", is(expected.trim()));
		}

		@Test
		@DisplayName("Holding a library role AND a consortium one is allowed on DCB Admin")
		void dualRoleIsAllowed() {
			// Consortium staff are people at libraries, and their tokens carry both. This is
			// the row that fails if somebody rewrites the rule as "has a library role".
			assertThat(enforcing()
				.enforce("someone", List.of("LIBRARY_ADMIN", "CONSORTIUM_ADMIN"), from(ADMIN_UI))
				.isEmpty(), is(true));
		}
	}

	@Test
	@DisplayName("The mode defaults to WARN and is read case-insensitively")
	void modeParsing() {
		assertThat(new AdminUiAccessPolicy(ADMIN_UI, "warn").isEnforcing(), is(false));
		assertThat(new AdminUiAccessPolicy(ADMIN_UI, "enforce").isEnforcing(), is(true));
		assertThat(new AdminUiAccessPolicy(ADMIN_UI, " ENFORCE ").isEnforcing(), is(true));
	}
}

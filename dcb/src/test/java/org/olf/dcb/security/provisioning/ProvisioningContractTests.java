package org.olf.dcb.security.provisioning;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import graphql.schema.idl.SchemaParser;

/**
 * The contract that keeps account provisioning from minting authority nobody asked for.
 *
 * Each assertion here corresponds to one of the four independent gates on the role, and the
 * point of having four is that they fail independently. A test that only exercised the
 * outermost one would pass while the others rotted.
 */
class ProvisioningContractTests {

	@Nested
	@DisplayName("The role vocabulary")
	class Roles {

		@Test
		@DisplayName("Contains exactly the two library roles - and no administrator")
		void onlyTwoRolesAreProvisionable() {
			assertThat(Arrays.asList(ProvisionableRole.values()), containsInAnyOrder(
				ProvisionableRole.LIBRARY_ADMIN, ProvisionableRole.LIBRARY_READ_ONLY));
		}

		@Test
		@DisplayName("Refuses ADMIN and CONSORTIUM_ADMIN outright")
		void refusesEscalation() {
			assertThat(ProvisionableRole.parse("ADMIN").isEmpty(), is(true));
			assertThat(ProvisionableRole.parse("CONSORTIUM_ADMIN").isEmpty(), is(true));
			assertThat(ProvisionableRole.parse("INTERNAL_API").isEmpty(), is(true));
		}

		@Test
		@DisplayName("Refuses anything it was not offered, rather than repairing it")
		void refusesRatherThanRepairs() {
			// No hyphen or space translation: the values come from a GraphQL enum the
			// client cannot invent, so a value needing repair is a client sending
			// something it was never given.
			assertThat(ProvisionableRole.parse("library-admin").isEmpty(), is(true));
			assertThat(ProvisionableRole.parse("library admin").isEmpty(), is(true));
			assertThat(ProvisionableRole.parse("").isEmpty(), is(true));
			assertThat(ProvisionableRole.parse(null).isEmpty(), is(true));
		}

		@Test
		@DisplayName("Accepts the two it does offer")
		void acceptsWhatItOffers() {
			assertThat(ProvisionableRole.parse("LIBRARY_ADMIN").orElseThrow(),
				is(ProvisionableRole.LIBRARY_ADMIN));

			assertThat(ProvisionableRole.parse(" library_read_only ").orElseThrow(),
				is(ProvisionableRole.LIBRARY_READ_ONLY));
		}
	}

	@Nested
	@DisplayName("Provider state maps to a status")
	class Status {

		private static IdentityProviderClient.ProvisionedUser user(boolean enabled, boolean verified) {
			return new IdentityProviderClient.ProvisionedUser("id", "a@b.c", "A", "B",
				enabled, verified);
		}

		@Test
		@DisplayName("Disabled wins over everything else")
		void disabledWins() {
			assertThat(user(false, true).status(), is(LibraryUserStatus.DISABLED));
			assertThat(user(false, false).status(), is(LibraryUserStatus.DISABLED));
		}

		@Test
		@DisplayName("Enabled but unverified is still only invited")
		void enabledButUnverifiedIsInvited() {
			assertThat(user(true, false).status(), is(LibraryUserStatus.INVITED));
		}

		@Test
		@DisplayName("Enabled and verified is active")
		void enabledAndVerifiedIsActive() {
			assertThat(user(true, true).status(), is(LibraryUserStatus.ACTIVE));
		}
	}

	@Nested
	@DisplayName("The schema")
	class Schema {

		@Test
		@DisplayName("Parses, so a malformed addition cannot reach startup")
		void schemaParses() throws IOException {
			// The schema is only otherwise validated when the application boots, which
			// means a typo in it is a runtime failure in an environment rather than a
			// build failure on a branch.
			new SchemaParser().parse(Files.readString(resource("schema.graphqls")));
		}

		@Test
		@DisplayName("Offers exactly the two provisionable roles, matching the Java enum")
		void schemaEnumMatchesTheJavaEnum() throws IOException {
			final var schema = Files.readString(resource("schema.graphqls"));
			final var block = between(schema, "enum ProvisionableRole {", "}");

			// Read as VALUES, not searched as substrings. "LIBRARY_ADMIN" ends in "ADMIN",
			// so a substring test for the forbidden role either matches a permitted one or
			// leans on whatever character follows it - and `contains("ADMIN\n")` leant on the
			// newline, which made the whole assertion depend on the checkout's line endings.
			// It passed on CRLF and failed on CI. \R matches any of them.
			final var declared = Arrays.stream(block.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.collect(Collectors.toSet());

			final var expected = Arrays.stream(ProvisionableRole.values())
				.map(Enum::name)
				.collect(Collectors.toSet());

			// Equality rather than containment, which is what the name of this test claims:
			// a role the schema offers and the Java side would refuse is as much a defect as
			// a missing one, and ADMIN is the one that matters.
			assertThat("the schema and the Java enum must offer the same vocabulary",
				declared, is(expected));
		}

		@Test
		@DisplayName("Declares no password field anywhere on the account type")
		void noPasswordOnTheAccountType() throws IOException {
			final var block = between(Files.readString(resource("schema.graphqls")),
				"type LibraryUser {", "}");

			assertThat(block.toLowerCase().contains("password"), is(false));
			assertThat(block.toLowerCase().contains("secret"), is(false));
		}

		@Test
		@DisplayName("Takes no agencyCode on the provisioning input")
		void provisioningInputCannotNameAnAgency() throws IOException {
			// The agency is derived server-side from the library. An input field for it
			// would be exactly the client-supplied scope this estate refuses everywhere
			// else.
			final var block = between(Files.readString(resource("schema.graphqls")),
				"input ProvisionLibraryUserInput {", "}");

			assertThat(block.contains("agencyCode"), is(false));
		}

		@Test
		@DisplayName("Every new field is wired to a fetcher")
		void everyFieldIsWired() throws IOException {
			// graphql-java resolves an unwired field to a property getter and returns null
			// rather than failing, so an unwired mutation is a silent no-op.
			final var factory = Files.readString(sourceFile(
				"org/olf/dcb/graphql/GraphQLFactory.java"));

			for (final var field : new String[] { "libraryUsers", "libraryUserProvisioningAvailable",
				"provisionLibraryUser", "setLibraryUserEnabled", "resendLibraryUserInvite" }) {

				assertThat(field + " is declared in the schema but not wired in GraphQLFactory",
					factory.contains("\"" + field + "\""), is(true));
			}
		}
	}

	private static String between(String source, String start, String end) {
		final var from = source.indexOf(start);

		assertThat("could not find '" + start + "' in the schema", from >= 0, is(true));

		final var to = source.indexOf(end, from + start.length());

		return source.substring(from + start.length(), to);
	}

	private static Path resource(String name) {
		final var fromModuleRoot = Paths.get("src/main/resources").resolve(name);

		return Files.exists(fromModuleRoot)
			? fromModuleRoot
			: Paths.get("dcb/src/main/resources").resolve(name);
	}

	private static Path sourceFile(String name) {
		final var fromModuleRoot = Paths.get("src/main/java").resolve(name);

		return Files.exists(fromModuleRoot)
			? fromModuleRoot
			: Paths.get("dcb/src/main/java").resolve(name);
	}
}

package org.olf.dcb.graphql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The authorisation decision every top-level data fetcher makes.
 *
 * No database and no Micronaut context: {@code DataFetchers} takes thirty-one repositories,
 * which is precisely why the check that used to live inside it had no test.
 */
class GraphQLRolesTests {

	@Test
	@DisplayName("a token with no roles claim is refused")
	void aTokenWithNoRolesClaimIsRefused() {
		// Fail closed. "This token does not say who you are" is not "you are permitted",
		// and a missing claim is the shape an IdP misconfiguration takes.
		assertFalse(GraphQLRoles.holdsAny(null, GraphQLRoles.STAFF));
		assertFalse(GraphQLRoles.holdsAny(null, GraphQLRoles.ADMINISTRATIVE));
		assertFalse(GraphQLRoles.holdsAny(null, GraphQLRoles.CONSORTIUM));
	}

	@Test
	@DisplayName("a token with an empty roles claim is refused")
	void aTokenWithAnEmptyRolesClaimIsRefused() {
		assertFalse(GraphQLRoles.holdsAny(List.of(), GraphQLRoles.STAFF));
	}

	@Test
	@DisplayName("a discovery service credential cannot reach the admin API")
	void aDiscoveryServiceCredentialCannotReachTheAdminApi() {
		// The case the read floor exists for. /graphql requires isAuthenticated() and
		// nothing more, and DISCOVERY_SERVICE is held by discovery backends that may be
		// third party. What they legitimately need is on the anonymous /discovery routes.
		assertFalse(GraphQLRoles.holdsAny(List.of("DISCOVERY_SERVICE"), GraphQLRoles.STAFF));
	}

	@Test
	@DisplayName("machine credentials are not staff")
	void machineCredentialsAreNotStaff() {
		// Both are granted where they belong, by explicit @Secured on REST controllers.
		// Neither drives an administration UI.
		assertFalse(GraphQLRoles.holdsAny(List.of("INTERNAL_API"), GraphQLRoles.STAFF));
		assertFalse(GraphQLRoles.holdsAny(List.of("INTEROP_TESTER"), GraphQLRoles.STAFF));
	}

	@Test
	@DisplayName("read-only staff may read, and may not configure the consortium")
	void readOnlyStaffMayReadAndMayNotConfigure() {
		// The whole reason STAFF and ADMINISTRATIVE are different sets. Before this,
		// LIBRARY_READ_ONLY was checked NOWHERE in the GraphQL layer, which cut both ways:
		// it was never granted a read, and it was never denied a write.
		assertTrue(GraphQLRoles.holdsAny(List.of("LIBRARY_READ_ONLY"), GraphQLRoles.STAFF));
		assertFalse(GraphQLRoles.holdsAny(List.of("LIBRARY_READ_ONLY"), GraphQLRoles.ADMINISTRATIVE));
		assertFalse(GraphQLRoles.holdsAny(List.of("LIBRARY_READ_ONLY"), GraphQLRoles.CONSORTIUM));
	}

	@Test
	@DisplayName("a library administrator does not configure the consortium")
	void aLibraryAdministratorDoesNotConfigureTheConsortium() {
		// Group membership decides which libraries and agencies participate with which.
		// A library administrator runs their own library.
		assertTrue(GraphQLRoles.holdsAny(List.of("LIBRARY_ADMIN"), GraphQLRoles.STAFF));
		assertTrue(GraphQLRoles.holdsAny(List.of("LIBRARY_ADMIN"), GraphQLRoles.ADMINISTRATIVE));
		assertFalse(GraphQLRoles.holdsAny(List.of("LIBRARY_ADMIN"), GraphQLRoles.CONSORTIUM));
	}

	@Test
	@DisplayName("each administrative role is admitted on its own")
	void eachAdministrativeRoleIsAdmittedOnItsOwn() {
		assertTrue(GraphQLRoles.holdsAny(List.of("ADMIN"), GraphQLRoles.ADMINISTRATIVE));
		assertTrue(GraphQLRoles.holdsAny(List.of("CONSORTIUM_ADMIN"), GraphQLRoles.ADMINISTRATIVE));
		assertTrue(GraphQLRoles.holdsAny(List.of("LIBRARY_ADMIN"), GraphQLRoles.ADMINISTRATIVE));
	}

	@Test
	@DisplayName("one permitted role among several unrecognised ones is enough")
	void onePermittedRoleAmongSeveralUnrecognisedOnesIsEnough() {
		assertTrue(GraphQLRoles.holdsAny(
			List.of("offline_access", "uma_authorization", "LIBRARY_ADMIN"),
			GraphQLRoles.ADMINISTRATIVE));
	}

	@Test
	@DisplayName("role names are matched exactly, not case-insensitively or by prefix")
	void roleNamesAreMatchedExactly() {
		// The claim is a value the realm controls and we compare it as one. Accepting
		// "admin" would mean a realm that lower-cased its role names silently granted
		// administration, and matching by prefix would grant it to a role invented later
		// that was never meant to have it.
		assertFalse(GraphQLRoles.holdsAny(List.of("admin"), GraphQLRoles.STAFF));
		assertFalse(GraphQLRoles.holdsAny(List.of("ADMINISTRATOR"), GraphQLRoles.STAFF));
		assertFalse(GraphQLRoles.holdsAny(List.of("LIBRARY"), GraphQLRoles.STAFF));
	}

	@Test
	@DisplayName("the sets nest, so no set is stricter than one it should contain")
	void theSetsNest() {
		// CONSORTIUM is the narrowest and STAFF the widest. Asserted rather
		// than assumed, because a role added to the wrong constant is a silent widening
		// that no other test here would catch.
		assertTrue(GraphQLRoles.STAFF.containsAll(GraphQLRoles.ADMINISTRATIVE));
		assertTrue(GraphQLRoles.ADMINISTRATIVE.containsAll(GraphQLRoles.CONSORTIUM));
		assertFalse(GraphQLRoles.CONSORTIUM.containsAll(GraphQLRoles.ADMINISTRATIVE));
	}

	@Test
	@DisplayName("a narrower permitted set does not admit the wider roles")
	void aNarrowerPermittedSetDoesNotAdmitTheWiderRoles() {
		// require() takes the permitted set as an argument so a fetcher can be stricter
		// than any of the constants. Proving the parameter is honoured keeps that usable.
		assertFalse(GraphQLRoles.holdsAny(List.of("LIBRARY_ADMIN"), Set.of("ADMIN")));
		assertTrue(GraphQLRoles.holdsAny(List.of("ADMIN"), Set.of("ADMIN")));
	}
}

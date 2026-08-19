package org.olf.dcb.graphql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The authorisation decision every administrative data fetcher makes.
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
		assertFalse(GraphQLRoles.holdsAny(null, GraphQLRoles.ADMINISTRATIVE));
	}

	@Test
	@DisplayName("a token with an empty roles claim is refused")
	void aTokenWithAnEmptyRolesClaimIsRefused() {
		assertFalse(GraphQLRoles.holdsAny(List.of(), GraphQLRoles.ADMINISTRATIVE));
	}

	@Test
	@DisplayName("a discovery service credential cannot read administrative data")
	void aDiscoveryServiceCredentialCannotReadAdministrativeData() {
		// The case this check exists for. /graphql requires isAuthenticated() and nothing
		// more, and DISCOVERY_SERVICE is held by discovery backends that may be third
		// party. The brand they legitimately need is on /discovery/consortium.
		assertFalse(GraphQLRoles.holdsAny(List.of("DISCOVERY_SERVICE"), GraphQLRoles.ADMINISTRATIVE));
	}

	@Test
	@DisplayName("an internal API credential is not an administrator either")
	void anInternalApiCredentialIsNotAnAdministratorEither() {
		assertFalse(GraphQLRoles.holdsAny(List.of("INTERNAL_API"), GraphQLRoles.ADMINISTRATIVE));
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
		// administration, and accepting "ADMIN_READONLY" by prefix would grant it to a
		// role invented later that was never meant to have it.
		assertFalse(GraphQLRoles.holdsAny(List.of("admin"), GraphQLRoles.ADMINISTRATIVE));
		assertFalse(GraphQLRoles.holdsAny(List.of("ADMINISTRATOR"), GraphQLRoles.ADMINISTRATIVE));
		assertFalse(GraphQLRoles.holdsAny(List.of("LIBRARY_READ_ONLY"), GraphQLRoles.ADMINISTRATIVE));
	}

	@Test
	@DisplayName("a narrower permitted set does not admit the wider administrative roles")
	void aNarrowerPermittedSetDoesNotAdmitTheWiderAdministrativeRoles() {
		// require() takes the permitted set as an argument so a fetcher can be stricter
		// than ADMINISTRATIVE. Proving the parameter is honoured keeps that usable.
		assertFalse(GraphQLRoles.holdsAny(List.of("LIBRARY_ADMIN"), Set.of("ADMIN", "CONSORTIUM_ADMIN")));
		assertTrue(GraphQLRoles.holdsAny(List.of("CONSORTIUM_ADMIN"), Set.of("ADMIN", "CONSORTIUM_ADMIN")));
	}
}

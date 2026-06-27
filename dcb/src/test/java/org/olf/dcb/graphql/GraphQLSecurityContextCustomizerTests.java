package org.olf.dcb.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GraphQLSecurityContextCustomizerTests {
	@Test
	void extractsRolesFromStandardClaimShapes() {
		var roles = GraphQLSecurityContextCustomizer.rolesFrom(List.of("ADMIN"), Map.of(
			"roles", List.of("CONSORTIUM_ADMIN"),
			"realm_access", Map.of("roles", List.of("LIBRARY_ADMIN")),
			"resource_access", Map.of("dcb", Map.of("roles", List.of("INTERNAL_API")))));

		assertEquals(List.of("ADMIN", "CONSORTIUM_ADMIN", "LIBRARY_ADMIN", "INTERNAL_API"), roles);
	}

	@Test
	void extractsRolesFromZitadelProjectRoleClaim() {
		var roles = GraphQLSecurityContextCustomizer.rolesFrom(List.of(), Map.of(
			"urn:zitadel:iam:org:project:roles", Map.of("ADMIN", Map.of("org", "OpenRS"))));

		assertEquals(List.of("ADMIN"), roles);
	}

	@Test
	void missingRolesReturnEmptyCollection() {
		var roles = GraphQLSecurityContextCustomizer.rolesFrom(List.of(), Map.of(
			"sub", "user-id"));

		assertEquals(List.of(), roles);
	}
}

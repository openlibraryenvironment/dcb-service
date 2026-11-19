package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;

import graphql.ExecutionInput;
import graphql.GraphQL;
import jakarta.inject.Inject;

@DcbTest
class PatronIdentityHostLmsGraphQLTests {
	@Inject
	GraphQL graphQL;
	@Inject
	PatronFixture patronFixture;
	@Inject
	HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		patronFixture.deleteAllPatrons();
		hostLmsFixture.deleteAll();
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldServeEachIdentityOfOnePatronWithItsLibrarySystem() {
		final var homeSystem = hostLmsFixture.createDummyHostLms("identity-home-system");
		final var lenderSystem = hostLmsFixture.createDummyHostLms("identity-lender-system");

		final var patron = patronFixture.savePatron("home-library");
		patronFixture.saveIdentityAndReturn(patron, homeSystem, "home-1", true, "-", "home-library", null);
		patronFixture.saveIdentityAndReturn(patron, lenderSystem, "virtual-1", false, "-", "home-library", null);

		final var otherPatron = patronFixture.savePatron("home-library");
		patronFixture.saveIdentityAndReturn(otherPatron, homeSystem, "home-2", true, "-", "home-library", null);

		final var result = graphQL.execute(ExecutionInput.newExecutionInput()
			.query("query ($query: String) { patronIdentities(query: $query, pagesize: 50) "
				+ "{ content { localId homeIdentity hostLms { code } } } }")
			.variables(Map.of("query", "patron:" + patron.getId()))
			.graphQLContext(Map.of("roles", List.of("CONSORTIUM_ADMIN")))
			.build());

		assertThat("no errors: " + result.getErrors(), result.getErrors().isEmpty(), is(true));

		final Map<String, Object> data = result.getData();
		final var content = (List<Map<String, Object>>) ((Map<String, Object>) data.get("patronIdentities")).get("content");

		assertThat(content.stream()
				.map(identity -> identity.get("localId") + "@" + ((Map<String, Object>) identity.get("hostLms")).get("code"))
				.toList(),
			containsInAnyOrder("home-1@identity-home-system", "virtual-1@identity-lender-system"));
	}
}

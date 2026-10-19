package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import graphql.ExecutionInput;
import graphql.GraphQL;
import jakarta.inject.Inject;

@DcbTest
class PatronRequestPlacedByStaffGraphQLTests {
	@Inject
	GraphQL graphQL;
	@Inject
	PatronRequestsFixture patronRequestsFixture;
	@Inject
	PatronFixture patronFixture;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldServeWhetherStaffPlacedARequest() {
		final var id = UUID.randomUUID();

		patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(id)
			.patron(patronFixture.savePatron("home-library"))
			.status(CONFIRMED)
			.placedByStaff(true)
			.build());

		final var result = graphQL.execute(ExecutionInput.newExecutionInput()
			.query("{ patronRequests(pagesize: 10) { content { id placedByStaff } } }")
			.graphQLContext(Map.of("roles", List.of("CONSORTIUM_ADMIN")))
			.build());

		assertThat("no errors: " + result.getErrors(), result.getErrors().isEmpty(), is(true));

		final Map<String, Object> data = result.getData();
		final var page = (Map<String, Object>) data.get("patronRequests");
		final var content = (List<Map<String, Object>>) page.get("content");

		final var served = content.stream()
			.filter(request -> id.toString().equals(request.get("id")))
			.findFirst()
			.orElseThrow();

		assertThat(served.get("placedByStaff"), is(true));
	}
}

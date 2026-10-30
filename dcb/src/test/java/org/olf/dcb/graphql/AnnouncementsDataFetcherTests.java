package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LibraryFixture;

import graphql.ExecutionInput;
import graphql.GraphQL;
import jakarta.inject.Inject;

@DcbTest
@TestInstance(PER_CLASS)
class AnnouncementsDataFetcherTests {
	private static final String LIST = """
		query($scopeType: String!, $scopeId: ID!) {
			announcements(scopeType: $scopeType, scopeId: $scopeId) { id headline }
		}
		""";

	@Inject
	private GraphQL graphQL;

	@Inject
	private LibraryFixture libraryFixture;

	@BeforeEach
	void beforeEach() {
		libraryFixture.deleteAll();
	}

	@AfterAll
	void afterAll() {
		libraryFixture.deleteAll();
	}

	@Test
	void aConsortiumAdministratorListsALibrarysAnnouncements() {
		final var library = libraryFixture.defineLibrary("announcements-library", null, null);

		final var result = graphQL.execute(ExecutionInput.newExecutionInput()
			.query(LIST)
			.variables(Map.of("scopeType", "LIBRARY", "scopeId", library.getId().toString()))
			.graphQLContext(Map.of("roles", List.of("CONSORTIUM_ADMIN"), "userName", "announcements-tests"))
			.build());

		assertThat(result.getErrors(), is(empty()));
		assertThat(result.<Map<String, List<?>>>getData().get("announcements"), is(empty()));
	}
}

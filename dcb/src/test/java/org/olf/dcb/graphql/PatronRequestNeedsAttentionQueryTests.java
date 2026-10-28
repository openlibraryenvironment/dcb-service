package org.olf.dcb.graphql;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import jakarta.inject.Inject;

@DcbTest
@TestInstance(PER_CLASS)
class PatronRequestNeedsAttentionQueryTests {
	private static final String REQUESTS = """
		query($q: String!) {
		  patronRequests(query: $q) { content { id needsAttention } }
		}""";

	@Inject
	private GraphQL graphQL;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	private UUID failedCheckout;
	private UUID quiet;

	@BeforeAll
	void beforeAll() {
		deleteAll();

		final var patron = patronFixture.savePatron("needs-attention");

		failedCheckout = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(PatronRequest.Status.LOANED)
			.needsAttention(true)
			.build()).getId();

		quiet = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(PatronRequest.Status.LOANED)
			.build()).getId();
	}

	@AfterAll
	void deleteAll() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
	}

	@Test
	void listsOnlyTheRequestsThatNeedAttention() {
		final var result = execute("needsAttention:true AND (id:%s OR id:%s)".formatted(failedCheckout, quiet));

		assertThat("errors: " + result.getErrors(), result.getErrors(), is(empty()));
		assertThat(ids(result), contains(failedCheckout.toString()));
	}

	private ExecutionResult execute(String query) {
		return graphQL.execute(ExecutionInput.newExecutionInput()
			.query(REQUESTS)
			.variables(Map.of("q", query))
			.graphQLContext(Map.of("roles", List.of("CONSORTIUM_ADMIN")))
			.build());
	}

	private static List<String> ids(ExecutionResult result) {
		final Map<String, Map<String, List<Map<String, Object>>>> data = result.getData();

		return data.get("patronRequests").get("content").stream()
			.map(request -> (String) request.get("id"))
			.toList();
	}
}

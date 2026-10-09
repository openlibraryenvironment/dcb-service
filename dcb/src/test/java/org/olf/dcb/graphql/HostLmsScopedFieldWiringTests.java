package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.graphql.GraphQLSecurityContextCustomizer.AGENCY_CODES;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import graphql.ExecutionInput;
import graphql.GraphQL;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

/**
 * HostLmsFieldScopeTests proves the scoping; this proves lastPing is wired to it, through
 * the real schema. Left unwired, the field is read straight off the entity for every caller.
 */
@DcbTest
class HostLmsScopedFieldWiringTests {
	private static final String CODE = "SCOPED-PING";

	@Inject
	GraphQL graphQL;
	@Inject
	HostLmsFixture hostLmsFixture;
	@Inject
	HostLmsRepository hostLmsRepository;

	@BeforeEach
	void beforeEach() {
		hostLmsFixture.deleteAll();
		hostLmsFixture.createKohaHostLms(CODE, "https://koha.example.com");
		Mono.from(hostLmsRepository.updateLastPing(CODE, "{\"status\": \"OK\"}")).block();
	}

	@Test
	void consortiumStaffReadALibrarysLastPing() {
		assertThat(lastPingAs(List.of("CONSORTIUM_ADMIN")), hasEntry("status", "OK"));
	}

	@Test
	void aLibraryUserDoesNotReadTheLastPingOfASystemTheyDoNotAdminister() {
		assertThat(lastPingAs(List.of("LIBRARY_ADMIN")), is(anEmptyMap()));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> lastPingAs(List<String> roles) {
		final var result = graphQL.execute(ExecutionInput.newExecutionInput()
			.query("{ hostLms(pagesize: 10) { content { code lastPing } } }")
			.graphQLContext(Map.of("roles", roles, AGENCY_CODES, List.of("SOME-OTHER-AGENCY")))
			.build());

		assertThat("no errors: " + result.getErrors(), result.getErrors().isEmpty(), is(true));

		final Map<String, Object> data = result.getData();
		final var page = (Map<String, Object>) data.get("hostLms");
		final var content = (List<Map<String, Object>>) page.get("content");

		return content.stream()
			.filter(hostLms -> CODE.equals(hostLms.get("code")))
			.map(hostLms -> (Map<String, Object>) hostLms.get("lastPing"))
			.findFirst()
			.orElseThrow();
	}
}

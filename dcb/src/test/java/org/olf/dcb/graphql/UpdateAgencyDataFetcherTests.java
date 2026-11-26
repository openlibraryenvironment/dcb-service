package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@DcbTest
@TestInstance(PER_CLASS)
class UpdateAgencyDataFetcherTests {

	@Inject
	private UpdateAgencyDataFetcher updateAgency;

	@Inject
	private AgencyFixture agencyFixture;

	@BeforeEach
	void beforeEach() {
		agencyFixture.deleteAll();
	}

	@AfterAll
	void afterAll() {
		agencyFixture.deleteAll();
	}

	@ParameterizedTest
	@ValueSource(strings = {"ADMIN", "CONSORTIUM_ADMIN"})
	void anAdministratorMayUpdateAnAgency(String role) {
		agencyFixture.defineAgencyWithNoHostLms("updatable", "Updatable agency");

		final var updated = (DataAgency) singleValueFrom(Mono.fromFuture(updateAgency.get(
			env(role, Map.of("code", "updatable", "latitude", 51.5)))));

		assertThat(updated.getLatitude(), is(51.5));
		assertThat(updated.getLastEditedBy(), is("the editor"));
	}

	@Test
	void aLibraryAdministratorMayNotUpdateAnAgency() {
		agencyFixture.defineAgencyWithNoHostLms("guarded", "Guarded agency");

		final var exception = assertThrows(HttpStatusException.class,
			() -> updateAgency.get(env("LIBRARY_ADMIN", Map.of("code", "guarded", "latitude", 51.5))));

		assertThat(exception.getStatus(), is(HttpStatus.UNAUTHORIZED));
		assertThat(agencyFixture.findByCode("guarded").getLatitude(), is(nullValue()));
	}

	private static DataFetchingEnvironment env(String role, Map<String, Object> input) {
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
			.graphQLContext(GraphQLContext.newContext()
				.of("roles", List.of(role))
				.of("userName", "the editor")
				.build())
			.arguments(Map.of("input", input))
			.build();
	}
}

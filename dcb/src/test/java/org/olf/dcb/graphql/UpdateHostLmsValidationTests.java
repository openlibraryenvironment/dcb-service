package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;

/**
 * A setting that becomes required must not lock out the records saved before it: they can
 * still be renamed, and they are held to the new rule the next time their configuration changes.
 */
@DcbTest
@TestInstance(PER_CLASS)
class UpdateHostLmsValidationTests {
	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private UpdateHostLmsDataFetcher dataFetcher;

	@BeforeEach
	void beforeEach() {
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldRenameAnAlmaHostSavedBeforeTheCancellationReasonWasRequired() throws Exception {
		// The fixture's Alma host has no request-cancellation-reason
		final var hostLms = hostLmsFixture.createAlmaHostLms("older-alma", "http://localhost:1");

		final var result = dataFetcher.get(environment(Map.of(
			"id", hostLms.getId().toString(),
			"name", "Renamed Alma"))).get();

		assertThat(result.getHostLms().getName(), is("Renamed Alma"));
	}

	@Test
	void shouldStillRefuseAConfigurationChangeThatLacksTheRequiredSetting() {
		final var hostLms = hostLmsFixture.createAlmaHostLms("older-alma", "http://localhost:1");

		final var config = new HashMap<>(hostLms.getClientConfig());
		config.put("sharing-library-code", "OTHER");

		final var failure = assertThrows(ExecutionException.class, () -> dataFetcher.get(environment(Map.of(
			"id", hostLms.getId().toString(),
			"clientConfig", config))).get());

		assertThat(failure.getCause() instanceof HttpStatusException, is(true));
		assertThat(((HttpStatusException) failure.getCause()).getStatus(), is(HttpStatus.BAD_REQUEST));
	}

	private static DataFetchingEnvironment environment(Map<String, Object> input) {
		final var environment = mock(DataFetchingEnvironment.class);

		when(environment.getArgument("input")).thenReturn(input);
		when(environment.getGraphQlContext()).thenReturn(GraphQLContext.of(Map.of(
			"roles", List.of("ADMIN"),
			"userName", "a-tester")));

		return environment;
	}
}

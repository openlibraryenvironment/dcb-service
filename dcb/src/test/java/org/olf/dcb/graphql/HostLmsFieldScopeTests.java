package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import reactor.core.publisher.Mono;

/** No database: the decision is which callers may read a Host LMS's scoped fields. */
class HostLmsFieldScopeTests {
	private static final Map<String, Object> LAST_PING = Map.of("status", "OK");

	private final DataHostLms ownSystem = hostLms();
	private final DataHostLms anotherLibrarysSystem = hostLms();

	@Test
	void consortiumStaffReadEverySystemsLastPing() throws Exception {
		assertThat(lastPingAs(List.of("CONSORTIUM_ADMIN"), anotherLibrarysSystem), is(LAST_PING));
	}

	@Test
	void aLibraryUserReadsTheLastPingOfTheirOwnSystem() throws Exception {
		assertThat(lastPingAs(List.of("LIBRARY_ADMIN"), ownSystem), is(LAST_PING));
	}

	@Test
	void aLibraryUserDoesNotReadAnotherLibrarysLastPing() throws Exception {
		assertThat(lastPingAs(List.of("LIBRARY_ADMIN"), anotherLibrarysSystem), is(Map.of()));
	}

	private Map<String, Object> lastPingAs(List<String> roles, DataHostLms source) throws Exception {
		final var resolver = mock(AgencyScopeResolver.class);
		when(resolver.permittedHostLmsIds(any())).thenReturn(Mono.just(Set.of(ownSystem.getId())));

		final DataFetchingEnvironment env = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
			.source(source)
			.graphQLContext(GraphQLContext.of(Map.<Object, Object>of("roles", roles)))
			.build();

		return HostLmsFieldScope.visibleToItsAdministrators(resolver, this::loadLastPing, Map.<String, Object>of())
			.get(env)
			.get();
	}

	@Test
	void aFieldHiddenFromTheCallerIsNotLoaded() throws Exception {
		lastPingAs(List.of("LIBRARY_ADMIN"), anotherLibrarysSystem);

		assertThat(loads.get(), is(0));
	}

	private final AtomicInteger loads = new AtomicInteger();

	@BeforeEach
	void resetLoads() {
		loads.set(0);
	}

	private Mono<Map<String, Object>> loadLastPing(DataHostLms hostLms) {
		loads.incrementAndGet();
		return Mono.just(LAST_PING);
	}

	private static DataHostLms hostLms() {
		return DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("CODE-" + UUID.randomUUID())
			.build();
	}
}

package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.LibraryContactRepository;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.PersonRepository;
import org.olf.dcb.storage.RoleRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LibraryFixture;
import org.reactivestreams.Publisher;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations.TransactionalCallback;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

/**
 * A created library is handed back only once its transaction has completed, and so committed:
 * a caller reading it straight back, or its change log entry, must find it.
 */
@DcbTest
@TestInstance(PER_CLASS)
class CreateLibraryAfterCommitTests {
	@Inject
	private LibraryRepository libraryRepository;
	@Inject
	private PersonRepository personRepository;
	@Inject
	private LibraryContactRepository libraryContactRepository;
	@Inject
	private RoleRepository roleRepository;
	@Inject
	private AgencyRepository agencyRepository;
	@Inject
	private HostLmsRepository hostLmsRepository;
	@Inject
	private R2dbcOperations r2dbcOperations;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private LibraryFixture libraryFixture;

	@BeforeEach
	void beforeEach() {
		libraryFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms("after-commit-sierra");
	}

	@AfterAll
	void afterAll() {
		libraryFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldHandBackTheLibraryOnlyOnceItsTransactionHasCompleted() throws Exception {
		final var transactionCompleted = new AtomicBoolean(false);
		final var completedWhenHandedBack = new AtomicBoolean(false);

		final var dataFetcher = new CreateLibraryDataFetcher(libraryRepository, personRepository,
			libraryContactRepository, roleRepository, agencyRepository, hostLmsRepository,
			recordingCompletion(r2dbcOperations, transactionCompleted));

		dataFetcher.get(environment(input("after-commit")))
			.thenAccept(library -> completedWhenHandedBack.set(transactionCompleted.get()))
			.get();

		assertThat(completedWhenHandedBack.get(), is(true));
	}

	private static R2dbcOperations recordingCompletion(R2dbcOperations operations, AtomicBoolean completed) {
		return new R2dbcOperations() {
			@Override
			public ConnectionFactory connectionFactory() {
				return operations.connectionFactory();
			}

			@Override
			public <T> Publisher<T> withTransaction(ReactiveTransactionStatus<Connection> status,
				TransactionalCallback<Connection, T> handler) {

				return operations.withTransaction(status, handler);
			}

			@Override
			public <T> Publisher<T> withTransaction(TransactionalCallback<Connection, T> handler) {
				return Flux.from(operations.withTransaction(handler))
					.doOnComplete(() -> completed.set(true));
			}

			@Override
			public <T> Publisher<T> withConnection(Function<Connection, Publisher<? extends T>> handler) {
				return operations.withConnection(handler);
			}
		};
	}

	private static Map<String, Object> input(String agencyCode) {
		final var input = new HashMap<String, Object>();
		input.put("agencyCode", agencyCode);
		input.put("fullName", "After Commit Library " + agencyCode);
		input.put("shortName", agencyCode);
		input.put("abbreviatedName", agencyCode);
		input.put("address", "1 Library Street");
		input.put("type", "Public");
		input.put("hostLmsCode", "after-commit-sierra");
		input.put("authProfile", "BASIC/BARCODE+PIN");
		return input;
	}

	private static DataFetchingEnvironment environment(Map<String, Object> input) {
		final var environment = mock(DataFetchingEnvironment.class);
		when(environment.getArgument("input")).thenReturn(input);
		when(environment.getGraphQlContext()).thenReturn(GraphQLContext.of(Map.of(
			"roles", List.of("ADMIN"), "userName", "a-tester")));
		return environment;
	}
}

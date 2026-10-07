package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.DataChangeLog;
import org.olf.dcb.storage.DataChangeLogRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LibraryFixture;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

/**
 * The change log entry for a new library carries what the caller said about the change.
 */
@DcbTest
@TestInstance(PER_CLASS)
class CreateLibraryChangeLogTests {
	@Inject
	private CreateLibraryDataFetcher dataFetcher;

	@Inject
	private DataChangeLogRepository dataChangeLogRepository;

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
		hostLmsFixture.createSierraHostLms("change-log-sierra");
	}

	// The libraries created here reference agencies, so a later class deleting agencies fails if they survive
	@AfterAll
	void afterAll() {
		libraryFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldRecordTheReasonCategoryAndReferenceItWasGiven() throws Exception {
		final var input = libraryInput("change-log-given");
		input.put("reason", "Initial setup");
		input.put("changeCategory", "Onboarding");
		input.put("changeReferenceUrl", "https://tickets.example.org/42");

		final var library = dataFetcher.get(environment(input)).get();

		assertThat(changeLogFor(library.getId()).stream().map(entry -> List.of(entry.getReason(),
				entry.getChangeCategory(), entry.getChangeReferenceUrl())).toList(),
			contains(List.of("Initial setup", "Onboarding", "https://tickets.example.org/42")));
	}

	@Test
	void shouldFallBackToTheOldWordingWhenNoneIsGiven() throws Exception {
		final var library = dataFetcher.get(environment(libraryInput("change-log-default"))).get();

		assertThat(changeLogFor(library.getId()).stream().map(entry -> List.of(entry.getReason(),
				entry.getChangeCategory())).toList(),
			contains(List.of("Adding a new library", "New member")));
	}

	private static Map<String, Object> libraryInput(String agencyCode) {
		final var input = new HashMap<String, Object>();
		input.put("agencyCode", agencyCode);
		input.put("fullName", "Change Log Library " + agencyCode);
		input.put("shortName", agencyCode);
		input.put("abbreviatedName", agencyCode);
		input.put("address", "1 Library Street");
		input.put("type", "Public");
		input.put("hostLmsCode", "change-log-sierra");
		input.put("authProfile", "BASIC/BARCODE+PIN");
		return input;
	}

	private List<DataChangeLog> changeLogFor(UUID libraryId) {
		return Flux.from(dataChangeLogRepository.queryAll())
			.filter(entry -> libraryId.equals(entry.getEntityId()))
			.collectList()
			.block();
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

package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LibraryFixture;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@DcbTest
@TestInstance(PER_CLASS)
class UpdateLibraryDataFetcherScopeTests {

	@Inject
	private UpdateLibraryDataFetcher updateLibrary;

	@Inject
	private LibraryRepository libraryRepository;

	@Inject
	private LibraryFixture libraryFixture;

	private Library ownLibrary;
	private Library otherLibrary;

	@BeforeEach
	void beforeEach() {
		libraryFixture.deleteAll();

		ownLibrary = libraryFixture.defineLibrary("own-library", "Card", "PIN");
		otherLibrary = libraryFixture.defineLibrary("other-library", "Card", "PIN");
	}

	@AfterAll
	void afterAll() {
		libraryFixture.deleteAll();
	}

	@Test
	void aLibraryAdministratorMayNotEditAnotherLibrary() {
		final var exception = assertThrows(HttpStatusException.class,
			() -> update(env(List.of("LIBRARY_ADMIN"), List.of("own-library")), otherLibrary));

		assertThat(exception.getStatus().getCode(), is(403));
		assertThat(supportHoursOf(otherLibrary), is((String) null));
	}

	@Test
	void aLibraryAdministratorMayEditTheirOwnLibrary() {
		final var updated = update(env(List.of("LIBRARY_ADMIN"), List.of("own-library")), ownLibrary);

		assertThat(updated.getSupportHours(), is("9-5"));
	}

	@Test
	void aConsortiumAdministratorMayEditAnyLibrary() {
		final var updated = update(env(List.of("CONSORTIUM_ADMIN"), List.of()), otherLibrary);

		assertThat(updated.getSupportHours(), is("9-5"));
	}

	// The fetcher's future completes on the saved row, before its transaction commits, so a
	// second read can race the commit
	@Test
	void aLinkStoredBeforeTheCheckDoesNotBlockSavingTheRestOfTheRecord() {
		ownLibrary.setPatronWebsite("www.own-library.example.org");
		singleValueFrom(libraryRepository.update(ownLibrary));

		// The admin form sends every field back, including the old link it was given
		final var updated = update(env(List.of("CONSORTIUM_ADMIN"), List.of()), ownLibrary,
			Map.of("patronWebsite", "www.own-library.example.org"));

		assertThat(updated.getSupportHours(), is("9-5"));
		assertThat(updated.getPatronWebsite(), is("www.own-library.example.org"));
	}

	private Library update(DataFetchingEnvironment env, Library library) {
		return update(env, library, Map.of());
	}

	private Library update(DataFetchingEnvironment env, Library library, Map<String, Object> extra) {
		final var input = new java.util.HashMap<String, Object>(Map.of(
			"id", library.getId().toString(),
			"supportHours", "9-5",
			"reason", "Scope test"));
		input.putAll(extra);

		return singleValueFrom(Mono.fromFuture(updateLibrary.get(DataFetchingEnvironmentImpl
			.newDataFetchingEnvironment(env)
			.arguments(Map.of("input", input))
			.build())));
	}

	private String supportHoursOf(Library library) {
		return singleValueFrom(libraryRepository.findById(library.getId())).getSupportHours();
	}

	private static DataFetchingEnvironment env(Collection<String> roles,
		Collection<String> agencyCodes) {

		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
			.graphQLContext(GraphQLContext.newContext()
				.of("roles", roles)
				.of("userName", "a tester")
				.of(GraphQLSecurityContextCustomizer.AGENCY_CODES, agencyCodes)
				.build())
			.build();
	}
}

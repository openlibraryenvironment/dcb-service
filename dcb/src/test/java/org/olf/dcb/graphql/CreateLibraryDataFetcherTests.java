package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LibraryFixture;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironmentImpl;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@DcbTest
@TestInstance(PER_CLASS)
class CreateLibraryDataFetcherTests {

	private static final String HOST_LMS = "create-library-tests";

	@Inject
	private CreateLibraryDataFetcher createLibrary;

	@Inject
	private LibraryFixture libraryFixture;

	@Inject
	private AgencyFixture agencyFixture;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		clear();
		hostLmsFixture.createFolioHostLms(HOST_LMS, "https://fake-folio", "key", "", "");
	}

	@AfterAll
	void afterAll() {
		clear();
	}

	private void clear() {
		libraryFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void aSupportUrlSentOnCreateIsStored() {
		final var library = create(Map.of("supportUrl", "https://help.example.org"));

		assertThat(library.getSupportUrl(), is("https://help.example.org"));
	}

	@Test
	void aSupportUrlUpdateWouldRefuseIsRefusedOnCreate() {
		final var exception = assertThrows(HttpStatusException.class,
			() -> create(Map.of("supportUrl", "help/desk")));

		assertThat(exception.getStatus().getCode(), is(400));
	}

	private Library create(Map<String, Object> extra) {
		final var input = new java.util.HashMap<String, Object>(Map.of(
			"agencyCode", "created-library",
			"fullName", "Created Library",
			"shortName", "Created",
			"abbreviatedName", "CL",
			"hostLmsCode", HOST_LMS));
		input.putAll(extra);

		return singleValueFrom(Mono.fromFuture(createLibrary.get(DataFetchingEnvironmentImpl
			.newDataFetchingEnvironment()
			.graphQLContext(GraphQLContext.newContext()
				.of("roles", List.of("CONSORTIUM_ADMIN"))
				.of("userName", "a tester")
				.build())
			.arguments(Map.of("input", input))
			.build())));
	}
}

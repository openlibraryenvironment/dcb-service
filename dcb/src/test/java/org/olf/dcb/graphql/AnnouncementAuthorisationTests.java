package org.olf.dcb.graphql;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.ScopeLevel;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.storage.LibraryGroupRepository;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.DataAccess;
import org.olf.dcb.test.LibraryFixture;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;

/**
 * Who may publish an announcement, and at which scope. The environment is built rather than
 * mocked: the roles and agency claims in the context ARE the input to this decision.
 */
@DcbTest
@TestInstance(PER_CLASS)
class AnnouncementAuthorisationTests {

	@Inject
	private AnnouncementAuthorisation authorisation;

	@Inject
	private LibraryFixture libraryFixture;

	@Inject
	private ConsortiumFixture consortiumFixture;

	@Inject
	private LibraryGroupRepository libraryGroups;

	private Library ownLibrary;
	private Library otherLibrary;

	@BeforeEach
	void beforeEach() {
		consortiumFixture.deleteAll();
		new DataAccess().deleteAll(libraryGroups.queryAll(), g -> libraryGroups.delete(g.getId()));
		libraryFixture.deleteAll();

		ownLibrary = libraryFixture.defineLibrary("mine", null, null);
		otherLibrary = libraryFixture.defineLibrary("theirs", null, null);
	}

	@AfterAll
	void afterAll() {
		consortiumFixture.deleteAll();
		new DataAccess().deleteAll(libraryGroups.queryAll(), g -> libraryGroups.delete(g.getId()));
		libraryFixture.deleteAll();
	}

	@Test
	@DisplayName("a consortium administrator may publish at consortium scope")
	void aConsortiumAdministratorMayPublishAtConsortiumScope() {
		final var scopeId = consortiumFixture.createConsortium().getId();

		assertThat(singleValueFrom(authorisation.assertMayWrite(
			env(List.of("CONSORTIUM_ADMIN"), List.of()), ScopeLevel.CONSORTIUM, scopeId)),
			is(scopeId));
	}

	@Test
	@DisplayName("a consortium administrator may publish for a library group")
	void aConsortiumAdministratorMayPublishForAGroup() {
		final var group = (LibraryGroup) singleValueFrom(libraryGroups.save(LibraryGroup.builder()
			.id(randomUUID()).code("west").name("West region").type("REGION").build()));

		assertThat(singleValueFrom(authorisation.assertMayWrite(
			env(List.of("CONSORTIUM_ADMIN"), List.of()), ScopeLevel.LIBRARY_GROUP, group.getId())),
			is(group.getId()));
	}

	@Test
	@DisplayName("a notice for a scope that does not exist is refused, not stored for nobody")
	void aScopeThatDoesNotExistIsRefused() {
		final var exception = assertThrows(HttpStatusException.class,
			() -> singleValueFrom(authorisation.assertMayWrite(
				env(List.of("CONSORTIUM_ADMIN"), List.of()), ScopeLevel.LIBRARY_GROUP, randomUUID())));

		assertThat(exception.getStatus().getCode(), is(404));
	}

	@Test
	@DisplayName("a library administrator may not publish for a group")
	void aLibraryAdministratorMayNotPublishForAGroup() {
		final var exception = assertThrows(HttpStatusException.class,
			() -> singleValueFrom(authorisation.assertMayWrite(
				env(List.of("LIBRARY_ADMIN"), List.of("mine")), ScopeLevel.LIBRARY_GROUP, randomUUID())));

		assertThat(exception.getStatus().getCode(), is(403));
	}

	@Test
	@DisplayName("a library administrator may NOT publish at consortium scope")
	void aLibraryAdministratorMayNotPublishAtConsortiumScope() {
		// The blast-radius rule, and the reason authorisation is not simply "is this person
		// an administrator of something". A consortium notice reaches every patron of every
		// member library; a library administrator's authority stops at their own.
		final var exception = assertThrows(HttpStatusException.class,
			() -> singleValueFrom(authorisation.assertMayWrite(
				env(List.of("LIBRARY_ADMIN"), List.of("mine")),
				ScopeLevel.CONSORTIUM, randomUUID())));

		assertThat(exception.getStatus().getCode(), is(403));
	}

	@Test
	@DisplayName("a library administrator may publish for their own library")
	void aLibraryAdministratorMayPublishForTheirOwn() {
		assertThat(singleValueFrom(authorisation.assertMayWrite(
			env(List.of("LIBRARY_ADMIN"), List.of("mine")),
			ScopeLevel.LIBRARY, ownLibrary.getId())),
			is(ownLibrary.getId()));
	}

	@Test
	@DisplayName("a library administrator may not publish for somebody else's library")
	void aLibraryAdministratorMayNotPublishForAnothersLibrary() {
		// The scope arrives in the request body; the agency claims arrive in the token. This
		// is the line between the two, and it is the whole reason the scope is not simply
		// trusted.
		final var exception = assertThrows(HttpStatusException.class,
			() -> singleValueFrom(authorisation.assertMayWrite(
				env(List.of("LIBRARY_ADMIN"), List.of("mine")),
				ScopeLevel.LIBRARY, otherLibrary.getId())));

		assertThat(exception.getStatus().getCode(), is(403));
	}

	@Test
	@DisplayName("a library administrator who administers nothing may publish nothing")
	void anAdministratorOfNothingMayPublishNothing() {
		// Fails closed. An empty claim is not "everything" and must never read as it.
		final var exception = assertThrows(HttpStatusException.class,
			() -> singleValueFrom(authorisation.assertMayWrite(
				env(List.of("LIBRARY_ADMIN"), List.of()),
				ScopeLevel.LIBRARY, ownLibrary.getId())));

		assertThat(exception.getStatus().getCode(), is(403));
	}

	@Test
	@DisplayName("a reader with no administrative role may publish nothing")
	void aReaderMayPublishNothing() {
		final var exception = assertThrows(HttpStatusException.class,
			() -> singleValueFrom(authorisation.assertMayWrite(
				env(List.of("LIBRARY_READ_ONLY"), List.of("mine")),
				ScopeLevel.LIBRARY, ownLibrary.getId())));

		assertThat(exception.getStatus().getCode(), is(403));
	}

	@Test
	@DisplayName("a request carrying no roles at all is refused rather than defaulted")
	void noRolesIsRefused() {
		final var exception = assertThrows(HttpStatusException.class,
			() -> singleValueFrom(authorisation.assertMayWrite(
				envWithoutRoles(), ScopeLevel.LIBRARY, ownLibrary.getId())));

		assertThat(exception.getStatus().getCode(), is(403));
	}

	private static DataFetchingEnvironment env(Collection<String> roles,
		Collection<String> agencyCodes) {

		final var context = GraphQLContext.newContext()
			.of("roles", roles)
			.of(GraphQLSecurityContextCustomizer.AGENCY_CODES, agencyCodes)
			.build();

		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
			.graphQLContext(context)
			.build();
	}

	private static DataFetchingEnvironment envWithoutRoles() {
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
			.graphQLContext(GraphQLContext.newContext().build())
			.build();
	}
}

package org.olf.dcb.graphql;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Announcement;
import org.olf.dcb.core.model.DataChangeLog;
import org.olf.dcb.core.model.ScopeLevel;
import org.olf.dcb.storage.AnnouncementRepository;
import org.olf.dcb.storage.DataChangeLogRepository;
import org.olf.dcb.test.DataAccess;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LibraryFixture;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironmentImpl;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DcbTest
@TestInstance(PER_CLASS)
class DeleteAnnouncementDataFetcherTests {

	@Inject
	private DeleteAnnouncementDataFetcher deleteAnnouncement;

	@Inject
	private AnnouncementRepository announcements;

	@Inject
	private DataChangeLogRepository dataChangeLog;

	@Inject
	private LibraryFixture libraryFixture;

	private final DataAccess dataAccess = new DataAccess();

	@BeforeEach
	void beforeEach() {
		dataAccess.deleteAll(announcements.queryAll(), a -> announcements.delete(a.getId()));
		libraryFixture.deleteAll();
	}

	@AfterAll
	void afterAll() {
		beforeEach();
	}

	@Test
	void theAuditEntryForAWithdrawalNamesWhoWithdrewItAndWhy() {
		final var library = libraryFixture.defineLibrary("withdrawing", null, null);
		final var notice = (Announcement) singleValueFrom(announcements.save(Announcement.builder()
			.id(randomUUID())
			.scopeType(ScopeLevel.LIBRARY)
			.scopeId(library.getId())
			.headline("Closed on Monday")
			.body("Closed on Monday")
			.urgent(false)
			.dismissible(true)
			.startsAt(Instant.now().minus(Duration.ofDays(1)))
			.expiresAt(Instant.now().plus(Duration.ofDays(1)))
			.lastEditedBy("the publisher")
			.reason("Published")
			.build()));

		singleValueFrom(Mono.fromFuture(deleteAnnouncement.get(DataFetchingEnvironmentImpl
			.newDataFetchingEnvironment()
			.graphQLContext(GraphQLContext.newContext()
				.of("roles", List.of("CONSORTIUM_ADMIN"))
				.of("userName", "the withdrawer")
				.build())
			.arguments(Map.of("input", Map.of(
				"id", notice.getId().toString(),
				"reason", "Reopened after all")))
			.build())));

		final var deletions = Flux.from(dataChangeLog.queryAll())
			.filter(entry -> notice.getId().equals(entry.getEntityId()))
			.filter(entry -> "DELETE".equalsIgnoreCase(entry.getActionInfo()))
			.collectList()
			.block();

		assertThat(deletions, hasSize(1));
		assertThat(deletions.get(0).getLastEditedBy(), is("the withdrawer"));
		assertThat(deletions.get(0).getReason(), is("Reopened after all"));
	}
}

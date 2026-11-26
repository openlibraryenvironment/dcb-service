package org.olf.dcb.storage;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Announcement;
import org.olf.dcb.core.model.ScopeLevel;
import org.olf.dcb.test.DataAccess;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
@TestInstance(PER_CLASS)
class AnnouncementRepositoryTests {
	private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

	@Inject
	private AnnouncementRepository announcements;

	private final DataAccess dataAccess = new DataAccess();

	@BeforeEach
	void beforeEach() {
		clearAnnouncements();
	}

	@AfterAll
	void afterAll() {
		clearAnnouncements();
	}

	@Test
	void anAdministratorSeesNothingForAScopeWithNoAnnouncements() {
		assertThat(manyValuesFrom(announcements.findForAdministration(ScopeLevel.LIBRARY.name(), randomUUID())),
			empty());
	}

	@Test
	void anAdministratorSeesEveryAnnouncementForTheScopeNewestFirst() {
		final var library = randomUUID();
		save(library, "older", NOW.minus(Duration.ofDays(2)));
		save(library, "newer", NOW.minus(Duration.ofDays(1)));
		save(randomUUID(), "another library's", NOW);

		final var listed = manyValuesFrom(announcements.findForAdministration(ScopeLevel.LIBRARY.name(), library));

		assertThat(listed.stream().map(Announcement::getHeadline).toList(), contains("newer", "older"));
	}

	private void save(UUID scopeId, String headline, Instant startsAt) {
		singleValueFrom(announcements.save(Announcement.builder()
			.id(randomUUID())
			.scopeType(ScopeLevel.LIBRARY)
			.scopeId(scopeId)
			.headline(headline)
			.body("Body of " + headline)
			.urgent(false)
			.dismissible(true)
			.startsAt(startsAt)
			.expiresAt(NOW.plus(Duration.ofDays(30)))
			.build()));
	}

	private void clearAnnouncements() {
		dataAccess.deleteAll(announcements.queryAll(), a -> announcements.delete(a.getId()));
	}
}

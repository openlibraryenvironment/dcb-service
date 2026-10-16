package org.olf.dcb.core;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Announcement;
import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.core.model.LibraryGroupMember;
import org.olf.dcb.core.model.ScopeLevel;
import org.olf.dcb.storage.AnnouncementRepository;
import org.olf.dcb.storage.LibraryGroupMemberRepository;
import org.olf.dcb.storage.LibraryGroupRepository;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.DataAccess;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LibraryFixture;

import jakarta.inject.Inject;

/**
 * What a patron is told, and — more importantly — what they are not.
 *
 * The rules here are refusals, and refusals are what rot first: one at a time, the narrower
 * level wins, the consortium only joins it when urgent, and an expired notice is gone. Every
 * one of those is a thing somebody will later ask to relax, so every one has a test that
 * says why it is there.
 *
 * The clock is stood still rather than waited on. A window is the whole feature, and a test
 * that sleeps to observe one is a test that is slow when it passes and flaky when it fails.
 */
@DcbTest
@TestInstance(PER_CLASS)
class AnnouncementServiceTests {

	private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

	@Inject
	private AnnouncementRepository announcements;

	@Inject
	private ConsortiumService consortiumService;

	@Inject
	private ConsortiumFixture consortiumFixture;

	@Inject
	private LibraryFixture libraryFixture;

	@Inject
	private LibraryGroupRepository libraryGroups;

	@Inject
	private LibraryGroupMemberRepository groupMembers;

	private final DataAccess dataAccess = new DataAccess();

	private AnnouncementService service;
	private Consortium consortium;

	@BeforeEach
	void beforeEach() {
		clearEverything();

		consortium = consortiumFixture.createConsortium();
		service = new AnnouncementService(announcements, consortiumService, libraryGroups,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@AfterAll
	void afterAll() {
		clearEverything();
	}

	@Test
	@DisplayName("nothing to say is an empty list, not a placeholder")
	void nothingToSayIsAnEmptyList() {
		assertThat(singleValueFrom(service.forPatronOf(library("quiet"))), hasSize(0));
	}

	@Test
	@DisplayName("a patron with no library named sees the consortium's")
	void aPatronWithNoLibraryNamedSeesTheConsortiums() {
		// Someone browsing before they have said which library they belong to. There is no
		// narrower notice for the consortium's to be competing with, so urgency is not the
		// question — it simply shows.
		announcement(ScopeLevel.CONSORTIUM, consortium.getId(), "Consortium notice", false);

		assertThat(headlines(null), contains("Consortium notice"));
	}

	@Test
	@DisplayName("the library's own notice shows, and a routine consortium one does not join it")
	void theLibrarysOwnNoticeWinsAlone() {
		// Two banners with no hierarchy is the thing the precedence rule exists to prevent.
		// The consortium has something routine to say; the library has something about this
		// patron's own service, and that is the one that matters today.
		final var library = library("both-1");
		announcement(ScopeLevel.CONSORTIUM, consortium.getId(), "Routine consortium notice", false);
		announcement(ScopeLevel.LIBRARY, library.getId(), "Our lift is broken", false);

		assertThat(headlines(library), contains("Our lift is broken"));
	}

	@Test
	@DisplayName("an URGENT consortium notice shows beneath the library's, in that order")
	void anUrgentConsortiumNoticeShowsBeneathTheLibrarys() {
		// The one case where two are right: requesting is down consortium-wide AND this
		// library has its own news. Order is part of the assertion — the patron's own
		// library speaks first.
		final var library = library("both-2");
		announcement(ScopeLevel.CONSORTIUM, consortium.getId(), "Requesting is paused", true);
		announcement(ScopeLevel.LIBRARY, library.getId(), "Our lift is broken", false);

		assertThat(headlines(library), contains("Our lift is broken", "Requesting is paused"));
	}

	@Test
	@DisplayName("an urgent consortium notice joins the library's even when a newer routine one is live")
	void anOlderUrgentConsortiumNoticeIsNotHiddenByANewerRoutineOne() {
		final var library = library("both-3");
		announcement(ScopeLevel.CONSORTIUM, consortium.getId(), "Requesting is paused", true,
			NOW.minus(Duration.ofDays(3)));
		announcement(ScopeLevel.CONSORTIUM, consortium.getId(), "New opening hours", false,
			NOW.minus(Duration.ofDays(1)));
		announcement(ScopeLevel.LIBRARY, library.getId(), "Our lift is broken", false);

		assertThat(headlines(library), contains("Our lift is broken", "Requesting is paused"));
	}

	@Test
	@DisplayName("a group's notice reaches the patrons of its member libraries")
	void aGroupsNoticeReachesItsMembersPatrons() {
		final var library = library("grouped-1");
		final var region = groupContaining("north", library);
		announcement(ScopeLevel.LIBRARY_GROUP, region.getId(), "Northern van is delayed", false);
		announcement(ScopeLevel.CONSORTIUM, consortium.getId(), "Requesting is paused", true);

		assertThat(headlines(library), contains("Northern van is delayed", "Requesting is paused"));
	}

	@Test
	@DisplayName("a library's own notice comes first, with an urgent group notice beneath it")
	void anUrgentGroupNoticeShowsBeneathTheLibrarys() {
		final var library = library("grouped-2");
		final var region = groupContaining("south", library);
		announcement(ScopeLevel.LIBRARY_GROUP, region.getId(), "Southern van is cancelled", true);
		announcement(ScopeLevel.LIBRARY, library.getId(), "Our lift is broken", false);

		assertThat(headlines(library), contains("Our lift is broken", "Southern van is cancelled"));
	}

	@Test
	@DisplayName("a group's notice does not reach a library outside the group")
	void aGroupsNoticeStaysInsideTheGroup() {
		final var member = library("grouped-3");
		final var outsider = library("ungrouped-3");
		final var region = groupContaining("east", member);
		announcement(ScopeLevel.LIBRARY_GROUP, region.getId(), "Eastern van is delayed", false);

		assertThat(headlines(outsider), hasSize(0));
	}

	@Test
	@DisplayName("one at a time: three live notices at one scope show the newest")
	void oneAtATime() {
		// A deliberate refusal rather than a missing feature. A strip a patron has to page
		// through is a strip they stop reading, and an operator with three things to say has
		// a website for the other two.
		final var library = library("many-1");
		announcement(ScopeLevel.LIBRARY, library.getId(), "Oldest", false, NOW.minus(Duration.ofDays(3)));
		announcement(ScopeLevel.LIBRARY, library.getId(), "Middle", false, NOW.minus(Duration.ofDays(2)));
		announcement(ScopeLevel.LIBRARY, library.getId(), "Newest", false, NOW.minus(Duration.ofDays(1)));

		assertThat(headlines(library), contains("Newest"));
	}

	@Test
	@DisplayName("an expired notice is gone, and that is the whole reason expiry is required")
	void anExpiredNoticeIsGone() {
		// The failure mode of every announcement banner ever built is the notice from March
		// still up in November, which teaches every patron to ignore that strip permanently
		// — including on the day it matters.
		final var library = library("expired-1");
		window(ScopeLevel.LIBRARY, library.getId(), "Last March",
			NOW.minus(Duration.ofDays(200)), NOW.minus(Duration.ofDays(100)));

		assertThat(headlines(library), hasSize(0));
	}

	@Test
	@DisplayName("a notice that has not started yet is not shown either")
	void aNoticeThatHasNotStartedIsNotShown() {
		// How an operator writes Monday's notice on Friday. It must not appear on Friday.
		final var library = library("future-1");
		window(ScopeLevel.LIBRARY, library.getId(), "From Monday",
			NOW.plus(Duration.ofDays(3)), NOW.plus(Duration.ofDays(10)));

		assertThat(headlines(library), hasSize(0));
	}

	@Test
	@DisplayName("one library's notice is not another's")
	void oneLibrarysNoticeIsNotAnothers() {
		final var mine = library("mine-1");
		final var theirs = library("theirs-1");
		announcement(ScopeLevel.LIBRARY, theirs.getId(), "Their lift is broken", false);

		assertThat(headlines(mine), hasSize(0));
	}

	private java.util.List<String> headlines(Library library) {
		return singleValueFrom(service.forPatronOf(library)).stream()
			.map(Announcement::getHeadline)
			.toList();
	}

	private LibraryGroup groupContaining(String code, Library library) {
		final var group = (LibraryGroup) singleValueFrom(libraryGroups.save(LibraryGroup.builder()
			.id(randomUUID())
			.code(code)
			.name(code + " region")
			.type("REGION")
			.build()));

		singleValueFrom(groupMembers.save(LibraryGroupMember.builder()
			.id(randomUUID())
			.library(library)
			.libraryGroup(group)
			.build()));

		return group;
	}

	private Library library(String agencyCode) {
		return libraryFixture.defineLibrary(agencyCode, null, null);
	}

	private Announcement announcement(ScopeLevel scopeType, UUID scopeId, String headline,
		boolean urgent) {

		return announcement(scopeType, scopeId, headline, urgent, NOW.minus(Duration.ofDays(1)));
	}

	private Announcement announcement(ScopeLevel scopeType, UUID scopeId, String headline,
		boolean urgent, Instant startsAt) {

		return save(scopeType, scopeId, headline, urgent, startsAt, NOW.plus(Duration.ofDays(30)));
	}

	/** An explicit window, for the two cases where the window IS the assertion. */
	private Announcement window(ScopeLevel scopeType, UUID scopeId, String headline,
		Instant startsAt, Instant expiresAt) {

		return save(scopeType, scopeId, headline, false, startsAt, expiresAt);
	}

	private Announcement save(ScopeLevel scopeType, UUID scopeId, String headline,
		boolean urgent, Instant startsAt, Instant expiresAt) {

		return (Announcement) singleValueFrom(announcements.save(Announcement.builder()
			.id(randomUUID())
			.scopeType(scopeType)
			.scopeId(scopeId)
			.headline(headline)
			.body("Body of " + headline)
			.urgent(urgent)
			.dismissible(true)
			.startsAt(startsAt)
			.expiresAt(expiresAt)
			.build()));
	}

	private void clearEverything() {
		dataAccess.deleteAll(announcements.queryAll(), a -> announcements.delete(a.getId()));
		dataAccess.deleteAll(groupMembers.queryAll(), m -> groupMembers.delete(m.getId()));
		consortiumFixture.deleteAll();
		dataAccess.deleteAll(libraryGroups.queryAll(), g -> libraryGroups.delete(g.getId()));
		libraryFixture.deleteAll();
	}
}

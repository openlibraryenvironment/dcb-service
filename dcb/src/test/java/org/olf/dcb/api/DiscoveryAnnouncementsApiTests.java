package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.DiscoveryAnnouncementsController.AnnouncementView;
import org.olf.dcb.core.model.Announcement;
import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.ScopeLevel;
import org.olf.dcb.storage.AnnouncementRepository;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.DataAccess;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LibraryFixture;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;

/**
 * The anonymous announcements route.
 *
 * {@link org.olf.dcb.core.AnnouncementServiceTests} covers the precedence rules. What is
 * asserted here is the part that only exists at the HTTP boundary: which library a code
 * selects, and what the view is allowed to carry.
 */
@DcbTest
@TestInstance(PER_CLASS)
class DiscoveryAnnouncementsApiTests {

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private AnnouncementRepository announcements;

	@Inject
	private ConsortiumFixture consortiumFixture;

	@Inject
	private LibraryFixture libraryFixture;

	private final DataAccess dataAccess = new DataAccess();

	private Consortium consortium;

	@BeforeEach
	void beforeEach() {
		clearEverything();
		consortium = consortiumFixture.createConsortium();
	}

	@AfterAll
	void afterAll() {
		clearEverything();
	}

	@Test
	@DisplayName("a code selects one library's notice out of many")
	void aCodeSelectsOneLibraryOutOfMany() {
		// Several libraries present, one of them speaking
		final var mine = library("SELECT-MINE");
		library("SELECT-OTHER-A");
		library("SELECT-OTHER-B");

		announcement(ScopeLevel.LIBRARY, mine.getId(), "Our lift is broken");

		assertThat(headlines("SELECT-MINE"), contains("Our lift is broken"));
		assertThat(headlines("SELECT-OTHER-A"), hasSize(0));
	}

	@Test
	@DisplayName("the code is matched whatever case it arrives in")
	void theCodeIsMatchedCaseInsensitively() {
		// It reaches us from a URL a patron may have typed or a bookmark they may have
		// edited. A case mismatch answering about the CONSORTIUM instead would be a wrong
		// answer that looks exactly like a right one.
		final var library = library("CASE-TEST");
		announcement(ScopeLevel.LIBRARY, library.getId(), "We close at four");

		assertThat(headlines("case-test"), contains("We close at four"));
		assertThat(headlines("Case-Test"), contains("We close at four"));
	}

	@Test
	@DisplayName("an unknown code answers the consortium's, never a 404")
	void anUnknownCodeAnswersTheConsortiums() {
		// A patron with a stale bookmark should see the notice, not an error page. It also
		// means the route cannot be used to discover which libraries exist.
		announcement(ScopeLevel.CONSORTIUM, consortium.getId(), "Requesting is paused");

		assertThat(headlines("NO-SUCH-LIBRARY"), contains("Requesting is paused"));
		assertThat(headlines(null), contains("Requesting is paused"));
	}

	@Test
	@DisplayName("the view carries no staff field")
	void theViewCarriesNoStaffField() {
		// lastEditedBy, reason and changeReferenceUrl name a member of staff and an internal
		// ticket. This route is anonymous, so the projection is the boundary.
		final var library = library("STAFF-FIELDS");
		singleValueFrom(announcements.save(Announcement.builder()
			.id(randomUUID())
			.scopeType(ScopeLevel.LIBRARY)
			.scopeId(library.getId())
			.headline("Visible headline")
			.body("Visible body")
			.urgent(false)
			.dismissible(true)
			.startsAt(Instant.now().minus(1, ChronoUnit.DAYS))
			.expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
			.lastEditedBy("a.librarian@example.org")
			.reason("Ticket DCB-1234")
			.changeReferenceUrl("https://tickets.example.org/DCB-1234")
			.build()));

		final var body = client.toBlocking().retrieve(
			HttpRequest.GET("/discovery/announcements?libraryCode=STAFF-FIELDS"));

		assertThat(body.contains("Visible headline"), is(true));
		assertThat(body.contains("a.librarian@example.org"), is(false));
		assertThat(body.contains("DCB-1234"), is(false));
	}

	private List<String> headlines(String libraryCode) {
		final var uri = libraryCode == null
			? "/discovery/announcements"
			: "/discovery/announcements?libraryCode=" + libraryCode;

		return client.toBlocking()
			.retrieve(HttpRequest.GET(uri), Argument.listOf(AnnouncementView.class))
			.stream()
			.map(AnnouncementView::headline)
			.toList();
	}

	private Library library(String agencyCode) {
		return libraryFixture.defineLibrary(agencyCode, null, null);
	}

	private void announcement(ScopeLevel scopeType, UUID scopeId, String headline) {
		singleValueFrom(announcements.save(Announcement.builder()
			.id(randomUUID())
			.scopeType(scopeType)
			.scopeId(scopeId)
			.headline(headline)
			.body("Body of " + headline)
			.urgent(true)
			.dismissible(true)
			.startsAt(Instant.now().minus(1, ChronoUnit.DAYS))
			.expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
			.build()));
	}

	private void clearEverything() {
		dataAccess.deleteAll(announcements.queryAll(), a -> announcements.delete(a.getId()));
		consortiumFixture.deleteAll();
		libraryFixture.deleteAll();
	}
}

package org.olf.dcb.graphql;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.graphql.GraphQLSecurityContextCustomizer.AGENCY_CODES;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.PatronRequestNote;
import org.olf.dcb.core.model.RequestNoteAuthorKind;
import org.olf.dcb.core.notifications.StaffNotificationService;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestNoteRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestNoteRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

/** A library's notification feed (S-20) and its weekly trends (S-23). */
@DcbTest
@TestInstance(PER_CLASS)
class StaffNotificationsTests {
	private static final List<String> LIBRARY_READ_ONLY = List.of("LIBRARY_READ_ONLY");
	private static final String OWN = "NOTIFY_OWN";
	private static final String OTHER = "NOTIFY_OTHER";

	private static final String FEED = """
		query($agency: String, $kinds: [String!]) {
		  staffNotifications(agencyCode: $agency, kinds: $kinds) {
		    generatedAt
		    items { id kind occurredAt count bucket patronRequestId authorAgencyCode measure current baseline direction }
		  }
		}""";

	@Inject
	private GraphQL graphQL;
	@Inject
	private R2dbcOperations r2dbcOperations;
	@Inject
	private PatronRequestAuditRepository audits;
	@Inject
	private PatronRequestNoteRepository noteWriter;
	@Inject
	private PostgresPatronRequestNoteRepository notes;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	private PatronRequest notedRequest;

	@BeforeAll
	void beforeAll() {
		deleteAll();

		final var hostLms = hostLmsFixture.createDummyHostLms("NOTIFY_LMS");
		final var own = agencyFixture.defineAgency(OWN, "Own Library", hostLms);
		final var other = agencyFixture.defineAgency(OTHER, "Other Library", hostLms);

		request(hostLms, own, "own-ready", PatronRequest.Status.READY_FOR_PICKUP);
		request(hostLms, other, "other-ready", PatronRequest.Status.READY_FOR_PICKUP);
		notedRequest = request(hostLms, own, "own-noted", PatronRequest.Status.CONFIRMED);

		singleValueFrom(noteWriter.save(PatronRequestNote.builder()
			.id(randomUUID())
			.patronRequestId(notedRequest.getId())
			.authorKind(RequestNoteAuthorKind.LIBRARY)
			.agencyCode(OTHER)
			.body("Arrived damp")
			.dateCreated(Instant.now())
			.build()));
		singleValueFrom(noteWriter.save(PatronRequestNote.builder()
			.id(randomUUID())
			.patronRequestId(notedRequest.getId())
			.authorKind(RequestNoteAuthorKind.LIBRARY)
			.agencyCode(OWN)
			.body("Our own note")
			.dateCreated(Instant.now().plusSeconds(5))
			.build()));

		final var unfilled = request(hostLms, own, "own-unfilled", PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY);
		for (int index = 0; index < 4; index++) {
			audit(unfilled, "this-week");
		}
		for (int index = 0; index < 4; index++) {
			audit(unfilled, "earlier");
		}
		// All four earlier ones in one week, fifteen days back: a trailing average of half a week
		execute("UPDATE patron_request_audit SET audit_date = now() - interval '15 days' WHERE brief_description = 'earlier'");
	}

	@AfterAll
	void deleteAll() {
		singleValueFrom(notes.deleteAll());
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void aLibraryHearsAboutItsOwnCountsAndOtherLibrariesNotesAndNothingElse() {
		final var items = items(feed(null, null, List.of(OWN)));

		assertThat(fieldOf(items, "bucket"), hasItem("readyForPickup"));
		final var ready = items.stream().filter(item -> "readyForPickup".equals(item.get("bucket"))).findFirst().orElseThrow();
		assertThat(ready, hasEntry("kind", "REQUESTS_NEED_ACTION"));
		assertThat(ready, hasEntry("count", 1));

		final var noted = items.stream().filter(item -> "NOTE_ADDED".equals(item.get("kind"))).toList();
		assertThat(noted, hasSize(1));
		assertThat(noted.getFirst(), hasEntry("patronRequestId", notedRequest.getId().toString()));
		assertThat(noted.getFirst(), hasEntry("authorAgencyCode", OTHER));

		assertThat(items.size(), lessThanOrEqualTo(StaffNotificationService.MAX_ITEMS));
		assertThat(items.toString(), not(containsString("Arrived damp")));
	}

	@Test
	void aWeekWellAboveTheLastEightIsATrend() {
		final var trends = items(feed(OWN, List.of("TREND"), List.of(OWN)));

		assertThat(trends, hasSize(1));
		assertThat(trends.getFirst(), hasEntry("measure", "UNFILLED_BORROWING"));
		assertThat(trends.getFirst(), hasEntry("current", 4.0));
		assertThat(trends.getFirst(), hasEntry("baseline", 0.5));
		assertThat(trends.getFirst(), hasEntry("direction", "UP"));
	}

	@Test
	void theKindsAskedForAreTheOnlyKindsReturned() {
		final var items = items(feed(null, List.of("NOTE_ADDED"), List.of(OWN)));

		assertThat(fieldOf(items, "kind"), everyItem(is("NOTE_ADDED")));
		assertThat(errorOf(feed(null, List.of("GOSSIP"), List.of(OWN))), containsString("Unknown notification kind"));
	}

	@Test
	void aLibraryCannotReadAnotherLibrarysFeed() {
		assertThat(errorOf(feed(OTHER, null, List.of(OWN))), containsString("your own library"));
	}

	@Test
	void anAccountWithSeveralLibrariesSaysWhich() {
		assertThat(errorOf(feed(null, null, List.of(OWN, OTHER))), containsString("Say which library"));
		assertThat(items(feed(OTHER, null, List.of(OWN, OTHER))), not(empty()));
	}

	private PatronRequest request(DataHostLms hostLms, DataAgency agency, String patronHome,
		PatronRequest.Status status) {

		final var patron = patronFixture.savePatron(patronHome);
		final var identity = patronFixture.saveIdentityAndReturn(patron, hostLms, patronHome + "-id",
			true, "-", patronHome, agency);

		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.status(status)
			.dateUpdated(Instant.now())
			.build());
	}

	private void audit(PatronRequest request, String description) {
		singleValueFrom(audits.save(PatronRequestAudit.builder()
			.id(randomUUID())
			.patronRequest(request)
			.briefDescription(description)
			.fromStatus(PatronRequest.Status.RESOLVED)
			.toStatus(PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY)
			.build()));
	}

	private ExecutionResult feed(String agencyCode, List<String> kinds, List<String> claims) {
		final var variables = new java.util.HashMap<String, Object>();
		if (agencyCode != null) variables.put("agency", agencyCode);
		if (kinds != null) variables.put("kinds", kinds);

		return graphQL.execute(ExecutionInput.newExecutionInput()
			.query(FEED)
			.variables(variables)
			.graphQLContext(Map.of("roles", LIBRARY_READ_ONLY, AGENCY_CODES, claims,
				"userName", "staff", "currentUser", "staff-sub"))
			.build());
	}

	private static List<Map<String, Object>> items(ExecutionResult result) {
		assertThat("errors: " + result.getErrors(), result.getErrors(), is(empty()));
		final Map<String, Object> data = result.getData();
		final Map<String, Object> feed = cast(data.get("staffNotifications"));
		return cast(feed.get("items"));
	}

	private static List<Object> fieldOf(List<Map<String, Object>> items, String field) {
		return items.stream().map(item -> item.get(field)).toList();
	}

	private static String errorOf(ExecutionResult result) {
		assertThat(result.getErrors(), hasSize(1));
		return result.getErrors().getFirst().getMessage();
	}

	private void execute(String sql) {
		Flux.from(r2dbcOperations.withConnection(connection ->
				Flux.from(connection.createStatement(sql).execute()).flatMap(result -> result.getRowsUpdated())))
			.collectList()
			.block(Duration.ofMinutes(1));
	}

	@SuppressWarnings("unchecked")
	private static <T> T cast(Object value) {
		return (T) value;
	}
}

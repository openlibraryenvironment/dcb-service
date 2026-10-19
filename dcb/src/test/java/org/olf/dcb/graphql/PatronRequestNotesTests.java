package org.olf.dcb.graphql;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.graphql.GraphQLSecurityContextCustomizer.AGENCY_CODES;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.InactiveSupplierRequest;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestNote;
import org.olf.dcb.core.model.RequestNoteAuthorKind;
import org.olf.dcb.core.model.RequestTag;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.PatronRequestNoteRepository;
import org.olf.dcb.storage.RequestTagRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestNoteRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestTagRepository;
import org.olf.dcb.storage.postgres.PostgresRequestTagRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.InactiveSupplierRequestsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironmentImpl;
import io.micronaut.data.model.Page;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

@DcbTest
@TestInstance(PER_CLASS)
class PatronRequestNotesTests {
	private static final List<String> LIBRARY_ADMIN = List.of("LIBRARY_ADMIN");
	private static final List<String> CONSORTIUM_ADMIN = List.of("CONSORTIUM_ADMIN");

	private static final String USER_NAME = "jane.librarian";
	private static final String USER_EMAIL = "jane@library.example";
	private static final String USER_SUB = "subject-7f3a";

	private static final String ADD_NOTE = """
		mutation($id: ID!, $body: String!, $agency: String) {
		  addPatronRequestNote(patronRequestId: $id, body: $body, agencyCode: $agency) {
		    id authorKind agencyCode libraryName body retracted retractedBy
		  }
		}""";

	private static final String RETRACT_NOTE = """
		mutation($id: ID!) {
		  retractPatronRequestNote(id: $id) { id body retracted retractedBy }
		}""";

	private static final String TAG = """
		mutation($id: ID!, $tag: String!, $agency: String) {
		  tagPatronRequest(patronRequestId: $id, tagCode: $tag, agencyCode: $agency) { code label agencyCodes consortium }
		}""";

	private static final String UNTAG = """
		mutation($id: ID!, $tag: String!) {
		  untagPatronRequest(patronRequestId: $id, tagCode: $tag) { code agencyCodes consortium }
		}""";

	private static final String REQUEST_WITH_NOTES = """
		query($q: String!, $size: Int) {
		  patronRequests(query: $q) {
		    content {
		      id involvedAgencyCodes
		      notes(pagesize: $size) { totalSize content { body agencyCode authorKind } }
		      tags { code agencyCodes consortium }
		    }
		  }
		}""";

	@Inject
	private GraphQL graphQL;
	@Inject
	private R2dbcOperations r2dbcOperations;
	@Inject
	private PatronRequestNoteDataFetchers noteDataFetchers;
	@Inject
	private PatronRequestNoteRepository noteWriter;
	@Inject
	private PostgresPatronRequestNoteRepository notes;
	@Inject
	private PostgresPatronRequestTagRepository appliedTags;
	@Inject
	private PostgresRequestTagRepository vocabulary;
	@Inject
	private RequestTagRepository vocabularyWriter;

	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private LocationFixture locationFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private InactiveSupplierRequestsFixture inactiveSupplierRequestsFixture;

	private UUID requestId;

	@BeforeAll
	void beforeAll() {
		deleteAll();

		final var hostLms = hostLmsFixture.createDummyHostLms("NOTES_LMS");
		final var borrower = agencyFixture.defineAgency("NOTES_B", "Borrowing Library", hostLms);
		agencyFixture.defineAgency("NOTES_S", "Supplying Library", hostLms);
		agencyFixture.defineAgency("NOTES_E", "Earlier Library", hostLms);
		final var pickup = agencyFixture.defineAgency("NOTES_P", "Pickup Library", hostLms);
		agencyFixture.defineAgency("NOTES_X", "Stranger Library", hostLms);

		final var pickupLocation = locationFixture.createPickupLocation(pickup);
		final var patron = patronFixture.savePatron("home");
		final var identity = patronFixture.saveIdentityAndReturn(patron, hostLms, "patron-1",
			true, "-", "home", borrower);

		final var request = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.pickupLocationCode(pickupLocation.getId().toString())
			.status(PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.build());

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(request)
			.localItemId("item-current")
			.hostLmsCode("NOTES_LMS")
			.localAgency("NOTES_S")
			.isActive(true)
			.build());

		inactiveSupplierRequestsFixture.save(InactiveSupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(request)
			.localItemId("item-earlier")
			.hostLmsCode("NOTES_LMS")
			.localAgency("NOTES_E")
			.build());

		requestId = request.getId();
	}

	@BeforeEach
	void deleteNotesAndTags() {
		singleValueFrom(notes.deleteAll());
		singleValueFrom(appliedTags.deleteAll());
	}

	@AfterAll
	void deleteAll() {
		singleValueFrom(notes.deleteAll());
		singleValueFrom(appliedTags.deleteAll());
		for (var code : List.of("TEST_TAG", "RETIRED_TAG", "KEEP_TAG", "SPARE_TAG")) {
			singleValueFrom(vocabulary.deleteById(code));
		}
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		locationFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void aLibraryInvolvedInTheRequestWritesANoteInItsOwnName() {
		final var note = data(addNote(LIBRARY_ADMIN, List.of("NOTES_B"), "Arrived wet", null), "addPatronRequestNote");

		assertThat(note, hasEntry("authorKind", "LIBRARY"));
		assertThat(note, hasEntry("agencyCode", "NOTES_B"));
		assertThat(note, hasEntry("libraryName", "Borrowing Library"));
		assertThat(note, hasEntry("body", "Arrived wet"));
		assertThat(note, hasEntry("retracted", false));
	}

	@Test
	void thePickupLibraryCanWriteANote() {
		assertThat(data(addNote(LIBRARY_ADMIN, List.of("NOTES_P"), "Collected", null), "addPatronRequestNote"),
			hasEntry("agencyCode", "NOTES_P"));
	}

	@Test
	void anEarlierSupplierNeitherWritesNorSeesTheRequest() {
		assertThat(errorOf(addNote(LIBRARY_ADMIN, List.of("NOTES_E"), "We could not ship", null)),
			containsString("does not belong to your library"));

		final Map<String, Object> page = data(execute(REQUEST_WITH_NOTES, Map.of("q", "id:" + requestId),
			LIBRARY_ADMIN, List.of("NOTES_E")), "patronRequests");
		final List<Map<String, Object>> content = cast(page.get("content"));
		assertThat(content, is(empty()));
	}

	@Test
	void aLibraryNotInvolvedIsRefusedAsIfTheRequestDidNotExist() {
		final var refused = errorOf(addNote(LIBRARY_ADMIN, List.of("NOTES_X"), "Not mine", null));
		final var unknown = errorOf(execute(ADD_NOTE, Map.of("id", randomUUID().toString(), "body", "x"),
			LIBRARY_ADMIN, List.of("NOTES_X")));

		assertThat(refused, containsString("does not belong to your library"));
		assertThat(unknown, containsString("does not belong to your library"));
	}

	@Test
	void readOnlyStaffCannotWriteANote() {
		assertThat(errorOf(addNote(List.of("LIBRARY_READ_ONLY"), List.of("NOTES_B"), "Read only", null)),
			containsString("Access denied"));
	}

	@Test
	void consortiumStaffWriteAsTheConsortiumWhateverAgencyTheyHoldOrName() {
		final var note = data(addNote(List.of("CONSORTIUM_ADMIN", "LIBRARY_ADMIN"), List.of("NOTES_B"),
			"Consortium note", "NOTES_B"), "addPatronRequestNote");

		assertThat(note, hasEntry("authorKind", "CONSORTIUM"));
		assertThat(note, hasEntry("agencyCode", null));
		assertThat(note, hasEntry("libraryName", null));
	}

	@Test
	void aCallerHoldingSeveralInvolvedAgenciesMustNameOneOfThem() {
		final var holder = List.of("NOTES_B", "NOTES_P", "NOTES_X");

		assertThat(errorOf(addNote(LIBRARY_ADMIN, holder, "Which?", null)), containsString("Name the library"));
		assertThat(data(addNote(LIBRARY_ADMIN, holder, "As pickup", "NOTES_P"), "addPatronRequestNote"),
			hasEntry("agencyCode", "NOTES_P"));
		assertThat(errorOf(addNote(LIBRARY_ADMIN, holder, "As stranger", "NOTES_X")),
			containsString("does not belong to your library"));
	}

	@Test
	void theStoredNoteAndTheResponseCarryNoUserIdentity() {
		final var response = addNote(LIBRARY_ADMIN, List.of("NOTES_B"), "Identity check", null);

		final var rows = query("SELECT CAST(row_to_json(n) AS text) FROM patron_request_note n");
		assertThat(rows, hasSize(1));

		for (var text : List.of(rows.get(0), response.getData().toString())) {
			assertThat(text, not(containsString(USER_NAME)));
			assertThat(text, not(containsString(USER_EMAIL)));
			assertThat(text, not(containsString(USER_SUB)));
		}
	}

	@Test
	void aNoteIsPlainTextOfBoundedLength() {
		assertThat(errorOf(addNote(LIBRARY_ADMIN, List.of("NOTES_B"), "   ", null)), containsString("between 1 and"));
		assertThat(errorOf(addNote(LIBRARY_ADMIN, List.of("NOTES_B"), "x".repeat(2001), null)),
			containsString("between 1 and"));
	}

	@Test
	void aRequestHoldsAtMostTwoHundredNotes() {
		Flux.range(0, 200)
			.concatMap(i -> noteWriter.save(PatronRequestNote.builder()
				.id(randomUUID())
				.patronRequestId(requestId)
				.authorKind(RequestNoteAuthorKind.LIBRARY)
				.agencyCode("NOTES_B")
				.body("note " + i)
				.dateCreated(Instant.now().minusSeconds(1000 - i))
				.build()))
			.blockLast(Duration.ofMinutes(2));

		assertThat(errorOf(addNote(LIBRARY_ADMIN, List.of("NOTES_B"), "One too many", null)),
			containsString("maximum of 200"));
	}

	@Test
	void notesAreReadNewestFirstAndAtMostFiftyAtATime() {
		Flux.range(0, 60)
			.concatMap(i -> noteWriter.save(PatronRequestNote.builder()
				.id(randomUUID())
				.patronRequestId(requestId)
				.authorKind(RequestNoteAuthorKind.LIBRARY)
				.agencyCode("NOTES_B")
				.body("note " + i)
				.dateCreated(Instant.now().minusSeconds(1000 - i))
				.build()))
			.blockLast(Duration.ofMinutes(2));

		final var request = onlyRequest(execute(REQUEST_WITH_NOTES,
			Map.of("q", "id:" + requestId, "size", 500), LIBRARY_ADMIN, List.of("NOTES_P")));

		final Map<String, Object> page = cast(request.get("notes"));
		final List<Map<String, Object>> content = cast(page.get("content"));

		assertThat(page, hasEntry("totalSize", 60));
		assertThat(content, hasSize(50));
		assertThat(content.get(0), hasEntry("body", "note 59"));
	}

	@Test
	void everyPartyCanReadTheRequestAndItsInvolvedAgencies() {
		addNote(LIBRARY_ADMIN, List.of("NOTES_S"), "Shipped", null);

		final var request = onlyRequest(execute(REQUEST_WITH_NOTES, Map.of("q", "id:" + requestId),
			LIBRARY_ADMIN, List.of("NOTES_P")));

		final List<String> involved = cast(request.get("involvedAgencyCodes"));
		assertThat(involved, containsInAnyOrder("NOTES_B", "NOTES_S", "NOTES_P"));

		final Map<String, Object> page = cast(request.get("notes"));
		assertThat(page, hasEntry("totalSize", 1));
	}

	@Test
	void theNotesResolverChecksInvolvementItselfWhenHandedARequest() throws Exception {
		addNote(LIBRARY_ADMIN, List.of("NOTES_B"), "Private to the parties", null);

		final var env = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
			.source(PatronRequest.builder().id(requestId).build())
			.arguments(Map.of())
			.graphQLContext(GraphQLContext.of(Map.<Object, Object>of(
				"roles", LIBRARY_ADMIN, AGENCY_CODES, List.of("NOTES_X"))))
			.build();

		final Page<PatronRequestNote> page = noteDataFetchers.notesOfPatronRequest().get(env).get();
		assertThat(page.getContent(), is(empty()));
		assertThat(noteDataFetchers.tagsOfPatronRequest().get(env).get(), is(empty()));
		assertThat(noteDataFetchers.involvedAgencyCodesOfPatronRequest().get(env).get(), is(empty()));
	}

	@Test
	void aLibraryRetractsItsOwnNoteAndNoOtherLibrarys() {
		final String noteId = (String) data(addNote(LIBRARY_ADMIN, List.of("NOTES_B"), "Wrong request", null),
			"addPatronRequestNote").get("id");

		assertThat(errorOf(execute(RETRACT_NOTE, Map.of("id", noteId), LIBRARY_ADMIN, List.of("NOTES_S"))),
			containsString("does not belong to your library"));

		final var retracted = data(execute(RETRACT_NOTE, Map.of("id", noteId), LIBRARY_ADMIN, List.of("NOTES_B")),
			"retractPatronRequestNote");

		assertThat(retracted, hasEntry("body", null));
		assertThat(retracted, hasEntry("retracted", true));
		assertThat(retracted, hasEntry("retractedBy", "LIBRARY"));
	}

	@Test
	void consortiumStaffRetractAnyNoteAndOnlyTheyRetractTheConsortiums() {
		final String libraryNote = (String) data(addNote(LIBRARY_ADMIN, List.of("NOTES_B"), "Rude", null),
			"addPatronRequestNote").get("id");
		final String consortiumNote = (String) data(addNote(CONSORTIUM_ADMIN, List.of(), "Policy reminder", null),
			"addPatronRequestNote").get("id");

		assertThat(data(execute(RETRACT_NOTE, Map.of("id", libraryNote), CONSORTIUM_ADMIN, List.of()),
			"retractPatronRequestNote"), hasEntry("retractedBy", "CONSORTIUM"));
		assertThat(errorOf(execute(RETRACT_NOTE, Map.of("id", consortiumNote), LIBRARY_ADMIN, List.of("NOTES_B"))),
			containsString("does not belong to your library"));
	}

	@Test
	void eachAuthorAppliesATagInItsOwnNameOnce() {
		execute(TAG, Map.of("id", requestId.toString(), "tag", "DAMAGED"), LIBRARY_ADMIN, List.of("NOTES_B"));
		execute(TAG, Map.of("id", requestId.toString(), "tag", "DAMAGED"), LIBRARY_ADMIN, List.of("NOTES_B"));
		execute(TAG, Map.of("id", requestId.toString(), "tag", "DAMAGED"), LIBRARY_ADMIN, List.of("NOTES_P"));
		final List<Map<String, Object>> tags = cast(execute(TAG, Map.of("id", requestId.toString(), "tag", "DAMAGED"),
			CONSORTIUM_ADMIN, List.of()).<Map<String, Object>>getData().get("tagPatronRequest"));

		assertThat(tags, hasSize(1));
		assertThat(tags.get(0), hasEntry("label", "Damaged"));
		assertThat(tags.get(0), hasEntry("agencyCodes", List.of("NOTES_B", "NOTES_P")));
		assertThat(tags.get(0), hasEntry("consortium", true));
	}

	@Test
	void aLibraryRemovesOnlyItsOwnApplicationOfATag() {
		execute(TAG, Map.of("id", requestId.toString(), "tag", "DAMAGED"), LIBRARY_ADMIN, List.of("NOTES_B"));
		execute(TAG, Map.of("id", requestId.toString(), "tag", "DAMAGED"), LIBRARY_ADMIN, List.of("NOTES_S"));

		final List<Map<String, Object>> tags = cast(execute(UNTAG, Map.of("id", requestId.toString(), "tag", "DAMAGED"),
			LIBRARY_ADMIN, List.of("NOTES_B")).<Map<String, Object>>getData().get("untagPatronRequest"));

		assertThat(tags, hasSize(1));
		assertThat(tags.get(0), hasEntry("agencyCodes", List.of("NOTES_S")));
	}

	@Test
	void onlyActiveTagsFromTheVocabularyCanBeApplied() {
		singleValueFrom(vocabularyWriter.save(RequestTag.builder()
			.code("RETIRED_TAG").label("Retired").active(false).build()));

		assertThat(errorOf(execute(TAG, Map.of("id", requestId.toString(), "tag", "FREE TEXT"),
			LIBRARY_ADMIN, List.of("NOTES_B"))), containsString("No such tag"));
		assertThat(errorOf(execute(TAG, Map.of("id", requestId.toString(), "tag", "RETIRED_TAG"),
			LIBRARY_ADMIN, List.of("NOTES_B"))), containsString("No such tag"));
	}

	@Test
	void theStartingVocabularyIsSeeded() {
		final List<Map<String, Object>> tags = cast(execute("{ requestTags { code label active } }", Map.of(),
			List.of("LIBRARY_READ_ONLY"), List.of("NOTES_B")).<Map<String, Object>>getData().get("requestTags"));

		assertThat(tags.stream().map(tag -> (String) tag.get("code")).filter(Set.of(
				"DAMAGED", "MISSING_PIECES", "PATRON_CONTACTED", "RETURN_DISPUTED", "SLOW_TO_SHIP")::contains).toList(),
			contains("DAMAGED", "MISSING_PIECES", "PATRON_CONTACTED", "RETURN_DISPUTED", "SLOW_TO_SHIP"));
	}

	@Test
	void onlyConsortiumStaffEditTheVocabulary() {
		final var create = """
			mutation { createRequestTag(input: { code: "TEST_TAG", label: "Test tag" }) { code label active } }""";

		assertThat(errorOf(execute(create, Map.of(), LIBRARY_ADMIN, List.of("NOTES_B"))), containsString("Access denied"));
		assertThat(data(execute(create, Map.of(), CONSORTIUM_ADMIN, List.of()), "createRequestTag"),
			hasEntry("active", true));
		assertThat(errorOf(execute(create, Map.of(), CONSORTIUM_ADMIN, List.of())), containsString("already exists"));

		final var retire = """
			mutation { updateRequestTag(input: { code: "TEST_TAG", label: "Test tag", active: false }) { active } }""";
		assertThat(data(execute(retire, Map.of(), CONSORTIUM_ADMIN, List.of()), "updateRequestTag"),
			hasEntry("active", false));

		final var badCode = """
			mutation { createRequestTag(input: { code: "lower case", label: "Bad" }) { code } }""";
		assertThat(errorOf(execute(badCode, Map.of(), CONSORTIUM_ADMIN, List.of())), containsString("A tag code is"));
	}

	@Test
	void updatingATagKeepsWhatTheInputLeavesOut() {
		execute("""
			mutation { createRequestTag(input: { code: "KEEP_TAG", label: "Keep", description: "Kept text" }) { code } }""",
			Map.of(), CONSORTIUM_ADMIN, List.of());
		execute("""
			mutation { updateRequestTag(input: { code: "KEEP_TAG", label: "Keep", description: "Kept text", active: false }) { code } }""",
			Map.of(), CONSORTIUM_ADMIN, List.of());

		final var renamed = data(execute("""
			mutation { updateRequestTag(input: { code: "KEEP_TAG", label: "Kept" }) { label description active } }""",
			Map.of(), CONSORTIUM_ADMIN, List.of()), "updateRequestTag");

		assertThat(renamed, hasEntry("label", "Kept"));
		assertThat("a retired tag stays retired", renamed, hasEntry("active", false));
		assertThat(renamed, hasEntry("description", "Kept text"));

		final var cleared = data(execute("""
			mutation { updateRequestTag(input: { code: "KEEP_TAG", label: "Kept", description: null }) { description active } }""",
			Map.of(), CONSORTIUM_ADMIN, List.of()), "updateRequestTag");

		assertThat("an explicit null clears it", cleared, hasEntry("description", null));
		assertThat(cleared, hasEntry("active", false));
	}

	@Test
	void aTagIsDeletedOnlyWhileNoRequestCarriesIt() {
		execute("""
			mutation { createRequestTag(input: { code: "SPARE_TAG", label: "Spare" }) { code } }""",
			Map.of(), CONSORTIUM_ADMIN, List.of());
		execute(TAG, Map.of("id", requestId.toString(), "tag", "SPARE_TAG"), LIBRARY_ADMIN, List.of("NOTES_B"));

		final var delete = """
			mutation { deleteRequestTag(code: "SPARE_TAG") { success message } }""";

		assertThat(errorOf(execute(delete, Map.of(), LIBRARY_ADMIN, List.of("NOTES_B"))), containsString("Access denied"));
		assertThat(errorOf(execute(delete, Map.of(), CONSORTIUM_ADMIN, List.of())), containsString("retire it instead"));
		assertThat(vocabularyCodes(), hasItem("SPARE_TAG"));

		execute(UNTAG, Map.of("id", requestId.toString(), "tag", "SPARE_TAG"), LIBRARY_ADMIN, List.of("NOTES_B"));

		assertThat(data(execute(delete, Map.of(), CONSORTIUM_ADMIN, List.of()), "deleteRequestTag"),
			hasEntry("success", true));
		assertThat(vocabularyCodes(), not(hasItem("SPARE_TAG")));
		assertThat(errorOf(execute(delete, Map.of(), CONSORTIUM_ADMIN, List.of())), containsString("No such tag"));
	}

	private List<String> vocabularyCodes() {
		final List<Map<String, Object>> tags = cast(execute("{ requestTags { code } }", Map.of(),
			CONSORTIUM_ADMIN, List.of()).<Map<String, Object>>getData().get("requestTags"));

		return tags.stream().map(tag -> (String) tag.get("code")).toList();
	}

	private ExecutionResult addNote(List<String> roles, List<String> agencies, String body, String agency) {
		final var variables = new HashMap<String, Object>();
		variables.put("id", requestId.toString());
		variables.put("body", body);
		variables.put("agency", agency);

		return execute(ADD_NOTE, variables, roles, agencies);
	}

	private ExecutionResult execute(String query, Map<String, Object> variables, List<String> roles,
		List<String> agencies) {

		return graphQL.execute(ExecutionInput.newExecutionInput()
			.query(query)
			.variables(variables)
			.graphQLContext(Map.of("roles", roles, AGENCY_CODES, agencies,
				"userName", USER_NAME, "userEmail", USER_EMAIL, "currentUser", USER_SUB))
			.build());
	}

	private static Map<String, Object> data(ExecutionResult result, String field) {
		assertThat("errors: " + result.getErrors(), result.getErrors(), is(empty()));

		final Map<String, Object> data = result.getData();
		return cast(data.get(field));
	}

	private static String errorOf(ExecutionResult result) {
		assertThat(result.getErrors(), hasSize(1));

		return result.getErrors().get(0).getMessage();
	}

	private static Map<String, Object> onlyRequest(ExecutionResult result) {
		final Map<String, Object> page = data(result, "patronRequests");
		final List<Map<String, Object>> content = cast(page.get("content"));

		assertThat(content, hasSize(1));
		return content.get(0);
	}

	@SuppressWarnings("unchecked")
	private static <T> T cast(Object value) {
		assertThat(value, is(notNullValue()));
		return (T) value;
	}

	private List<String> query(String sql) {
		return Flux.from(r2dbcOperations.withConnection(connection ->
				Flux.from(connection.createStatement(sql).execute())
					.flatMap(result -> result.map((row, metadata) -> String.valueOf(row.get(0))))))
			.collectList()
			.block(Duration.ofMinutes(2));
	}
}

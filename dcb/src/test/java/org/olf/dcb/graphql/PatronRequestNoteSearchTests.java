package org.olf.dcb.graphql;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.graphql.GraphQLSecurityContextCustomizer.AGENCY_CODES;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestNote;
import org.olf.dcb.core.model.PatronRequestTag;
import org.olf.dcb.core.model.RequestNoteAuthorKind;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.PatronRequestNoteRepository;
import org.olf.dcb.storage.PatronRequestTagRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestNoteRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestTagRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import jakarta.inject.Inject;

/**
 * The tagCode and noteText fields of the patron request query, in the shapes DCB Admin sends them.
 */
@DcbTest
@TestInstance(PER_CLASS)
class PatronRequestNoteSearchTests {
	private static final List<String> CONSORTIUM_ADMIN = List.of("CONSORTIUM_ADMIN");
	private static final List<String> LIBRARY_ADMIN = List.of("LIBRARY_ADMIN");

	private static final String SEARCH = """
		query($q: String!) {
		  patronRequests(query: $q, pagesize: 100) { content { description } }
		}""";

	@Inject
	private GraphQL graphQL;
	@Inject
	private PatronRequestNoteRepository noteWriter;
	@Inject
	private PatronRequestTagRepository tagWriter;
	@Inject
	private PostgresPatronRequestNoteRepository notes;
	@Inject
	private PostgresPatronRequestTagRepository appliedTags;

	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;

	private DataHostLms hostLms;
	private UUID otherSymbols;

	@BeforeAll
	void beforeAll() {
		deleteAll();

		hostLms = hostLmsFixture.createDummyHostLms("SEARCH_LMS");
		final var borrower = agencyFixture.defineAgency("SRCH_B", "Borrowing Library", hostLms);
		agencyFixture.defineAgency("SRCH_S", "Supplying Library", hostLms);
		final var stranger = agencyFixture.defineAgency("SRCH_X", "Stranger Library", hostLms);

		final var mine = request("mine", borrower);
		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(PatronRequest.builder().id(mine).build())
			.localItemId("item-mine")
			.hostLmsCode("SEARCH_LMS")
			.localAgency("SRCH_S")
			.isActive(true)
			.build());

		tag(mine, "DAMAGED", "SRCH_B");
		tag(mine, "DAMAGED", "SRCH_S");
		tag(mine, "DAMAGED", null);
		note(mine, "SRCH_B", "Returned with WATER damage: see \"photo\"");
		note(mine, "SRCH_S", "Also water stained");

		final var others = request("other", stranger);
		tag(others, "DAMAGED", "SRCH_X");
		note(others, "SRCH_X", "Some water damage too");

		final var symbols = request("symbols", borrower);
		tag(symbols, "SLOW_TO_SHIP", "SRCH_B");
		note(symbols, "SRCH_B", "Discount 50% applied");
		note(symbols, "SRCH_B", "Shelf code A_1");
		note(symbols, "SRCH_B", "Path C:\\temp");

		otherSymbols = request("lookalikes", borrower);
		note(otherSymbols, "SRCH_B", "Discount 500 applied");
		note(otherSymbols, "SRCH_B", "Shelf code AB1");
		note(otherSymbols, "SRCH_B", "Path C:temp");
	}

	@AfterAll
	void deleteAll() {
		singleValueFrom(notes.deleteAll());
		singleValueFrom(appliedTags.deleteAll());
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void aTagCodeFindsEachRequestCarryingItOnceWhoeverAppliedIt() {
		final var found = search("tagCode:DAMAGED", CONSORTIUM_ADMIN, List.of());

		assertThat("three parties tagged \"mine\", and it is listed once", found, hasSize(2));
		assertThat(found, containsInAnyOrder("mine", "other"));
	}

	@Test
	void tagCodesCombineLikeAnyOtherField() {
		assertThat(search("(tagCode:DAMAGED OR tagCode:SLOW_TO_SHIP)", CONSORTIUM_ADMIN, List.of()),
			containsInAnyOrder("mine", "other", "symbols"));
		assertThat(search("*:* AND NOT (tagCode:DAMAGED)", CONSORTIUM_ADMIN, List.of()),
			containsInAnyOrder("symbols", "lookalikes"));
		assertThat(search("tagCode:DAMAGED AND patronAgencyCode:SRCH_X", CONSORTIUM_ADMIN, List.of()),
			containsInAnyOrder("other"));
	}

	@Test
	void aLibrarySearchingByTagSeesOnlyRequestsInItsScope() {
		assertThat(search("tagCode:DAMAGED", LIBRARY_ADMIN, List.of("SRCH_B")), containsInAnyOrder("mine"));
		assertThat(search("tagCode:DAMAGED", LIBRARY_ADMIN, List.of("SRCH_S")), containsInAnyOrder("mine"));
		assertThat(search("tagCode:DAMAGED", LIBRARY_ADMIN, List.of("SRCH_X")), containsInAnyOrder("other"));
		assertThat("a negated tag does not reach past the scope either",
			search("*:* AND NOT (tagCode:DAMAGED)", LIBRARY_ADMIN, List.of("SRCH_X")), is(empty()));
	}

	@Test
	void aQuotedPhraseWithSpacesAColonAndAnEscapedQuoteMatchesNoteText() {
		assertThat(search("noteText:\"with water damage: see \\\"photo\\\"\"", CONSORTIUM_ADMIN, List.of()),
			containsInAnyOrder("mine"));
	}

	@Test
	void noteTextIgnoresCaseAndListsARequestWithSeveralMatchingNotesOnce() {
		final var found = search("noteText:water", CONSORTIUM_ADMIN, List.of());

		assertThat(found, hasSize(2));
		assertThat(found, containsInAnyOrder("mine", "other"));
		assertThat(search("noteText:\"WATER DAMAGE\"", CONSORTIUM_ADMIN, List.of()),
			containsInAnyOrder("mine", "other"));
	}

	@Test
	void aLibrarySearchingNoteTextSeesOnlyRequestsInItsScope() {
		assertThat(search("noteText:water", LIBRARY_ADMIN, List.of("SRCH_B")), containsInAnyOrder("mine"));
		assertThat(search("noteText:water", LIBRARY_ADMIN, List.of("SRCH_X")), containsInAnyOrder("other"));
	}

	@Test
	void likeMetacharactersInNoteTextAreLiteral() {
		assertThat(search("noteText:\"50%\"", CONSORTIUM_ADMIN, List.of()), containsInAnyOrder("symbols"));
		assertThat(search("noteText:\"code A_1\"", CONSORTIUM_ADMIN, List.of()), containsInAnyOrder("symbols"));
		assertThat(search("noteText:\"C:\\\\temp\"", CONSORTIUM_ADMIN, List.of()), containsInAnyOrder("symbols"));
	}

	@Test
	void aRetractedNoteNeverMatches() {
		final String noteId = (String) data(execute("""
				mutation($id: ID!) { addPatronRequestNote(patronRequestId: $id, body: "Withdrawn remark") { id } }""",
				Map.of("id", otherSymbols.toString()), LIBRARY_ADMIN, List.of("SRCH_B")),
			"addPatronRequestNote").get("id");

		assertThat(search("noteText:\"withdrawn remark\"", CONSORTIUM_ADMIN, List.of()),
			containsInAnyOrder("lookalikes"));

		data(execute("mutation($id: ID!) { retractPatronRequestNote(id: $id) { id } }",
			Map.of("id", noteId), LIBRARY_ADMIN, List.of("SRCH_B")), "retractPatronRequestNote");

		assertThat(search("noteText:\"withdrawn remark\"", CONSORTIUM_ADMIN, List.of()), is(empty()));
	}

	@Test
	void noteTextShorterThanThreeCharactersIsRefused() {
		for (var query : List.of("noteText:ab", "noteText:\"  ab  \"", "noteText:\"\"")) {
			assertThat(query, errorOf(execute(SEARCH, Map.of("q", query), CONSORTIUM_ADMIN, List.of())),
				containsString("Cannot filter on noteText: search for at least 3 characters."));
		}
	}

	@Test
	void aTagCodeOutsideTheVocabularyShapeIsRefused() {
		assertThat(errorOf(execute(SEARCH, Map.of("q", "tagCode:\"not a code\""), CONSORTIUM_ADMIN, List.of())),
			containsString("Cannot filter on tagCode: \"not a code\" is not a tag code."));
	}

	@Test
	void noteAndTagFieldsApplyOnlyToPatronRequests() {
		final var result = execute("""
			query($q: String!) { agencies(query: $q) { content { code } } }""",
			Map.of("q", "tagCode:DAMAGED"), CONSORTIUM_ADMIN, List.of());

		assertThat(errorOf(result), containsString("Cannot filter on tagCode: it applies only to patron requests."));
	}

	private UUID request(String description, DataAgency borrower) {
		final var patron = patronFixture.savePatron("home");
		final var identity = patronFixture.saveIdentityAndReturn(patron, hostLms, "patron-" + description,
			true, "-", "home", borrower);

		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.description(description)
			.status(PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.build()).getId();
	}

	private void tag(UUID requestId, String tagCode, String agencyCode) {
		singleValueFrom(tagWriter.save(PatronRequestTag.builder()
			.id(randomUUID())
			.patronRequestId(requestId)
			.tagCode(tagCode)
			.authorKind(agencyCode == null ? RequestNoteAuthorKind.CONSORTIUM : RequestNoteAuthorKind.LIBRARY)
			.agencyCode(agencyCode)
			.dateCreated(Instant.now())
			.build()));
	}

	private void note(UUID requestId, String agencyCode, String body) {
		singleValueFrom(noteWriter.save(PatronRequestNote.builder()
			.id(randomUUID())
			.patronRequestId(requestId)
			.authorKind(RequestNoteAuthorKind.LIBRARY)
			.agencyCode(agencyCode)
			.body(body)
			.dateCreated(Instant.now())
			.build()));
	}

	private List<String> search(String query, List<String> roles, List<String> agencies) {
		final Map<String, Object> page = data(execute(SEARCH, Map.of("q", query), roles, agencies),
			"patronRequests");
		final List<Map<String, Object>> content = cast(page.get("content"));

		return content.stream().map(request -> (String) request.get("description")).toList();
	}

	private ExecutionResult execute(String query, Map<String, Object> variables, List<String> roles,
		List<String> agencies) {

		return graphQL.execute(ExecutionInput.newExecutionInput()
			.query(query)
			.variables(variables)
			.graphQLContext(Map.of("roles", roles, AGENCY_CODES, agencies))
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

	@SuppressWarnings("unchecked")
	private static <T> T cast(Object value) {
		assertThat(value, is(notNullValue()));
		return (T) value;
	}
}

package org.olf.dcb.graphql;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.olf.dcb.graphql.GraphQLSecurityContextCustomizer.AGENCY_CODES;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestTag;
import org.olf.dcb.core.model.RequestNoteAuthorKind;
import org.olf.dcb.request.notes.PatronRequestTagService;
import org.olf.dcb.request.notes.RequestNoteAccess;
import org.olf.dcb.request.notes.RequestNoteCaller;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.PatronRequestTagRepository;
import org.olf.dcb.storage.RequestTagRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestTagRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

/**
 * PatronRequest.tags for a page of requests is one batch, however many requests the page holds.
 */
@DcbTest
@TestInstance(PER_CLASS)
class PatronRequestTagBatchingTests {
	private static final String PAGE_WITH_TAGS = """
		{ patronRequests(pagesize: 100) { content { description tags { code label agencyCodes consortium } } } }""";

	@Inject
	private GraphQL graphQL;
	@Inject
	private PatronRequestTagService tagService;
	@Inject
	private RequestNoteAccess access;
	@Inject
	private PatronRequestTagRepository tagWriter;
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

	private DataHostLms hostLms;

	@MockBean(RequestNoteAccess.class)
	RequestNoteAccess requestNoteAccess(PatronRequestRepository patronRequestRepository,
		PostgresPatronRequestRepository patronRequestQueries) {

		return spy(new RequestNoteAccess(patronRequestRepository, patronRequestQueries));
	}

	@MockBean(PatronRequestTagService.class)
	PatronRequestTagService patronRequestTagService(PatronRequestTagRepository tagRepository,
		RequestTagRepository vocabulary, RequestNoteAccess requestNoteAccess) {

		return spy(new PatronRequestTagService(tagRepository, vocabulary, requestNoteAccess));
	}

	@BeforeAll
	void beforeAll() {
		deleteAll();

		hostLms = hostLmsFixture.createDummyHostLms("BATCH_LMS");
		final var borrower = agencyFixture.defineAgency("BATCH_B", "Borrowing Library", hostLms);
		final var stranger = agencyFixture.defineAgency("BATCH_X", "Stranger Library", hostLms);

		final var damaged = request("damaged", borrower);
		tag(damaged, "DAMAGED", "BATCH_B");
		tag(damaged, "DAMAGED", null);

		tag(request("slow", borrower), "SLOW_TO_SHIP", "BATCH_B");
		request("untagged", borrower);
		tag(request("elsewhere", stranger), "DAMAGED", "BATCH_X");
	}

	@BeforeEach
	void forgetCalls() {
		clearInvocations(tagService, access);
	}

	@AfterAll
	void deleteAll() {
		singleValueFrom(appliedTags.deleteAll());
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void aConsortiumPageOfRequestsReadsTagsInOneBatch() {
		final var tags = tagsByRequest(List.of("CONSORTIUM_ADMIN"), List.of());

		assertThat(tags.keySet(), hasSize(4));
		assertThat(tags.get("damaged"), hasSize(1));
		assertThat(tags.get("damaged").get(0), hasEntry("code", "DAMAGED"));
		assertThat(tags.get("damaged").get(0), hasEntry("label", "Damaged"));
		assertThat(tags.get("damaged").get(0), hasEntry("agencyCodes", List.of("BATCH_B")));
		assertThat(tags.get("damaged").get(0), hasEntry("consortium", true));
		assertThat(tags.get("slow").get(0), hasEntry("code", "SLOW_TO_SHIP"));
		assertThat(tags.get("untagged"), is(empty()));
		assertThat(tags.get("elsewhere").get(0), hasEntry("agencyCodes", List.of("BATCH_X")));

		verify(tagService, times(1)).tagsOfEach(argThat(ids -> ids.size() == 4));
		verify(access, times(1)).readableAmong(anyCollection(), any(RequestNoteCaller.class));
		verifyNoPerRequestReads();
	}

	@Test
	void aLibraryPageOfRequestsReadsInvolvementAndTagsInOneBatchEach() {
		final var tags = tagsByRequest(List.of("LIBRARY_ADMIN"), List.of("BATCH_B"));

		assertThat(tags.keySet(), hasSize(3));
		assertThat(tags.get("damaged").get(0), hasEntry("agencyCodes", List.of("BATCH_B")));
		assertThat(tags.get("slow").get(0), hasEntry("code", "SLOW_TO_SHIP"));
		assertThat(tags.get("untagged"), is(empty()));

		verify(access, times(1)).readableAmong(argThat(ids -> ids.size() == 3), any(RequestNoteCaller.class));
		verify(tagService, times(1)).tagsOfEach(argThat(ids -> ids.size() == 3));
		verifyNoPerRequestReads();
	}

	private void verifyNoPerRequestReads() {
		verify(tagService, never()).tagsOf(any(UUID.class));
		verify(access, never()).canRead(any(UUID.class), any(RequestNoteCaller.class));
	}

	private Map<String, List<Map<String, Object>>> tagsByRequest(List<String> roles, List<String> agencies) {
		final ExecutionResult result = graphQL.execute(ExecutionInput.newExecutionInput()
			.query(PAGE_WITH_TAGS)
			.graphQLContext(Map.of("roles", roles, AGENCY_CODES, agencies))
			.build());

		assertThat("errors: " + result.getErrors(), result.getErrors(), is(empty()));

		final Map<String, Object> data = result.getData();
		final Map<String, Object> page = cast(data.get("patronRequests"));
		final List<Map<String, Object>> content = cast(page.get("content"));

		return content.stream().collect(Collectors.toMap(
			request -> (String) request.get("description"),
			request -> cast(request.get("tags"))));
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

	@SuppressWarnings("unchecked")
	private static <T> T cast(Object value) {
		assertThat(value, is(notNullValue()));
		return (T) value;
	}
}

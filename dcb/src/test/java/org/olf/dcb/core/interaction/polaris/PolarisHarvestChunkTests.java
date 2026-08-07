package org.olf.dcb.core.interaction.polaris;

import static java.util.stream.Collectors.joining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.dataimport.job.SourceRecordImportChunk;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Exercises PolarisLmsClient.getChunk - the harvest v2 entry point.
 *
 * PolarisIngestTests covers the legacy getResources path instead, so without this class every
 * paging, completeness and checkpoint decision in the harvest is untested.
 *
 * Page size is deliberately tiny (3) so a "full page" is cheap to express.
 */
@Slf4j
@MockServerMicronautTest
@MicronautTest(transactional = false, rebuildContext = true,
	contextBuilder = org.olf.dcb.test.DcbTestContainerContextBuilder.class)
@TestInstance(PER_CLASS)
class PolarisHarvestChunkTests {
	private static final String HOST_LMS_CODE = "polaris-harvest-chunk-tests";
	private static final String HOST = "polaris-harvest-chunk-tests.com";
	private static final String BASE_URL = "https://" + HOST;

	// A second host with a page size above the Synch_BibsByIDGet limit of 50, so that one delta
	// window genuinely requires more than one batch. The main host cannot express that.
	private static final String BATCH_HOST_LMS_CODE = "polaris-harvest-batch-tests";
	private static final String BATCH_HOST = "polaris-harvest-batch-tests.com";
	private static final String BATCH_BASE_URL = "https://" + BATCH_HOST;

	private static final int PAGE_SIZE = 3;
	private static final int BATCH_PAGE_SIZE = 60;

	// Arbitrary fixed epoch millis so modification dates are deterministic and ordered by bib id.
	private static final long MOD_BASE = 1700000000000L;

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private ObjectMapper objectMapper;

	private MockServerClient mockServerClient;

	private MockPolarisFixture mockPolarisFixture;
	private MockPolarisFixture mockBatchPolarisFixture;

	private PolarisLmsClient client;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		hostLmsFixture.deleteAll();

		createPolarisHost(HOST_LMS_CODE, BASE_URL, PAGE_SIZE);
		createPolarisHost(BATCH_HOST_LMS_CODE, BATCH_BASE_URL, BATCH_PAGE_SIZE);

		mockPolarisFixture = new MockPolarisFixture(HOST, mockServerClient,
			testResourceLoaderProvider);

		mockBatchPolarisFixture = new MockPolarisFixture(BATCH_HOST, mockServerClient,
			testResourceLoaderProvider);
	}

	@BeforeEach
	void beforeEach() {
		// MockServer is shared for the whole class and expectations are unlimited by default, so
		// without a reset a page mocked by one test will satisfy the request made by another.
		mockServerClient.reset();

		mockPolarisFixture.mockPapiStaffAuthentication();
		mockBatchPolarisFixture.mockPapiStaffAuthentication();

		// rebuildContext tears the context down between tests, which kills the Netty event loop
		// behind any client held from @BeforeAll. Resolve a live one per test.
		client = (PolarisLmsClient) hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	private void createPolarisHost(String code, String baseUrl, int pageSize) {
		hostLmsFixture.createHostLms(UUID.randomUUID(), code,
			PolarisLmsClient.class, Optional.of(PolarisLmsClient.class),
			Map.ofEntries(
				Map.entry("staff-username", "test-key"),
				Map.entry("staff-password", "test-secret"),
				Map.entry("base-url", baseUrl),
				Map.entry("domain-id", "TEST"),
				Map.entry("access-id", "test-key"),
				Map.entry("access-key", "test-secret"),
				Map.entry("page-size", pageSize),
				Map.entry("logon-branch-id", "73"),
				Map.entry("logon-user-id", "1"),
				Map.entry("services", Map.of("product-id", "20", "organisation-id", "73"))));
	}

	/**
	 * Synch_BibsPagedGet pages an ID window and omits ids that do not exist, so a page can be
	 * shorter than nrecs long before the end of the catalogue. Terminating the full harvest on row
	 * count strands every bib above the first gap - delta mode can never recover them because their
	 * modification dates predate the watermark.
	 *
	 * Fails before MR-1: isLastChunk was (rows != nrecs), so 2 rows out of 3 ended the harvest.
	 */
	@Test
	void fullHarvestDoesNotEndOnAShortPageWhenTheCursorStillAdvances() {
		// Arrange - 2 rows for a page size of 3, but Polaris advanced its cursor to 30
		mockPolarisFixture.mockGetPagedBibs(0, pagedBibsResponse(30, 10, 20));

		// Act
		final var chunk = singleValueFrom(client.getChunk(Optional.empty()));

		// Assert
		assertThat("A short page mid catalogue must not end the harvest",
			chunk.isLastChunk(), is(false));

		assertThat(remoteIds(chunk), contains("10", "20"));

		final var checkpoint = checkpointOf(chunk);
		assertThat("Cursor must advance to the LastID Polaris reported, not the last row",
			checkpoint.getLastId(), is(30));
		assertThat("Still in full harvest mode", checkpoint.getStartdatemodified(), is(nullValue()));
	}

	/**
	 * End of catalogue on the full harvest: no rows at all. This is the only honest end signal when
	 * pages are ID windows.
	 */
	@Test
	void fullHarvestEndsOnAnEmptyPageAndRollsIntoDeltaMode() {
		// Arrange
		mockPolarisFixture.mockGetPagedBibs(0, pagedBibsResponse(90, 10, 20, 30));
		mockPolarisFixture.mockGetPagedBibs(90, pagedBibsResponse(90));

		// Act - first page fills, second is empty
		final var first = singleValueFrom(client.getChunk(Optional.empty()));
		final var second = singleValueFrom(client.getChunk(Optional.of(first.getCheckpoint())));

		// Assert
		assertThat(first.isLastChunk(), is(false));
		assertThat(second, is(notNullValue()));
		assertThat("An empty page ends the full harvest", second.isLastChunk(), is(true));

		final var checkpoint = checkpointOf(second);
		assertThat("Id cursor resets so the next run walks the delta window from the start",
			checkpoint.getLastId(), is(0));
		assertThat("Harvest transitions to delta mode by adopting a date watermark",
			checkpoint.getStartdatemodified(), is(notNullValue()));
		assertThat("Watermark is rewound to cover records modified during the walk",
			checkpoint.getStartdatemodified().isBefore(checkpoint.getHighestDateUpdatedSeen()),
			is(true));
	}

	/**
	 * A cursor that does not advance must end the harvest rather than spin expand() against the
	 * library's PAPI server.
	 */
	@Test
	void fullHarvestEndsWhenTheCursorFailsToAdvance() {
		// Arrange - full page of rows, but Polaris reports a cursor that has not moved past the request
		mockPolarisFixture.mockGetPagedBibs(0, pagedBibsResponse(0, 10, 20, 30));

		// Act
		final var chunk = singleValueFrom(client.getChunk(Optional.empty()));

		// Assert
		assertThat("A stalled cursor must terminate the harvest", chunk.isLastChunk(), is(true));
	}

	/**
	 * THE WEDGE. A delta window whose id page comes back empty used to emit a null node, which
	 * getChunk dropped via mapNotNull, producing an empty Mono. ReactiveJobRunnerService then ended
	 * the run without saving a checkpoint, so lastId stayed pinned and every later run repeated the
	 * same empty query. Permanently stuck, recoverable only by deleting the job_checkpoint row.
	 *
	 * Fails before MR-1: the second getChunk completes empty and singleValueFrom returns null.
	 */
	@Test
	void deltaHarvestSelfHealsWhenTheIdPageComesBackEmpty() {
		// Arrange - a full id page, then nothing left in the window
		final var startingCheckpoint = deltaCheckpoint(Instant.ofEpochMilli(MOD_BASE), 0);

		mockPolarisFixture.mockGetUpdatedBibs(0, updatedBibsResponse(10, 20, 30));
		mockPolarisFixture.mockGetBibsById("10,20,30", bibsByIdResponse(10, 20, 30));
		mockPolarisFixture.mockGetUpdatedBibs(30, updatedBibsResponse());

		// Act
		final var first = singleValueFrom(client.getChunk(Optional.of(startingCheckpoint)));
		final var second = singleValueFrom(client.getChunk(Optional.of(first.getCheckpoint())));

		// Assert
		assertThat("A full id page continues the walk", first.isLastChunk(), is(false));
		assertThat(checkpointOf(first).getLastId(), is(30));

		assertThat("An empty id page must still emit a chunk so the checkpoint is written",
			second, is(notNullValue()));
		assertThat(second.isLastChunk(), is(true));
		assertThat("No rows to fetch when no ids were returned", second.getData(), hasSize(0));

		final var checkpoint = checkpointOf(second);
		assertThat("The id cursor must reset, otherwise the harvest stays wedged",
			checkpoint.getLastId(), is(0));
		assertThat(checkpoint.getStartdatemodified(), is(notNullValue()));
	}

	/**
	 * Synch_BibsByIDGet accepts at most 50 ids. The pre-MR behaviour was .take(50), which silently
	 * discarded every id beyond the first 50 in a window - the original DCB-2191 data loss.
	 */
	@Test
	void deltaHarvestFetchesEveryIdInBatchesOfFifty() {
		// Arrange - a full page of 60 ids, requiring two Synch_BibsByIDGet calls (50 then 10)
		final var batchClient = (PolarisLmsClient) hostLmsFixture.createClient(BATCH_HOST_LMS_CODE);

		final var firstBatch = idList(IntStream.rangeClosed(1, 50).toArray());
		final var secondBatch = idList(IntStream.rangeClosed(51, 60).toArray());

		mockBatchPolarisFixture.mockGetUpdatedBibs(0,
			updatedBibsResponse(IntStream.rangeClosed(1, 60).toArray()));
		mockBatchPolarisFixture.mockGetBibsById(firstBatch,
			bibsByIdResponse(IntStream.rangeClosed(1, 50).toArray()));
		mockBatchPolarisFixture.mockGetBibsById(secondBatch,
			bibsByIdResponse(IntStream.rangeClosed(51, 60).toArray()));

		// Act
		final var chunk = singleValueFrom(batchClient.getChunk(
			Optional.of(deltaCheckpoint(Instant.ofEpochMilli(MOD_BASE), 0))));

		// Assert
		assertThat("Every id must be fetched, none truncated", chunk.getData(), hasSize(60));

		mockBatchPolarisFixture.verifyGetBibsById(firstBatch);
		mockBatchPolarisFixture.verifyGetBibsById(secondBatch);
		mockBatchPolarisFixture.verifyGetBibsByIdCalledTimes(exactly(2));

		assertThat("A full id page continues the walk", chunk.isLastChunk(), is(false));
		assertThat("Cursor is the highest id seen", checkpointOf(chunk).getLastId(), is(60));
	}

	/**
	 * Deleted bibs are omitted from Synch_BibsByIDGet, so rows are shorter than the ids requested.
	 * Completeness must still be judged on the id count, otherwise a window containing deletions
	 * looks like the end of the walk.
	 */
	@Test
	void deltaHarvestJudgesCompletenessOnIdCountNotRowCount() {
		// Arrange - 3 ids requested (a full page), only 2 bibs still exist
		mockPolarisFixture.mockGetUpdatedBibs(0, updatedBibsResponse(10, 20, 30));
		mockPolarisFixture.mockGetBibsById("10,20,30", bibsByIdResponse(10, 30));

		// Act
		final var chunk = singleValueFrom(client.getChunk(
			Optional.of(deltaCheckpoint(Instant.ofEpochMilli(MOD_BASE), 0))));

		// Assert
		assertThat("Rows short of the id count must not end the walk",
			chunk.isLastChunk(), is(false));
		assertThat(remoteIds(chunk), contains("10", "30"));
		assertThat(checkpointOf(chunk).getLastId(), is(30));
	}

	/**
	 * A structurally broken response must abort the run so it retries next schedule with the
	 * checkpoint intact. It must never resolve to an empty Mono, which would look like success and
	 * silently freeze the cursor.
	 */
	@Test
	void malformedPageErrorsRatherThanSilentlyEndingTheRun() {
		// Arrange - neither GetBibsPagedRows nor GetBibsByIDRows present
		mockPolarisFixture.mockGetPagedBibs(0, "{ \"PAPIErrorCode\": 0, \"LastID\": 10 }");

		// Act / Assert
		final var error = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
			() -> singleValueFrom(client.getChunk(Optional.empty())));

		assertThat(error, is(notNullValue()));
	}

	// ---- helpers -------------------------------------------------------------------------------

	private List<String> remoteIds(SourceRecordImportChunk chunk) {
		return chunk.getData().stream()
			.map(SourceRecord::getRemoteId)
			.toList();
	}

	private ExtendedBibsPagedGetParams checkpointOf(SourceRecordImportChunk chunk) {
		try {
			return objectMapper.readValueFromTree(chunk.getCheckpoint(),
				ExtendedBibsPagedGetParams.class);
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not read checkpoint", e);
		}
	}

	private JsonNode deltaCheckpoint(Instant startDateModified, Integer lastId) {
		try {
			return objectMapper.writeValueToTree(ExtendedBibsPagedGetParams.builder()
				.startdatemodified(startDateModified)
				.highestDateUpdatedSeen(startDateModified)
				.lastId(lastId)
				.hostCode(HOST_LMS_CODE)
				.build());
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not write checkpoint", e);
		}
	}

	private static String idList(int... bibIds) {
		return IntStream.of(bibIds)
			.mapToObj(String::valueOf)
			.collect(joining(","));
	}

	private static String pagedBibsResponse(int lastId, int... bibIds) {
		return """
			{ "PAPIErrorCode": 0, "ErrorMessage": null, "LastID": %d, "GetBibsPagedRows": [%s] }"""
			.formatted(lastId, rows(bibIds));
	}

	private static String bibsByIdResponse(int... bibIds) {
		return """
			{ "PAPIErrorCode": 0, "ErrorMessage": null, "GetBibsByIDRows": [%s] }"""
			.formatted(rows(bibIds));
	}

	private static String updatedBibsResponse(int... bibIds) {
		return """
			{ "PAPIErrorCode": 0, "ErrorMessage": null, "BibIDListRows": [%s] }"""
			.formatted(IntStream.of(bibIds)
				.mapToObj("{ \"BibliographicRecordID\": %d }"::formatted)
				.collect(joining(",")));
	}

	private static String rows(int... bibIds) {
		return IntStream.of(bibIds)
			.mapToObj(PolarisHarvestChunkTests::row)
			.collect(joining(","));
	}

	private static String row(int bibId) {
		// getChunk only reads BibliographicRecordID and ModificationDate; the MARC blob is passed
		// through untouched, so a placeholder keeps these fixtures readable.
		return """
			{ "BibliographicRecordID": %d, "IsDisplayInPAC": true,
			  "CreationDate": "/Date(%d+0000)/",
			  "FirstAvailableDate": "/Date(%d+0000)/",
			  "ModificationDate": "/Date(%d+0000)/",
			  "BibliographicRecordXML": "<marc:collection/>" }"""
			.formatted(bibId, MOD_BASE, MOD_BASE, MOD_BASE + (bibId * 1000L));
	}
}

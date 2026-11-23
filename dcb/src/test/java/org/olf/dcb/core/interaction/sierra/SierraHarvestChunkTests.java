package org.olf.dcb.core.interaction.sierra;

import static java.util.stream.Collectors.joining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
import org.olf.dcb.dataimport.job.SourceRecordImportChunk;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.SierraTestUtils.MockSierraV6Host;
import services.k_int.interaction.sierra.bibs.BibParams;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraHarvestChunkTests {
	private static final String HOST_LMS_CODE = "sierra-harvest-chunk";
	private static final String BASE_URL = "https://sierra-harvest-chunk-tests.com";
	private static final String KEY = "harvest-key";
	private static final String SECRET = "harvest-secret";
	private static final String BIBS_PATH = "/iii/sierra-api/v6/bibs";

	// getChunk asks for 500 bibs a page whatever the Host LMS says
	private static final int PAGE_SIZE = 500;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ObjectMapper objectMapper;

	private MockServerClient mockServerClient;
	private MockSierraV6Host sierra;
	private SierraLmsClient client;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE,
			Map.of("key", KEY, "secret", SECRET, "base-url", BASE_URL, "ingest", "false"));
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();

		sierra = SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, "harvest-token", 3600);

		client = (SierraLmsClient) hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	@Test
	void aWalkCarriesOnFromWhereItsLastPageEnded() {
		// Arrange
		mockPage(0, bibs(1, PAGE_SIZE));
		mockPage(PAGE_SIZE, bibs(PAGE_SIZE + 1, 10));

		// Act
		final var first = singleValueFrom(client.getChunk(Optional.empty()));
		final var last = singleValueFrom(client.getChunk(Optional.of(first.getCheckpoint())));

		// Assert
		assertThat(first.isLastChunk(), is(false));
		assertThat(last.isLastChunk(), is(true));
		assertThat(last.getData().size(), is(10));
	}

	@Test
	void aDeltaRunAsksSierraOnlyForBibsChangedSinceItsWindowOpened() {
		// Arrange
		final var deltaCheckpoint = jsonNode("""
			{ "updatedDate": { "fromDate": "2026-11-20T10:00:00" } }""");

		sierra.whenRequest(req -> req.withPath(BIBS_PATH)
				.withQueryStringParameter("updatedDate", "2026-11-20T10:00.*"))
			.respond(bibs(1, 10));

		// Act
		final var chunk = singleValueFrom(client.getChunk(Optional.of(deltaCheckpoint)));

		// Assert
		assertThat(chunk.getData().size(), is(10));
	}

	private void mockPage(int offset, HttpResponse response) {
		sierra.whenRequest(req -> req.withPath(BIBS_PATH)
				.withQueryStringParameter("offset", String.valueOf(offset)))
			.respond(response);
	}

	private static HttpResponse bibs(int firstId, int count) {
		final var entries = IntStream.range(firstId, firstId + count)
			.mapToObj(id -> """
				{ "id": "%d", "updatedDate": "2026-11-20T10:00:00Z", "deleted": false, "suppressed": false }"""
				.formatted(id))
			.collect(joining(","));

		return okJson("""
			{ "total": %d, "start": 0, "entries": [%s] }""".formatted(count, entries));
	}

	private static HttpResponse okJson(String body) {
		return response().withStatusCode(200).withBody(json(body, APPLICATION_JSON));
	}

	private Instant windowStartOf(SourceRecordImportChunk chunk) {
		return checkpointOf(chunk).getUpdatedDate().getFromDate().toInstant(ZoneOffset.UTC);
	}

	private BibParams checkpointOf(SourceRecordImportChunk chunk) {
		try {
			return objectMapper.readValueFromTree(chunk.getCheckpoint(), BibParams.class);
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read checkpoint", e);
		}
	}

	private JsonNode jsonNode(String json) {
		try {
			return objectMapper.readValue(json, JsonNode.class);
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not parse " + json, e);
		}
	}
}

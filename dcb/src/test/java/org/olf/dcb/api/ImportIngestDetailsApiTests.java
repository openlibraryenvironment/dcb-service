package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.dataimport.job.model.SourceRecord.ProcessingStatus.FAILURE;
import static org.olf.dcb.dataimport.job.model.SourceRecord.ProcessingStatus.PROCESSING_REQUIRED;
import static org.olf.dcb.dataimport.job.model.SourceRecord.ProcessingStatus.SUCCESS;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.RecordCount;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.dataimport.job.model.SourceRecord.ProcessingStatus;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.SourceRecordRepository;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DcbTest
@TestInstance(PER_CLASS)
class ImportIngestDetailsApiTests {
	private static final String ACCESS_TOKEN = "import-ingest-details-admin-token";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private SourceRecordRepository sourceRecordRepository;
	@Inject
	private HostLmsRepository hostLmsRepository;

	@BeforeAll
	void beforeAll() {
		TestStaticTokenValidator.add(ACCESS_TOKEN, "test-admin", List.of(ADMINISTRATOR));
	}

	@BeforeEach
	void beforeEach() {
		deleteAll();
	}

	@AfterAll
	void afterAll() {
		deleteAll();
	}

	@Test
	void shouldCountEveryHostLmsFromOneSharedCount() {
		// Arrange
		final var harvested = hostLmsFixture.createSierraHostLms("harvested-host-lms");
		final var notHarvested = hostLmsFixture.createSierraHostLms("not-harvested-host-lms");

		createSourceRecords(harvested, SUCCESS, 3);
		createSourceRecords(harvested, PROCESSING_REQUIRED, 2);
		createSourceRecords(harvested, FAILURE, 1);
		createBibRecords(harvested, 3);

		// Act
		final var firstRead = getAllImportIngestDetails();

		createSourceRecords(harvested, PROCESSING_REQUIRED, 4);

		final var secondRead = getAllImportIngestDetails();

		// Assert
		assertThat(detailsFor(firstRead, harvested), allOf(
			hasProperty("name", is(harvested.getName())),
			hasProperty("sourceRecordCount", is(6L)),
			hasProperty("bibRecordCount", is(3L)),
			hasProperty("processStates", containsInAnyOrder(
				stateCount("SUCCESS", 3L),
				stateCount("PROCESSING_REQUIRED", 2L),
				stateCount("FAILURE", 1L))),
			hasProperty("countedAt", notNullValue())
		));

		assertThat(detailsFor(firstRead, notHarvested), allOf(
			hasProperty("sourceRecordCount", is(0L)),
			hasProperty("bibRecordCount", is(0L))
		));

		assertThat("A repeat within the cache lifetime is served from the first count",
			detailsFor(secondRead, harvested), allOf(
				hasProperty("sourceRecordCount", is(6L)),
				hasProperty("countedAt", is(detailsFor(firstRead, harvested).getCountedAt()))
			));
	}

	@Test
	void shouldCountOneHostLmsAtTheTimeOfAsking() {
		// Arrange
		final var hostLms = hostLmsFixture.createSierraHostLms("single-host-lms");
		final var otherHostLms = hostLmsFixture.createSierraHostLms("other-host-lms");

		createSourceRecords(hostLms, SUCCESS, 2);
		createSourceRecords(hostLms, PROCESSING_REQUIRED, 1);
		createBibRecords(hostLms, 2);

		createSourceRecords(otherHostLms, SUCCESS, 5);
		createBibRecords(otherHostLms, 5);

		// Act
		final var firstRead = getImportIngestDetails(hostLms.getId());

		createSourceRecords(hostLms, SUCCESS, 1);

		final var secondRead = getImportIngestDetails(hostLms.getId());

		// Assert
		assertThat(firstRead, allOf(
			hasProperty("id", is(hostLms.getId())),
			hasProperty("sourceRecordCount", is(3L)),
			hasProperty("bibRecordCount", is(2L)),
			hasProperty("processStates", containsInAnyOrder(
				stateCount("SUCCESS", 2L),
				stateCount("PROCESSING_REQUIRED", 1L)))
		));

		assertThat(secondRead, allOf(
			hasProperty("sourceRecordCount", is(4L)),
			hasProperty("processStates", containsInAnyOrder(
				stateCount("SUCCESS", 3L),
				stateCount("PROCESSING_REQUIRED", 1L)))
		));
	}

	@Test
	void shouldWriteCountedAtAsAnIsoInstant() {
		// Arrange
		final var hostLms = hostLmsFixture.createSierraHostLms("counted-at-host-lms");

		// Act
		final Map<String, Object> details = client.toBlocking().retrieve(
			HttpRequest.GET("/hostlmss/importIngestDetails/" + hostLms.getId()).bearerAuth(ACCESS_TOKEN),
			Argument.mapOf(String.class, Object.class));

		// Assert
		assertThat(Instant.parse((String) details.get("countedAt")), notNullValue());
	}

	@Test
	void shouldNotFindUnknownHostLms() {
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> getImportIngestDetails(randomUUID()));

		assertThat(exception.getStatus(), is(NOT_FOUND));
	}

	private List<ImportIngestDetails> getAllImportIngestDetails() {
		return client.toBlocking().retrieve(
			HttpRequest.GET("/hostlmss/importIngestDetails").bearerAuth(ACCESS_TOKEN),
			Argument.listOf(ImportIngestDetails.class));
	}

	private ImportIngestDetails getImportIngestDetails(UUID hostLmsId) {
		return client.toBlocking().retrieve(
			HttpRequest.GET("/hostlmss/importIngestDetails/" + hostLmsId).bearerAuth(ACCESS_TOKEN),
			ImportIngestDetails.class);
	}

	private static ImportIngestDetails detailsFor(List<ImportIngestDetails> allDetails,
		DataHostLms hostLms) {

		return allDetails.stream()
			.filter(details -> hostLms.getId().equals(details.getId()))
			.findFirst()
			.orElseThrow();
	}

	private static org.hamcrest.Matcher<RecordCount> stateCount(String state, Long count) {
		return allOf(hasProperty("value", is(state)), hasProperty("count", is(count)));
	}

	private void createSourceRecords(DataHostLms hostLms, ProcessingStatus state, int howMany) {
		for (int i = 0; i < howMany; i++) {
			Mono.from(sourceRecordRepository.save(SourceRecord.builder()
					.hostLmsId(hostLms.getId())
					.remoteId(randomUUID().toString())
					.lastFetched(now())
					.processingState(state)
					.build()))
				.block();
		}
	}

	private void createBibRecords(DataHostLms hostLms, int howMany) {
		for (int i = 0; i < howMany; i++) {
			final var bibRecordId = randomUUID();

			bibRecordFixture.createBibRecord(bibRecordId, hostLms.getId(), randomUUID().toString(),
				clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId));
		}
	}

	private void deleteAll() {
		Flux.from(hostLmsRepository.queryAll())
			.concatMap(hostLms -> Mono.from(sourceRecordRepository.deleteAllByHostLmsId(hostLms.getId())))
			.blockLast();

		bibRecordFixture.deleteAll();
		clusterRecordFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Data
	@Serdeable
	@NoArgsConstructor
	public static class ImportIngestDetails {
		private UUID id;
		private String name;
		private Long sourceRecordCount;
		private Long bibRecordCount;
		@Nullable private List<RecordCount> processStates;
		private Instant countedAt;
	}
}

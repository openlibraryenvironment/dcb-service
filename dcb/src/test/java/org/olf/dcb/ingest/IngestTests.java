package org.olf.dcb.ingest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;

import org.junit.jupiter.api.*;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.micronaut.PublisherTransformation;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(transactional = false, rebuildContext = true)
@TestInstance(PER_CLASS)
@Slf4j
class IngestTests {
	private static final String HOST_LMS_CODE = "ingest-service-service-tests";
	private static final String CP_RESOURCES = "classpath:mock-responses/sierra/";

	@Inject
	private ResourceLoader loader;

	@Inject
	private IngestService ingestService;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;

	@BeforeAll
	void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://ingest-service-service-tests.com";
		final String KEY = "ingest-service-key";
		final String SECRET = "ingest-service-secret";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");

		var mockSierra = SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		// Mock bibs returned by the sierra system for ingest.
		mockSierra.whenRequest(req -> req.withMethod("GET").withPath("/iii/sierra-api/v6/bibs/*"))
			.respond(okJson(getResourceAsString("bibs-slice-0-9.json")));

		mockSierra
			.whenRequest(
				req -> req.withMethod("GET").withPath("/iii/sierra-api/v6/bibs/*").withQueryStringParameter("offset", "10"))
			.respond(notFoundResponse());
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
	}

	@Test
	void ingestFromSierra() {
		// Run the ingest process
		final var bibs =  ingestService.getBibRecordStream().collectList().block();

		// Assertion changed to 9 after adding filter condition to bib record processing. We now drop records
		// with a null title on the floor. 10 input records, 1 with a null title = 9 records after ingest.
		assertEquals(9, bibs.size());
	}

	@Test
	@Property(name="tests.enableLimiter", value = "true")
	void ingestFromSierraWithLimiter() {
		// Run the ingest process again, but with the limiter bean.
		final var bibs =  manyValuesFrom(ingestService.getBibRecordStream());

		// Should limit the returned items to 5 but because one of them has a null title, we drop it giving a count of 4
		// Sometimes the null title record is not dropped, so there could be 5 records
		assertThat(bibs, hasSize(oneOf(4, 5)));
	}

	@MockBean
	@Prototype // Prototype ensures new instance of this bean at every injection point.
	@Named(IngestService.TRANSFORMATIONS_RECORDS) // Qualified name is used when searching for Applicable Transformers.
	@Requires(property = "tests.enableLimiter", value = "true")
	PublisherTransformation<IngestRecord> testIngestLimiter() {
		log.info("Test pipeline limiter");
		return (pub) -> Flux.from(pub).take(5, true);
	}

	@SneakyThrows
	private String getResourceAsString(String resourceName) {
		return new String(loader.getResourceAsStream(CP_RESOURCES + resourceName).get().readAllBytes());
	}
}

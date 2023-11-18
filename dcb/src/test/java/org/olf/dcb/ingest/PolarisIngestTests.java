package org.olf.dcb.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.ingest.IngestService.TRANSFORMATIONS_RECORDS;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import services.k_int.interaction.polaris.PolarisTestUtils;
import services.k_int.micronaut.PublisherTransformation;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(transactional = false, rebuildContext = true)
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
@TestInstance(PER_CLASS)
@Slf4j
class PolarisIngestTests {
	private static final String HOST_LMS_CODE = "ingest-service-service-tests";
	private static final String CP_RESOURCES_POLARIS = "classpath:mock-responses/polaris/";

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
		final String BASE_URL = "https://ingest-service-service-tests.com";
		final String KEY = "ingest-service-key";
		final String SECRET = "ingest-service-secret";
		final String DOMAIN = "TEST";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createPAPIHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE, DOMAIN, KEY, SECRET);
		var mockPolaris = PolarisTestUtils.mockFor(mock, BASE_URL);

		// Mock bibs returned by the polaris system for ingest.
		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/string/synch/bibs/MARCXML/paged/*"))
			.respond(okJson(getResourceAsString(CP_RESOURCES_POLARIS, "bibs-slice-0-9.json")));

		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/authenticator/staff"))
			.respond(okJson(getResourceAsString(CP_RESOURCES_POLARIS, "test-staff-auth.json")));
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
	}

	@Test
	void ingestFromPolaris() {
		// Run the ingest process
		final var bibs =  ingestService.getBibRecordStream()
			.collectList()
			.block();

		// Assertion changed to 9 after adding filter condition to bib record processing. We now drop records
		// with a null title on the floor. 10 input records, 1 with a null title = 9 records after ingest.
		assertEquals(9, bibs.size());
	}

	@MockBean
	@Prototype // Prototype ensures new instance of this bean at every injection point.
	@Named(TRANSFORMATIONS_RECORDS) // Qualified name is used when searching for Applicable Transformers.
	@Requires(property = "tests.enableLimiter", value = "true")
	PublisherTransformation<IngestRecord> testIngestLimiter() {
		log.info("Test pipeline limiter");

		return (pub) -> Flux.from(pub).take(5, true);
	}

	@SneakyThrows
	private String getResourceAsString(String cp_resources, String resourceName) {
		return new String(loader.getResourceAsStream(cp_resources + resourceName).get().readAllBytes());
	}
}

package org.olf.dcb.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.test.HostLmsFixture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;
import services.k_int.interaction.polaris.PolarisTestUtils;
import services.k_int.micronaut.PublisherTransformation;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(transactional = false, rebuildContext = true)
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
@TestInstance(Lifecycle.PER_CLASS)
public class PolarisIngestTests {
	private final Logger log = LoggerFactory.getLogger(PolarisIngestTests.class);
	private static final String HOST_LMS_CODE = "ingest-service-service-tests";
	@Inject
	private ResourceLoader loader;
	@Inject
	private IngestService ingestService;
	@Inject
	private HostLmsFixture hostLmsFixture;

	private static final String CP_RESOURCES_SIERRA = "classpath:mock-responses/sierra/";
	private static final String CP_RESOURCES_POLARIS = "classpath:mock-responses/polaris/";

	private String getResourceAsString(String cp_resources, String resourceName) throws IOException {
		return new String(loader.getResourceAsStream(cp_resources + resourceName).get().readAllBytes());
	}

	@BeforeAll
	public void addFakeSierraApis(MockServerClient mock) throws IOException {
		final String BASE_URL = "https://ingest-service-service-tests.com";
		final String KEY = "ingest-service-key";
		final String SECRET = "ingest-service-secret";
		final String DOMAIN = "TEST";

		hostLmsFixture.deleteAllHostLMS();

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

	@Test
	public void ingestFromPolaris() {

		// Run the ingest process
		List<BibRecord> bibs =  ingestService.getBibRecordStream()
			.collectList()
			.block();

		// Assertion changed to 9 after adding filter condition to bib record processing. We now drop records
		// with a null title on the floor. 10 input records, 1 with a null title = 9 records after ingest.
		assertEquals(9, bibs.size());
	}

	@MockBean
	@Prototype // Prototype ensures new instance of this bean at every injection point.
	@Named(IngestService.TRANSFORMATIONS_RECORDS) // Qualified name is used when searching for Applicable Transformers.
	@Requires(property = "tests.enableLimiter", value = "true")
	PublisherTransformation<IngestRecord> testIngestLimiter() {
		log.info("Test pipeline limiter");
		return (pub) -> Flux.from(pub).take(5, true);
	}
}

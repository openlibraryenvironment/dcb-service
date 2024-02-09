package org.olf.dcb.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.polaris.PolarisTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@MicronautTest(transactional = false, rebuildContext = true)
@TestInstance(PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class PolarisIngestTests {
	private static final String HOST_LMS_CODE = "ingest-service-service-tests";
	private static final String CP_RESOURCES_POLARIS = "classpath:mock-responses/polaris/";

	private static final String BASE_URL = "https://ingest-service-service-tests.com";
	private static final String KEY = "ingest-service-key";
	private static final String SECRET = "ingest-service-secret";
	private static final String DOMAIN = "TEST";

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private IngestService ingestService;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;

	@BeforeAll
	void beforeAll(MockServerClient mock) {
		hostLmsFixture.deleteAll();

		hostLmsFixture.createPolarisHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL,
			DOMAIN, KEY, SECRET);

		var mockPolaris = PolarisTestUtils.mockFor(mock, BASE_URL);

		final var resourceLoader = testResourceLoaderProvider.forBasePath(CP_RESOURCES_POLARIS);

		// Mock bibs returned by the polaris system for ingest.
		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/string/synch/bibs/MARCXML/paged/*"))
			.respond(okJson(resourceLoader.getJsonResource("bibs-slice-0-9.json")));

		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/authenticator/staff"))
			.respond(okJson(resourceLoader.getJsonResource("test-staff-auth.json")));
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
	}

	@Test
	@Order(1)
	void ingestFromPolaris() {
		// Run the ingest process
		final var bibs =  ingestService.getBibRecordStream()
			.collectList()
			.block();

		// Assertion changed to 9 after adding filter condition to bib record processing. We now drop records
		// with a null title on the floor. 10 input records, 1 with a null title = 9 records after ingest.
		assertEquals(9, bibs.size());
	}
}

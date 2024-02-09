package org.olf.dcb.ingest;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.polaris.MockPolarisFixture;
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
class PolarisIngestTests {
	private static final String HOST_LMS_CODE = "ingest-service-service-tests";

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

	private MockPolarisFixture mockPolarisFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		hostLmsFixture.deleteAll();

		hostLmsFixture.createPolarisHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL,
			DOMAIN, KEY, SECRET);

		var mockPolaris = PolarisTestUtils.mockFor(mockServerClient, BASE_URL);

		final var resourceLoader = testResourceLoaderProvider.forBasePath(
			"classpath:mock-responses/polaris/");

		mockPolarisFixture = new MockPolarisFixture("ingest-service-service-tests.com",
			mockServerClient, testResourceLoaderProvider);

		mockPolarisFixture.mockPagedBibs();

		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/authenticator/staff"))
			.respond(okJson(resourceLoader.getJsonResource("test-staff-auth.json")));
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
	}

	@Test
	void ingestFromPolaris() {
		// Act
		final var bibs = manyValuesFrom(ingestService.getBibRecordStream());

		// Assert

		// Assertion changed to 9 after adding filter condition to bib record processing. We now drop records
		// with a null title on the floor. 10 input records, 1 with a null title = 9 records after ingest.
		assertThat(bibs, allOf(
			notNullValue(),
			hasSize(9)
		));
	}
}

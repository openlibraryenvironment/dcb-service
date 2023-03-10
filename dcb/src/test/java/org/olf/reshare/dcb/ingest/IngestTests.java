package org.olf.reshare.dcb.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.model.BibRecord;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(transactional = false, propertySources = { "classpath:configs/ingestTests.yml" })
@TestInstance(Lifecycle.PER_CLASS)
public class IngestTests {
	private static final String SIERRA_TOKEN = "test-token-for-user";

	@Inject
	ResourceLoader loader;

	@Inject
	IngestService ingestService;

	// Properties should line up with included property source for the spec.
	@Property(name = "hosts.test1.client.base-url")
	private String sierraHost;

	@Property(name = "hosts.test1.client.key")
	private String sierraUser;

	@Property(name = "hosts.test1.client.secret")
	private String sierraPass;

	private static final String CP_RESOURCES = "classpath:mock-responses/sierra/";

	private String getResourceAsString(String resourceName) throws IOException {
		return new String(loader.getResourceAsStream(CP_RESOURCES + resourceName).get().readAllBytes());
	}

	@BeforeAll
	public void addFakeSierraApis(MockServerClient mock) throws IOException {

		var mockSierra = SierraTestUtils.mockFor(mock, sierraHost).setValidCredentials(sierraUser, sierraPass, SIERRA_TOKEN,
				60);

		// Mock bibs returned by the sierra system for ingest.
		mockSierra.whenRequest(req -> req.withMethod("GET").withPath("/iii/sierra-api/v6/bibs/*"))
				.respond(okJson(getResourceAsString("bibs-slice-0-9.json")));

		mockSierra
				.whenRequest(
						req -> req.withMethod("GET").withPath("/iii/sierra-api/v6/bibs/*").withQueryStringParameter("offset", "10"))
				.respond(notFoundResponse());
		
		
	}

	@Test
	public void ingestFromSierra() {

		// Run the ingest process
		List<BibRecord> bibs =  ingestService.getBibRecordStream().collectList().block();
		
		assertEquals(10, bibs.size());

	}
}

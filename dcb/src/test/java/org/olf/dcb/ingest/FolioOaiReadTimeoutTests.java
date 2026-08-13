package org.olf.dcb.ingest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockserver.model.Delay.milliseconds;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.TEXT_XML;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.audit.ProcessAuditService;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

// Proves the whole reason FolioOaiClientConfig exists: its read-timeout override is actually
// honoured by the @Client(configuration = FolioOaiClientConfig.class) client. The global read
// timeout is forced to 1s while the FOLIO OAI response is delayed 2s. If the override is applied
// (folioTimeout = 10 min) the ingest completes; if the client fell back to the 1s global it would
// ReadTimeout and yield nothing. This is the behaviour the plaintext ingest tests can't express,
// and it guards the switch to extending DefaultHttpClientConfiguration (the Polaris pattern) --
// the historical claim was that overriding getReadTimeout on the default config "wasn't read".
@Property(name = "micronaut.http.client.read-timeout", value = "1s")
@MockServerMicronautTest
class FolioOaiReadTimeoutTests {
	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private IngestService ingestService;

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void appliesFolioReadTimeoutNotTheGlobalDefault(MockServerClient mockServerClient) {
		// Arrange
		hostLmsFixture.createFolioHostLms("slow-folio-host-lms", "https://slow-folio",
			"api-key", "oai_dc", "marc21_withholdings");

		mockServerClient
			.when(request()
				.withMethod("GET")
				.withPath("/oai")
				.withHeader("host", "slow-folio")
				.withHeader("Authorization", "api-key")
				.withQueryStringParameter("verb", "ListRecords")
				.withQueryStringParameter("metadataPrefix", "marc21_withholdings"))
			.respond(response()
				.withStatusCode(200)
				// 2s: comfortably past the 1s global read timeout, nowhere near the 10 min folio one
				.withDelay(milliseconds(2000))
				.withBody(testResourceLoaderProvider.forBasePath("classpath:mock-responses/folio/")
					.getResource("example-oai-response.xml"), TEXT_XML));

		// Act
		final List<BibRecord> ingestedBibRecords = manyValuesFrom(ingestService.getBibRecordStream()
				.transformDeferred(ProcessAuditService.withNewProcessAudit("test-ingest")));

		// Assert -- completing at all proves the 10 min read timeout was applied, not the 1s global
		assertThat(ingestedBibRecords, hasSize(2));
	}
}

package org.olf.dcb.ingest;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.TEXT_XML;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.matchers.BibRecordMatchers.hasSourceRecordId;
import static org.olf.dcb.test.matchers.BibRecordMatchers.hasSourceSystemIdFor;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.audit.ProcessAuditService;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Ingest from Koha, against a response captured verbatim from a real Koha OPAC.
 * <p>
 * The fixture keeps two things the FOLIO fixture does not exercise, both of which
 * would break ingest silently rather than loudly:
 * <ul>
 *   <li>the {@code <?xml-stylesheet?>} processing instruction Koha emits before
 *       the root element;</li>
 *   <li>an identifier of the form {@code <archiveID>:<biblionumber>}, whose
 *       trailing segment has to become the source record id - it is the id
 *       KohaHostLmsClient.getItems later calls
 *       /api/v1/biblios/{biblio_id}/items with, so a record ingested under
 *       anything else resolves to no items at all.</li>
 * </ul>
 * The two records deliberately carry <em>different</em> identifier shapes rather
 * than both matching the capture: OAI-PMH:archiveID is a free-text preference, so
 * a site may set a bare token ("KOHA-OAI-TEST") or the conformant
 * {@code oai:<domain>} form, and the latter puts two colons in the identifier.
 * Only the segment after the last one is the biblionumber.
 * The path is Koha's own, not the generic /oai: the harvest is built from
 * base-url plus /cgi-bin/koha/oai.pl.
 */
@MockServerMicronautTest
class KohaIngestTests {
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
	void shouldIngestFromKoha(MockServerClient mockServerClient) {
		// Arrange
		hostLmsFixture.createHarvestingKohaHostLms("koha-host-lms",
			"https://fake-koha-staff-interface", "https://fake-koha-opac");

		mockOaiResponse(mockServerClient, "fake-koha-opac", "example-oai-response.xml");

		// Act
		final List<BibRecord> ingestedBibRecords = manyValuesFrom(
			ingestService.getBibRecordStream()
				// processType is capped at 15 characters
				.transformDeferred(ProcessAuditService.withNewProcessAudit("koha-ingest")));

		// Assert
		assertThat(ingestedBibRecords, hasSize(2));

		assertThat(ingestedBibRecords, containsInAnyOrder(
			allOf(
				// from "KOHA-OAI-TEST:1", not the whole identifier
				hasSourceRecordId("1"),
				hasSourceSystemIdFor(hostLmsFixture.findByCode("koha-host-lms"))
			),
			allOf(
				// from "oai:koha-001-a.vhosts.k-int.com:2" - the last segment, so
				// changing OAI-PMH:archiveID cannot change which id DCB harvests under
				hasSourceRecordId("2"),
				hasSourceSystemIdFor(hostLmsFixture.findByCode("koha-host-lms"))
			)
		));
	}

	private void mockOaiResponse(MockServerClient mockServerClient,
		String expectedHost, String responsePath) {

		mockServerClient
			.when(request()
				.withMethod("GET")
				.withPath("/cgi-bin/koha/oai.pl")
				.withHeader("host", expectedHost)
				.withQueryStringParameter("verb", "ListRecords")
				.withQueryStringParameter("metadataPrefix", "marcxml")
			)
			.respond(response()
				.withStatusCode(200)
				.withBody(testResourceLoaderProvider.forBasePath(
						"classpath:mock-responses/koha/")
					.getResource(responsePath), TEXT_XML));
	}
}

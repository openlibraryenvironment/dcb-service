package org.olf.dcb.ingest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.TEXT_XML;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.matchers.BibRecordMatchers.hasSourceRecordId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
class FolioIngestTests {
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
	void shouldIngestFromFolio(MockServerClient mockServerClient) {
		// Arrange
		hostLmsFixture.createFolioHostLms("folio-host-lms", "https://fake-folio",
			"api-key", "oai_dc", "marc21_withholdings");

		mockOaiResponse(mockServerClient, "fake-folio", "example-oai-response.xml");

		// Act
		final var ingestedBibRecords = manyValuesFrom(ingestService.getBibRecordStream());

		// Assert
		assertThat(ingestedBibRecords, hasSize(1));

		assertThat(ingestedBibRecords, containsInAnyOrder(
			allOf(
				hasSourceRecordId("087b84b3-fe04-4d41-bfa5-ac0d85980d62")
			)
		));
	}

	@Test
	void shouldNotIngestInstanceWithInvalidIdentifier(MockServerClient mockServerClient) {
		// Arrange
		hostLmsFixture.createFolioHostLms("invalid-folio-host-lms", "https://invalid-folio",
			"api-key", "oai_dc", "marc21_withholdings");

		mockOaiResponse(mockServerClient, "invalid-folio",
			"invalid-identifier-oai-response.xml");

		// Act
		final var ingestedBibRecords = manyValuesFrom(ingestService.getBibRecordStream());

		// Assert
		assertThat(ingestedBibRecords, empty());
	}

	private void mockOaiResponse(MockServerClient mockServerClient,
		String expectedHost, String responsePath) {

		mockServerClient
			.when(request()
				.withMethod("GET")
				.withPath("/oai")
				.withHeader("host", expectedHost)
				.withQueryStringParameter("verb", "ListRecords")
				.withQueryStringParameter("metadataPrefix",
					"marc21_withholdings")
				.withQueryStringParameter("apikey", "api-key"))
			.respond(response()
				.withStatusCode(200)
				.withBody(testResourceLoaderProvider.forBasePath(
						"classpath:mock-responses/folio/")
					.getResource(responsePath), TEXT_XML));
	}
}
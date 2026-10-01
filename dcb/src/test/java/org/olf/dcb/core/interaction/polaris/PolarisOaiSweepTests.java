package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.TEXT_XML;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.verify.VerificationTimes;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.dataimport.job.SourceRecordService;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.dataimport.job.model.SourceRecord.ProcessingStatus;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.SourceRecordRepository;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The provider here is synthetic and shaped by the OAI-PMH spec, not captured from Polaris. Each
 * test uses its own host name because expectations outlive a single test.
 */
@MockServerMicronautTest
class PolarisOaiSweepTests {
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private SourceRecordRepository sourceRecordRepository;
	@Inject
	private HostLmsRepository hostLmsRepository;
	@Inject
	private BibRepository bibRepository;
	@Inject
	private HostLmsService hostLmsService;

	private SourceRecordService sourceRecordService;

	@BeforeEach
	void beforeEach() {
		// Built by hand: its package is @Requires(notEnv = TEST), so the context never provides it.
		// The sweep uses neither the job runner, the concurrency groups nor the lock.
		sourceRecordService = new SourceRecordService(hostLmsService, sourceRecordRepository,
			null, null, null, bibRepository);

		cleanUp();
	}

	@AfterEach
	void afterEach() {
		cleanUp();
	}

	@Test
	void shouldFetchOnlyTheRecordsDcbIsMissing(MockServerClient mockServerClient) {
		// Arrange
		final var host = "polaris-missing";
		mockTwoPagesOfIdentifiers(mockServerClient, host);
		mockGetRecord(mockServerClient, host, "oai:polaris:2");
		mockGetRecord(mockServerClient, host, "oai:polaris:4");

		final var source = harvestingPolaris(host);

		// Act - one bib held from each harvest, so both remote id shapes count as held
		final var recovered = manyValuesFrom(source.findMissingRecords(Flux.just("oai:polaris:1", "3")));

		// Assert
		assertThat(recovered.stream().map(SourceRecord::getRemoteId).toList(),
			containsInAnyOrder("oai:polaris:2", "oai:polaris:4"));

		mockServerClient.verify(request().withHeader("host", host)
			.withQueryStringParameter("verb", "GetRecord"), VerificationTimes.exactly(2));
	}

	@Test
	void shouldFindHeldBibsTheProviderNoLongerListsAsLive(MockServerClient mockServerClient) {
		// Arrange
		final var host = "polaris-vanished";
		mockTwoPagesOfIdentifiers(mockServerClient, host);

		final var source = harvestingPolaris(host);

		// Act - 5 is listed as deleted, 6 is not listed at all
		final var found = singleValueFrom(source.findVanishedRecords(
			Flux.just("1", "2", "5", "6"),
			Flux.just("oai:polaris:1", "oai:polaris:5", "5", "oai:polaris:6")));

		// Assert
		assertThat(found.held(), is(4L));
		assertThat(found.vanished(), is(2L));
		assertThat(found.sample(), containsInAnyOrder("5", "6"));

		final var deletions = manyValuesFrom(found.deletions());

		assertThat(deletions.stream().map(SourceRecord::getRemoteId).toList(),
			containsInAnyOrder("oai:polaris:5", "5", "oai:polaris:6"));
	}

	@Test
	void shouldDeleteTheSameBibThePapiHarvestCreated(MockServerClient mockServerClient) {
		// Arrange
		final var host = "polaris-deletion-keys";
		mockTwoPagesOfIdentifiers(mockServerClient, host);

		final var source = harvestingPolaris(host);
		final var papi = (PolarisLmsClient) hostLmsFixture.createClient(host);

		final var found = singleValueFrom(source.findVanishedRecords(
			Flux.just("6"), Flux.just("6", "oai:polaris:6")));

		// Act
		final List<IngestRecord> converted = manyValuesFrom(found.deletions()
			.concatMap(source::convertSourceToIngestRecord));

		// Assert
		final var papiBib = papi.initIngestRecordBuilder(PolarisLmsClient.BibsPagedRow.builder()
			.BibliographicRecordID(6)
			.IsDisplayInPAC(true)
			.build()).build();

		assertThat(converted.stream().map(IngestRecord::getDeleted).toList(), everyItem(is(true)));
		assertThat(converted.stream().map(IngestRecord::getUuid).toList(), everyItem(is(papiBib.getUuid())));
	}

	@Test
	void shouldFailRatherThanTrustAnIncompleteWalk(MockServerClient mockServerClient) {
		// Arrange
		final var host = "polaris-incomplete";
		mockIdentifiersPage(mockServerClient, host, null, "page-2", "1", "2");

		mockServerClient
			.when(request().withHeader("host", host)
				.withQueryStringParameter("verb", "ListIdentifiers")
				.withQueryStringParameter("resumptionToken", "page-2"))
			.respond(response().withStatusCode(500));

		final var source = harvestingPolaris(host);

		// Act
		final var error = assertThrows(HttpClientResponseException.class,
			() -> singleValueFrom(source.findVanishedRecords(
				Flux.just("1", "2", "3"), Flux.just("oai:polaris:3"))));

		// Assert - the first page was read, so it is the second page's failure that stops the sweep
		assertThat(error.getStatus().getCode(), is(500));

		mockServerClient.verify(request().withHeader("host", host)
			.withQueryStringParameter("resumptionToken", "page-2"), VerificationTimes.once());
	}

	@Test
	void shouldOnlyReportWhenNotAskedToApply(MockServerClient mockServerClient) {
		// Arrange
		final var host = "polaris-report-only";
		final var hostLms = tenHeldBibsOfWhichOneHasVanished(mockServerClient, host);

		// Act
		singleValueFrom(sourceRecordService.sweepVanished(harvestingSource(host), hostLms.getId(), false));

		// Assert
		final var report = sourceRecordService.getReconcileStatus();

		assertThat(report.get("held"), is(10L));
		assertThat(report.get("vanished"), is(1L));
		assertThat(report.get("sample"), is(List.of("10")));
		assertThat(report, not(hasKey("deletionsQueued")));

		assertThat(storedRecord(hostLms, "oai:polaris:10").getProcessingState(), is(ProcessingStatus.SUCCESS));
	}

	@Test
	void shouldReplaceVanishedRecordsWithDeletionsWhenAskedToApply(MockServerClient mockServerClient) {
		// Arrange
		final var host = "polaris-apply";
		final var hostLms = tenHeldBibsOfWhichOneHasVanished(mockServerClient, host);

		// Act
		singleValueFrom(sourceRecordService.sweepVanished(harvestingSource(host), hostLms.getId(), true));

		// Assert
		assertThat(sourceRecordService.getReconcileStatus().get("deletionsQueued"), is(1L));

		final var replaced = storedRecord(hostLms, "oai:polaris:10");

		assertThat(replaced.getProcessingState(), is(ProcessingStatus.PROCESSING_REQUIRED));
		assertThat(replaced.getSourceRecordData().get("header").get("status").getStringValue(), is("deleted"));

		assertThat(storedRecord(hostLms, "oai:polaris:9").getProcessingState(), is(ProcessingStatus.SUCCESS));
	}

	@Test
	void shouldRefuseToDeleteMoreThanASmallShareOfAHost(MockServerClient mockServerClient) {
		// Arrange
		final var host = "polaris-refused";
		final var hostLms = heldBibs(host, 10);
		mockIdentifiersPage(mockServerClient, host, null, null, "1", "2", "3", "4", "5");

		// Act
		singleValueFrom(sourceRecordService.sweepVanished(harvestingSource(host), hostLms.getId(), true));

		// Assert
		final var report = sourceRecordService.getReconcileStatus();

		assertThat(report.get("vanished"), is(5L));
		assertThat(report, hasKey("refused"));
		assertThat(report, not(hasKey("deletionsQueued")));

		assertThat(storedRecord(hostLms, "oai:polaris:10").getProcessingState(), is(ProcessingStatus.SUCCESS));
	}

	private DataHostLms tenHeldBibsOfWhichOneHasVanished(MockServerClient mockServerClient, String host) {
		final var hostLms = heldBibs(host, 10);
		mockIdentifiersPage(mockServerClient, host, null, null, "1", "2", "3", "4", "5", "6", "7", "8", "9");
		return hostLms;
	}

	private DataHostLms heldBibs(String host, int count) {
		final var hostLms = hostLmsFixture.createHarvestingPolarisHostLms(host, "https://" + host);

		for (int bibId = 1; bibId <= count; bibId++) {
			bibRecordFixture.createBibRecord(UUID.randomUUID(), hostLms.getId(), String.valueOf(bibId), null);

			singleValueFrom(sourceRecordRepository.save(SourceRecord.builder()
				.hostLmsId(hostLms.getId())
				.remoteId("oai:polaris:" + bibId)
				.lastFetched(Instant.now())
				.processingState(ProcessingStatus.SUCCESS)
				.sourceRecordData(JsonNode.createObjectNode(Map.of("header", JsonNode.createObjectNode(
					Map.of("identifier", JsonNode.createStringNode("oai:polaris:" + bibId))))))
				.build()));
		}

		return hostLms;
	}

	private SourceRecord storedRecord(DataHostLms hostLms, String remoteId) {
		return singleValueFrom(sourceRecordRepository.getById(SourceRecord.builder()
			.hostLmsId(hostLms.getId())
			.remoteId(remoteId)
			.lastFetched(Instant.now())
			.build()
			.getId()));
	}

	private PolarisOaiPmhIngestSource harvestingPolaris(String host) {
		hostLmsFixture.createHarvestingPolarisHostLms(host, "https://" + host);
		return harvestingSource(host);
	}

	private PolarisOaiPmhIngestSource harvestingSource(String host) {
		return (PolarisOaiPmhIngestSource) hostLmsFixture.getIngestSource(host);
	}

	private void mockTwoPagesOfIdentifiers(MockServerClient mockServerClient, String host) {
		mockIdentifiersPage(mockServerClient, host, null, "page-2", "1", "2", "3");
		mockIdentifiersPage(mockServerClient, host, "page-2", null, "4", "deleted:5");
	}

	// An id written "deleted:5" is listed with status="deleted"
	private void mockIdentifiersPage(MockServerClient mockServerClient, String host,
		String requestedWith, String nextToken, String... ids) {

		final var headers = Arrays.stream(ids)
			.map(id -> id.startsWith("deleted:")
				? "<header status=\"deleted\"><identifier>oai:polaris:%s</identifier><datestamp>2026-09-01T00:00:00Z</datestamp></header>"
					.formatted(id.substring("deleted:".length()))
				: "<header><identifier>oai:polaris:%s</identifier><datestamp>2026-09-01T00:00:00Z</datestamp></header>"
					.formatted(id))
			.collect(Collectors.joining());

		final var token = nextToken == null
			? "<resumptionToken completeListSize=\"5\" cursor=\"3\"/>"
			: "<resumptionToken completeListSize=\"5\" cursor=\"0\">%s</resumptionToken>".formatted(nextToken);

		final var request = request().withMethod("GET")
			.withPath("/polaris.oaipmh.dataprovider/polaris/bibliographic")
			.withHeader("host", host)
			.withQueryStringParameter("verb", "ListIdentifiers");

		mockServerClient
			.when(requestedWith == null
				? request.withQueryStringParameter("metadataPrefix", "marc21")
				: request.withQueryStringParameter("resumptionToken", requestedWith))
			.respond(response().withStatusCode(200).withBody(
				oaiResponse("ListIdentifiers", "<ListIdentifiers>%s%s</ListIdentifiers>".formatted(headers, token)), TEXT_XML));
	}

	private void mockGetRecord(MockServerClient mockServerClient, String host, String identifier) {
		mockServerClient
			.when(request().withMethod("GET")
				.withPath("/polaris.oaipmh.dataprovider/polaris/bibliographic")
				.withHeader("host", host)
				.withQueryStringParameter("verb", "GetRecord")
				.withQueryStringParameter("identifier", identifier)
				.withQueryStringParameter("metadataPrefix", "marc21"))
			.respond(response().withStatusCode(200).withBody(oaiResponse("GetRecord", """
				<GetRecord><record><header><identifier>%s</identifier><datestamp>2026-09-01T00:00:00Z</datestamp></header>
				<metadata><record xmlns="http://www.loc.gov/MARC21/slim">
				<leader>00242nam a22000857a 4500</leader>
				<datafield tag="245" ind1=" " ind2=" "><subfield code="a">Recovered title</subfield></datafield>
				</record></metadata></record></GetRecord>""".formatted(identifier)), TEXT_XML));
	}

	private static String oaiResponse(String verb, String body) {
		return """
			<?xml version="1.0" encoding="UTF-8"?>
			<OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
			<responseDate>2026-10-01T00:00:00Z</responseDate>
			<request verb="%s" metadataPrefix="marc21">https://polaris.example.org/polaris.oaipmh.dataprovider/polaris/bibliographic</request>
			%s
			</OAI-PMH>""".formatted(verb, body);
	}

	private void cleanUp() {
		bibRecordFixture.deleteAll();
		manyValuesFrom(Flux.from(hostLmsRepository.queryAll())
			.concatMap(hostLms -> sourceRecordRepository.deleteAllByHostLmsId(hostLms.getId())));
		hostLmsFixture.deleteAll();
	}
}

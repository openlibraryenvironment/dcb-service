package org.olf.dcb.ingest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.TEXT_XML;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.koha.KohaOaiPmhIngestSource;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * A Koha harvest through the source-record import, which is the harvest that runs: IngestJob
 * replaces IngestService. From 25.11.05 and 26.05 Koha withholds an OpacSuppression record and
 * lists only its header, marked deleted, so the deletion is the only signal DCB gets.
 */
@MockServerMicronautTest
class KohaSourceRecordIngestTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-withheld";
	private static final String KOHA_OPAC_HOST = "fake-koha-opac-withheld";

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordService bibRecordService;
	@Inject
	private BibRepository bibRepository;
	@Inject
	private R2dbcReactorTransactionOperations transactionOperations;

	private UUID hostLmsId;
	private KohaOaiPmhIngestSource source;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		bibRecordFixture.deleteAll();
		clusterRecordFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsId = hostLmsFixture.createHarvestingKohaHostLms(KOHA_HOST_LMS_CODE,
			"https://fake-koha-staff-withheld", "https://" + KOHA_OPAC_HOST).getId();
		source = (KohaOaiPmhIngestSource) hostLmsFixture.getIngestSource(KOHA_HOST_LMS_CODE);

		mockServerClient
			.when(request()
				.withMethod("GET")
				.withPath("/cgi-bin/koha/oai.pl")
				.withHeader("host", KOHA_OPAC_HOST)
				.withQueryStringParameter("verb", "ListRecords"))
			.respond(response()
				.withStatusCode(200)
				.withBody(testResourceLoaderProvider.forBasePath("classpath:mock-responses/koha/")
					.getResource("withheld-oai-response.xml"), TEXT_XML));
	}

	@Test
	void shouldKeepAWithheldRecordInTheHarvest() {
		final var harvested = harvest();

		assertThat(harvested.keySet(), containsInAnyOrder(
			"KOHA-OAI-TEST:30", "KOHA-OAI-TEST:31", "KOHA-OAI-TEST:32"));
		assertThat(harvested.get("KOHA-OAI-TEST:32").getSourceRecordData()
			.get("header").get("status").getStringValue(), is("deleted"));
	}

	@Test
	void shouldRemoveTheBibsOfRecordsKohaSuppressesOrWithholds() {
		final var ingested = harvest().values().stream()
			.map(record -> singleValueFrom(source.convertSourceToIngestRecord(record)))
			.collect(Collectors.toMap(IngestRecord::getSourceRecordId, Function.identity()));

		// Marked for what they are: a header-only record would also be dropped for having no title
		assertThat(ingested.get("31").getSuppressFromDiscovery(), is(true));
		assertThat(ingested.get("32").getDeleted(), is(true));
		assertThat(ingested.get("30").getSuppressFromDiscovery(), is(false));
		assertThat(ingested.get("30").getDeleted(), is(false));

		// Both were harvested and shared before the library hid them
		bibRecordFixture.createBibRecord(ingested.get("31").getUuid(), hostLmsId, "31", null);
		bibRecordFixture.createBibRecord(ingested.get("32").getUuid(), hostLmsId, "32", null);

		ingested.values().forEach(this::process);

		assertThat(bib(ingested.get("30")), is(notNullValue()));
		assertThat("942$n = 1 in the full record", bib(ingested.get("31")), is(nullValue()));
		assertThat("Koha withheld the record", bib(ingested.get("32")), is(nullValue()));
	}

	private Map<String, SourceRecord> harvest() {
		final Collection<SourceRecord> data = singleValueFrom(source.getChunk(Optional.empty())).getData();

		return data.stream().collect(Collectors.toMap(SourceRecord::getRemoteId, Function.identity()));
	}

	private void process(IngestRecord ingestRecord) {
		Mono.from(transactionOperations.withTransactionMono(status -> bibRecordService.process(ingestRecord)))
			.block();
	}

	private BibRecord bib(IngestRecord ingestRecord) {
		return Flux.from(bibRepository.findById(ingestRecord.getUuid())).blockFirst();
	}
}

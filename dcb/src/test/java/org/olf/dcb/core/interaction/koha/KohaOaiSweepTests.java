package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.TEXT_XML;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.verify.VerificationTimes;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Koha decides each record's status as it lists it, so a change to OpacSuppression,
 * OpacHiddenItems or OpacHiddenItemsHidesRecord shows in ListIdentifiers without moving any
 * timestamp an incremental harvest would see.
 */
@MockServerMicronautTest
class KohaOaiSweepTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-oai-sweep";
	private static final String KOHA_OPAC_HOST = "fake-koha-opac-sweep";
	private static final String OAI_PATH = "/cgi-bin/koha/oai.pl";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockServerClient mockServerClient;
	private KohaOaiPmhIngestSource source;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createHarvestingKohaHostLms(KOHA_HOST_LMS_CODE,
			"https://fake-koha-staff-sweep", "https://" + KOHA_OPAC_HOST);
		source = (KohaOaiPmhIngestSource) hostLmsFixture.getIngestSource(KOHA_HOST_LMS_CODE);
	}

	@Test
	void shouldFindHeldBibsKohaNowWithholds() {
		// 4 is listed as deleted: hidden by a preference changed after it was harvested
		mockIdentifiers("1", "2", "3", "deleted:4");

		// 5 is not listed at all
		final var found = singleValueFrom(source.findVanishedRecords(
			Flux.just("1", "2", "4", "5"),
			Flux.just("KOHA-OAI-TEST:1", "KOHA-OAI-TEST:4", "KOHA-OAI-TEST:5")));

		assertThat(found.held(), is(4L));
		assertThat(found.vanished(), is(2L));
		assertThat(found.sample(), containsInAnyOrder("4", "5"));

		final var deletions = manyValuesFrom(found.deletions());

		assertThat(deletions.stream().map(SourceRecord::getRemoteId).toList(),
			containsInAnyOrder("KOHA-OAI-TEST:4", "KOHA-OAI-TEST:5"));
	}

	@Test
	void shouldRemoveTheSameBibTheHarvestCreated() {
		mockIdentifiers("1");

		final var found = singleValueFrom(source.findVanishedRecords(
			Flux.just("4"), Flux.just("KOHA-OAI-TEST:4")));

		final var deletion = singleValueFrom(found.deletions()
			.concatMap(source::convertSourceToIngestRecord));

		assertThat(deletion.getDeleted(), is(true));
		assertThat(deletion.getSourceRecordId(), is("4"));
	}

	@Test
	void shouldFetchOnlyRecordsKohaShowsThatDcbLacks() {
		// 2 is live again after a preference change, and DCB dropped it while it was hidden
		mockIdentifiers("1", "2", "3", "deleted:4");
		mockGetRecord("KOHA-OAI-TEST:2");

		final var recovered = manyValuesFrom(source.findMissingRecords(
			Flux.just("KOHA-OAI-TEST:1", "KOHA-OAI-TEST:3")));

		assertThat(recovered.stream().map(SourceRecord::getRemoteId).toList(),
			containsInAnyOrder("KOHA-OAI-TEST:2"));

		mockServerClient.verify(request().withHeader("host", KOHA_OPAC_HOST)
			.withQueryStringParameter("verb", "GetRecord"), VerificationTimes.once());
	}

	@Test
	void shouldReadTheBiblionumberFromEitherShapeOfId() {
		assertThat(KohaOaiPmhIngestSource.biblionumberOf("KOHA-OAI-TEST:12345").getAsInt(), is(12345));
		assertThat(KohaOaiPmhIngestSource.biblionumberOf("12345").getAsInt(), is(12345));
		assertThat(KohaOaiPmhIngestSource.biblionumberOf("KOHA-OAI-TEST:abc").isPresent(), is(false));
	}

	// An id written "deleted:4" is listed with status="deleted"
	private void mockIdentifiers(String... ids) {
		final var headers = Arrays.stream(ids)
			.map(id -> id.startsWith("deleted:")
				? "<header status=\"deleted\"><identifier>KOHA-OAI-TEST:%s</identifier><datestamp>2026-10-01T00:00:00Z</datestamp></header>"
					.formatted(id.substring("deleted:".length()))
				: "<header><identifier>KOHA-OAI-TEST:%s</identifier><datestamp>2026-10-01T00:00:00Z</datestamp></header>"
					.formatted(id))
			.collect(Collectors.joining());

		mockServerClient
			.when(request().withMethod("GET")
				.withPath(OAI_PATH)
				.withHeader("host", KOHA_OPAC_HOST)
				.withQueryStringParameter("verb", "ListIdentifiers")
				.withQueryStringParameter("metadataPrefix", "marcxml"))
			.respond(response().withStatusCode(200).withBody(
				oaiResponse("ListIdentifiers", "<ListIdentifiers>%s</ListIdentifiers>".formatted(headers)), TEXT_XML));
	}

	private void mockGetRecord(String identifier) {
		mockServerClient
			.when(request().withMethod("GET")
				.withPath(OAI_PATH)
				.withHeader("host", KOHA_OPAC_HOST)
				.withQueryStringParameter("verb", "GetRecord")
				.withQueryStringParameter("identifier", identifier)
				.withQueryStringParameter("metadataPrefix", "marcxml"))
			.respond(response().withStatusCode(200).withBody(oaiResponse("GetRecord", """
				<GetRecord><record><header><identifier>%s</identifier><datestamp>2026-10-01T00:00:00Z</datestamp></header>
				<metadata><record xmlns="http://www.loc.gov/MARC21/slim">
				<leader>00242nam a22000857a 4500</leader>
				<datafield tag="245" ind1=" " ind2=" "><subfield code="a">Visible again</subfield></datafield>
				</record></metadata></record></GetRecord>""".formatted(identifier)), TEXT_XML));
	}

	private static String oaiResponse(String verb, String body) {
		return """
			<?xml version="1.0" encoding="UTF-8"?>
			<OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
			<responseDate>2026-10-22T00:00:00Z</responseDate>
			<request verb="%s" metadataPrefix="marcxml">https://fake-koha-opac-sweep/cgi-bin/koha/oai.pl</request>
			%s
			</OAI-PMH>""".formatted(verb, body);
	}
}

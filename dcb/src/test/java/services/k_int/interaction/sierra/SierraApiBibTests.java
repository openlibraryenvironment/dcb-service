package services.k_int.interaction.sierra;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.IdentifierGenerator.generateNumericLocalIdAsString;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraBibsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.interaction.sierra.bibs.BibResult;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraApiBibTests {
	private static final String HOST_LMS_CODE = "sierra-bib-api-tests";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;

	private SierraBibsAPIFixture sierraBibsAPIFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final var host = "bib-api-tests.com";
		final var token = "test-token";
		final var baseUrl = "https://" + host;
		final var key = "bib-key";
		final var secret = "bib-secret";

		SierraTestUtils.mockFor(mockServerClient, baseUrl)
			.setValidCredentials(key, secret, token, 3600);

		sierraBibsAPIFixture = sierraApiFixtureProvider.bibs(mockServerClient, host);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, key, secret, baseUrl, "item");
	}

	@Test
	void shouldGetSinglePageOfBibs() {
		// Arrange
		final var firstBibId = generateNumericLocalIdAsString();
		final var secondBibId = generateNumericLocalIdAsString();
		final var thirdBibId = generateNumericLocalIdAsString();

		sierraBibsAPIFixture.createGetBibsMockWithQueryStringParameters(
			BibResult.builder()
				.id(firstBibId)
				.build(),
			BibResult.builder()
				.id(secondBibId)
				.build(),
			BibResult.builder()
				.id(thirdBibId)
				.build());

		final var sierraApiClient = hostLmsFixture.createLowLevelSierraClient(HOST_LMS_CODE);

		// Act
		final var response = singleValueFrom(sierraApiClient.bibs(3, 1, "null",
			"null", null, false, null, false,
			List.of("a")));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.getClass(), is(BibResultSet.class));
		assertThat(response.total(), is(3));

		final var bibIds = response.entries().stream().map(BibResult::id).toList();

		assertThat(bibIds, contains(firstBibId, secondBibId, thirdBibId));
	}

	@Test
	void resumesDeltaHarvestAtNextOffsetWithOriginalDateFilter() {
		final var since = "2026-11-20T10:00:35";
		final var checkpoint = JsonNode.from(Map.of("offset", 0, "updatedDate", Map.of("fromDate", since)));
		final var firstPageIds = IntStream.rangeClosed(1, 500).mapToObj(Integer::toString).toList();
		sierraBibsAPIFixture.mockHarvestPage(0, since, firstPageIds);
		sierraBibsAPIFixture.mockHarvestPage(500, since, List.of("501"));

		final var client = (SierraLmsClient) hostLmsFixture.createClient(HOST_LMS_CODE);
		final var first = singleValueFrom(client.getChunk(Optional.of(checkpoint)));
		final var next = singleValueFrom(client.getChunk(Optional.of(first.getCheckpoint())));

		assertThat(next.getData().stream().map(SourceRecord::getRemoteId).toList(), contains("501"));
	}

	@Test
	void shouldCreateBib() {
		// Arrange
		final var fixedFields = Map.of(31, FixedField.builder().label("suppress").value("n").build());

		final var bibPatch = BibPatch.builder()
			.fixedFields(fixedFields)
			.authors(List.of("John Smith"))
			.titles(List.of("The Book of John"))
			.build();

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916922);

		final var sierraApiClient = hostLmsFixture.createLowLevelSierraClient(HOST_LMS_CODE);

		// Act
		var response = singleValueFrom(sierraApiClient.bibs(bibPatch));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.getClass(), is(LinkResult.class));
		assertThat(response.getLink(), is("https://sandbox.iii.com/iii/sierra-api/v6/bibs/7916922"));
	}
}

package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Availability for several clusters in one request.
 *
 * <p>The endpoint exists so the fan-out has a bound. A caller looping over
 * {@code /items/availability} makes N independent requests, and this service cannot see that
 * they are one page of one reading list — so it cannot limit them, cannot dedupe them and
 * cannot report on them together.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class BatchLiveAvailabilityApiTests {
	private static final String CATALOGUING_HOST_LMS_CODE = "batch-availability-cataloguing";
	private static final String CIRCULATING_HOST_LMS_CODE = "batch-availability-circulating";

	private static final String SUPPLYING_AGENCY_CODE = "batch-test-agency";
	private static final String LOCATION_CODE = "ab6";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private ConsortiumFixture consortiumFixture;
	@Inject
	private LiveAvailabilityApiClient liveAvailabilityApiClient;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mockServerClient) {
		final var token = "test-token";
		final var host = "batch-availability-api-tests.com";
		final var baseUrl = "https://" + host;
		final var key = "batch-availability-key";
		final var secret = "batch-availability-secret";

		SierraTestUtils.mockFor(mockServerClient, baseUrl)
			.setValidCredentials(key, secret, token, 3600);

		sierraItemsAPIFixture = sierraApiFixtureProvider.items(mockServerClient, host);

		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms(CATALOGUING_HOST_LMS_CODE, key, secret, baseUrl, "item");
		hostLmsFixture.createSierraHostLms(CIRCULATING_HOST_LMS_CODE, key, secret, baseUrl, "item");
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
		agencyFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		consortiumFixture.deleteAll();
		consortiumFixture.createConsortium();

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, LOCATION_CODE, SUPPLYING_AGENCY_CODE);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, "Batch Test College",
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			CIRCULATING_HOST_LMS_CODE, 999, 999, "BKM");
	}

	@Test
	void checksEveryClusterAskedAbout() {
		final var first = clusterHolding("100001", "1000001");
		final var second = clusterHolding("100002", "1000002");

		final var report = liveAvailabilityApiClient
			.getBatchAvailabilityReport(List.of(first, second));

		assertThat(report.getResults().size(), is(2));
		assertTrue(report.getFailures().isEmpty(), "nothing should have gone wrong");
	}

	@Test
	void keepsTheOrderTheyWereAskedFor() {
		// A reading list that reorders itself while its rows fill in is worse than one that
		// fills in slowly. flatMapSequential rather than flatMap is what holds this.
		final var first = clusterHolding("100003", "1000003");
		final var second = clusterHolding("100004", "1000004");
		final var third = clusterHolding("100005", "1000005");

		final var report = liveAvailabilityApiClient
			.getBatchAvailabilityReport(List.of(third, first, second));

		assertEquals(List.of(third, first, second),
			report.getResults().stream().map(AvailabilityResponse::getClusteredBibId).toList());
	}

	@Test
	void aClusterNobodyHoldsIsAFailureRatherThanAFailedBatch() {
		// The property that matters: one unreachable cluster must not cost a patron the
		// other rows of their list. A single-cluster caller gets a 400 and nothing else.
		final var real = clusterHolding("100006", "1000006");
		final var missing = randomUUID();

		final var report = liveAvailabilityApiClient
			.getBatchAvailabilityReport(List.of(real, missing));

		assertThat(report.getResults().size(), is(1));
		assertThat(report.getResults().getFirst().getClusteredBibId(), is(real));
		assertThat(report.getFailures().size(), is(1));
		assertThat(report.getFailures().getFirst().getClusteredBibId(), is(missing));
	}

	@Test
	void theSameClusterTwiceIsAskedAboutOnce() {
		// A duplicate in a caller's list is their mistake and must not become two fan-outs
		// into a member library.
		final var only = clusterHolding("100007", "1000007");

		final var report = liveAvailabilityApiClient
			.getBatchAvailabilityReport(List.of(only, only, only));

		assertThat(report.getResults().size(), is(1));
	}

	@Test
	void askingAboutTooManyIsRefusedRatherThanServed() {
		// THE BOUND. Without it the endpoint is the amplifier it was built to replace, with
		// the convenience of a single request.
		final var tooMany = IntStream.range(0, 30).mapToObj(i -> randomUUID()).toList();

		final var refused = assertThrows(HttpClientResponseException.class,
			() -> liveAvailabilityApiClient.getBatchAvailabilityReport(tooMany));

		assertThat(refused.getStatus(), is(BAD_REQUEST));
		// The BODY, not the exception message, which is only "Bad Request". The refusal has
		// to tell a caller what to do instead, or they will raise the limit.
		final var body = refused.getResponse().getBody(String.class).orElse("");
		assertTrue(body.contains("Page the list"), body);
	}

	@Test
	void anEmptyRequestIsRefused() {
		assertThrows(HttpClientResponseException.class,
			() -> liveAvailabilityApiClient.getBatchAvailabilityReport(List.of()));
	}

	/** A cluster with one bib, held at one location, with one item. */
	private UUID clusterHolding(String sourceRecordId, String itemId) {
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, clusterRecordId);

		bibRecordFixture.createBibRecord(randomUUID(),
			hostLmsFixture.findByCode(CATALOGUING_HOST_LMS_CODE).getId(),
			sourceRecordId, clusterRecord);

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			SierraItem.builder()
				.id(itemId)
				.barcode("barcode-" + itemId)
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("999")
				.locationCode(LOCATION_CODE)
				.locationName("King 6th Floor")
				.suppressed(false)
				.deleted(false)
				.build()));

		return clusterRecordId;
	}
}

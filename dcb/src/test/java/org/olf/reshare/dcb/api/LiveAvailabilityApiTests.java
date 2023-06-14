package org.olf.reshare.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.reshare.dcb.test.BibRecordFixture;
import org.olf.reshare.dcb.test.ClusterRecordFixture;
import org.olf.reshare.dcb.test.HostLmsFixture;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveAvailabilityApiTests {
	private static final String HOST_LMS_CODE = "live-availability-api-tests";

	@Inject
	private ResourceLoader loader;
	@Inject
	@Client("/")
	private HttpClient client;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://live-availability-api-tests.com";
		final String KEY = "live-availability-key";
		final String SECRET = "live-availability-secret";

		SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);

		sierraItemsAPIFixture.twoItemsResponseForBibId("798472");

		hostLmsFixture.deleteAllHostLMS();

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);
	}

	@BeforeEach
	void beforeEach() {
		bibRecordFixture.deleteAllBibRecords();
		clusterRecordFixture.deleteAllClusterRecords();
	}

	@Test
	void canProvideAListOfAvailableItems() {
		final var clusterRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);

		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(randomUUID(), sourceSystemId,
			"798472", clusterRecord);

		final var uri = UriBuilder.of("/items/availability")
			.queryParam("clusteredBibId", clusterRecordId)
			.build();

		final var availabilityResponse = client.toBlocking()
			.retrieve(HttpRequest.GET(uri), AvailabilityResponse.class);

		assertThat(availabilityResponse, is(notNullValue()));

		final var items = availabilityResponse.getItemList();

		assertThat(items, is(notNullValue()));
		assertThat(items.size(), is(2));
		assertThat(availabilityResponse.getClusteredBibId(), is(clusterRecordId));

		final var firstItem = items.get(0);

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem.getId(), is("1000002"));
		assertThat(firstItem.getBarcode(), is("6565750674"));
		assertThat(firstItem.getCallNumber(), is("BL221 .C48"));
		assertThat(firstItem.getDueDate(), is(nullValue()));
		assertThat(firstItem.getIsRequestable(), is(true));
		assertThat(firstItem.getHoldCount(), is(0));

		final var firstItemStatus = firstItem.getStatus();

		assertThat(firstItemStatus, is(notNullValue()));
		assertThat(firstItemStatus.getCode(), is("AVAILABLE"));

		final var firstItemLocation = firstItem.getLocation();

		assertThat(firstItemLocation, is(notNullValue()));
		assertThat(firstItemLocation.getCode(), is("ab6"));
		assertThat(firstItemLocation.getName(), is("King 6th Floor"));

		final var secondItem = items.get(1);

		assertThat(secondItem, is(notNullValue()));
		assertThat(secondItem.getId(), is("1000001"));
		assertThat(secondItem.getBarcode(), is("30800005238487"));
		assertThat(secondItem.getCallNumber(), is("HD9787.U5 M43 1969"));
		assertThat(secondItem.getDueDate(), is("2021-02-25T12:00:00Z"));
		assertThat(secondItem.getIsRequestable(), is(false));
		assertThat(secondItem.getHoldCount(), is(0));

		final var secondItemStatus = secondItem.getStatus();

		assertThat(secondItemStatus, is(notNullValue()));
		assertThat(secondItemStatus.getCode(), is("CHECKED_OUT"));

		final var secondItemLocation = secondItem.getLocation();

		assertThat(secondItemLocation, is(notNullValue()));
		assertThat(secondItemLocation.getCode(), is("ab6"));
		assertThat(secondItemLocation.getName(), is("King 6th Floor"));
	}
}

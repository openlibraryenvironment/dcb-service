package org.olf.reshare.dcb.api;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.reshare.dcb.test.BibRecordFixture;
import org.olf.reshare.dcb.test.ClusterRecordFixture;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.UUID;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MockServerMicronautTest
@MicronautTest(transactional = false, propertySources = { "classpath:configs/LiveAvailabilityApiTests.yml" }, rebuildContext = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveAvailabilityApiTests {
	private static final String SIERRA_TOKEN = "test-token-for-user";

	@Inject
	ResourceLoader loader;

	@Inject
	@Client("/")
	HttpClient client;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private HostLmsService hostLmsService;

	// Properties should line up with included property source for the spec.
	@Property(name = "hosts.test1.client.base-url")
	private String sierraHost;
	@Property(name = "hosts.test1.client.key")
	private String sierraUser;
	@Property(name = "hosts.test1.client.secret")
	private String sierraPass;

	@BeforeAll
	@SneakyThrows
	public void addFakeSierraApis(MockServerClient mock) {
		SierraTestUtils.mockFor(mock, sierraHost)
			.setValidCredentials(sierraUser, sierraPass, SIERRA_TOKEN, 60);

		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);

		sierraItemsAPIFixture.twoItemsResponseForBibId("798472");

		sierraItemsAPIFixture.zeroItemsResponseForBibId("565382");
	}

	@BeforeEach
	void beforeEach() {
		bibRecordFixture.deleteAllBibRecords();
		clusterRecordFixture.deleteAllClusterRecords();
	}

	@Test
	void canProvideAListOfAvailableItemsViaLiveAvailabilityApi() {
		final var clusterRecordId = randomUUID();

		createClusterRecordAndBibRecord(clusterRecordId, "798472");

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
		assertThat(firstItem.getBarcode(), is("9849123490"));
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

	@Test
	void reportsNoItemsWhenSierraRespondsWithNoRecordsFoundError() {
		final var clusterRecordId = randomUUID();

		createClusterRecordAndBibRecord(clusterRecordId,"565382");

		final var uri = UriBuilder.of("/items/availability")
			.queryParam("clusteredBibId", clusterRecordId)
			.build();

		final var availabilityResponse = client.toBlocking()
			.retrieve(HttpRequest.GET(uri), AvailabilityResponse.class);

		assertThat(availabilityResponse.getClusteredBibId(), is(clusterRecordId));

		// Empty lists does not get serialised to JSON
		assertThat(availabilityResponse.getItemList(), is(nullValue()));
	}

	@Test
	void failsWhenHostLmsCannotBeFound() {
		final var clusterRecordId = randomUUID();
		final var sourceSystemId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId,
			"565382", clusterRecord);

		final var uri = UriBuilder.of("/items/availability")
			.queryParam("clusteredBibId", clusterRecordId)
			.build();

		// These are separate variables to only have single invocation in assertThrows
		final var blockingClient = client.toBlocking();
		final var request = HttpRequest.GET(uri);

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> blockingClient.exchange(request, Argument.of(AvailabilityResponse.class),
				Argument.of(String.class)));

		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(BAD_REQUEST));

		final var optionalBody = response.getBody(String.class);

		assertThat(optionalBody.isPresent(), is(true));

		final var body = optionalBody.get();

		assertThat(body, is("No Host LMS found for ID: " + sourceSystemId));
	}

	@Test
	void noBibsInClusteredBibShouldReturnEmptyList() {
		final var clusterRecordId = randomUUID();

		clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var uri = UriBuilder.of("/items/availability")
			.queryParam("clusteredBibId", clusterRecordId)
			.build();

		final var availabilityResponse = client.toBlocking()
			.retrieve(HttpRequest.GET(uri), AvailabilityResponse.class);

		assertThat(availabilityResponse.getClusteredBibId(), is(clusterRecordId));

		// Empty lists does not get serialised to JSON
		assertThat(availabilityResponse.getItemList(), is(nullValue()));
	}

	@Test
	void nullBibsInClusteredBibShouldReturnEmptyList() {
		final var clusterRecordId = randomUUID();

		clusterRecordFixture.createClusterRecordNullBibs(clusterRecordId);

		final var uri = UriBuilder.of("/items/availability")
			.queryParam("clusteredBibId", clusterRecordId)
			.build();

		final var availabilityResponse = client.toBlocking()
			.retrieve(HttpRequest.GET(uri), AvailabilityResponse.class);

		assertThat(availabilityResponse.getClusteredBibId(), is(clusterRecordId));

		// Empty lists does not get serialised to JSON
		assertThat(availabilityResponse.getItemList(), is(nullValue()));
	}

	private void createClusterRecordAndBibRecord(UUID clusterRecordId, String sourceRecordId) {
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var testHostLms = hostLmsService.findByCode("test1").block();

		UUID sourceSystemId = testHostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId,
			sourceRecordId, clusterRecord);
	}
}

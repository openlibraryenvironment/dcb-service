package org.olf.reshare.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.HostLmsService.UnknownHostLmsException;
import org.olf.reshare.dcb.test.BibRecordFixture;
import org.olf.reshare.dcb.test.ClusterRecordFixture;
import org.olf.reshare.dcb.test.DcbTest;
import org.olf.reshare.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;

@DcbTest
class SharedIndexServiceTests {
	@Inject
	private SharedIndexService sharedIndexService;

	@Inject
	private ClusterRecordFixture clusterRecordFixture;

	@Inject
	private BibRecordFixture bibRecordFixture;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		bibRecordFixture.deleteAllBibRecords();
		clusterRecordFixture.deleteAllClusterRecords();
		hostLmsFixture.deleteAllHostLMS();
	}

	@Test
	void canFindClusterRecordWithAssociatedBibs() {
		final var clusterRecordId = randomUUID();

		final var firstHostLmsId = randomUUID();
		final var secondHostLmsId = randomUUID();

		final var firstBibRecordId = randomUUID();
		final var secondBibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		hostLmsFixture.createHostLms(firstHostLmsId, "FIRST-HOST-LMS");
		hostLmsFixture.createHostLms(secondHostLmsId, "SECOND-HOST-LMS");

		bibRecordFixture.createBibRecord(firstBibRecordId, secondHostLmsId,
			"798472", clusterRecord);

		bibRecordFixture.createBibRecord(secondBibRecordId, firstHostLmsId,
			"896857", clusterRecord);

		final var clusteredBib = sharedIndexService
			.findClusteredBib(clusterRecordId).block();

		assertThat(clusteredBib, is(notNullValue()));
		assertThat(clusteredBib.getId(), is(clusterRecordId));

		final var bibs = clusteredBib.getBibs();

		assertThat(bibs, is(notNullValue()));
		assertThat(bibs, hasSize(2));

		final var firstBib = bibs.get(0);

		assertThat(firstBib, is(notNullValue()));
		assertThat(firstBib.getId(), is(firstBibRecordId));
		assertThat(firstBib.getBibRecordId(), is("798472"));

		assertThat(firstBib.getHostLms(), is(notNullValue()));
		assertThat(firstBib.getHostLms().getCode(), is("SECOND-HOST-LMS"));

		final var secondBib = bibs.get(1);

		assertThat(secondBib, is(notNullValue()));
		assertThat(secondBib.getId(), is(secondBibRecordId));
		assertThat(secondBib.getBibRecordId(), is("896857"));

		assertThat(secondBib.getHostLms(), is(notNullValue()));
		assertThat(secondBib.getHostLms().getCode(), is("FIRST-HOST-LMS"));
	}

	@Test
	void canFindClusterRecordWithNoAssociatedBibs() {
		final var clusterRecordId = randomUUID();

		clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var clusteredBib = sharedIndexService
			.findClusteredBib(clusterRecordId).block();

		assertThat(clusteredBib, is(notNullValue()));
		assertThat(clusteredBib.getId(), is(clusterRecordId));

		final var bibs = clusteredBib.getBibs();

		assertThat(bibs, is(notNullValue()));
		assertThat(bibs, hasSize(0));
	}

	@Test
	void failsWhenCannotFindClusterRecordById() {
		final var clusterRecordId = randomUUID();

		final var exception = assertThrows(CannotFindClusterRecordException.class,
			() -> sharedIndexService.findClusteredBib(clusterRecordId).block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is(
			"Cannot find cluster record for: " + clusterRecordId));
	}

	@Test
	void failsWhenCannotFindHostLmsForBib() {
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();
		final var unknownHostId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, unknownHostId,
			"7657673", clusterRecord);

		final var exception = assertThrows(UnknownHostLmsException.class,
			() -> sharedIndexService.findClusteredBib(clusterRecordId).block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is(
			"No Host LMS found for ID: " + unknownHostId));
	}
}

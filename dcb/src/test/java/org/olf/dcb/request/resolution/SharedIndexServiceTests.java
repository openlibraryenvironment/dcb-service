package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.matchers.BibMatchers.hasHostLmsCode;
import static org.olf.dcb.test.matchers.BibMatchers.hasSourceRecordId;
import static org.olf.dcb.test.matchers.ModelMatchers.hasId;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService.UnknownHostLmsException;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

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
		clusterRecordFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void canFindClusterRecordWithAssociatedBibs() {
		final var clusterRecordId = randomUUID();

		final var firstBibRecordId = randomUUID();
		final var secondBibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var firstHostLms = hostLmsFixture.createSierraHostLms("FIRST-HOST-LMS");
		final var secondHostLms = hostLmsFixture.createSierraHostLms("SECOND-HOST-LMS");

		final var firstHostLmsId = firstHostLms.getId();
		final var secondHostLmsId = secondHostLms.getId();

		bibRecordFixture.createBibRecord(firstBibRecordId, secondHostLmsId,
			"798472", clusterRecord);

		bibRecordFixture.createBibRecord(secondBibRecordId, firstHostLmsId,
			"896857", clusterRecord);

		final var clusteredBib = sharedIndexService
			.findClusteredBib(clusterRecordId).block();

		assertThat(clusteredBib, is(notNullValue()));
		assertThat(clusteredBib, hasId(clusterRecordId));

		assertThat("Should have multiple bib records",
			clusteredBib.getBibs(), containsInAnyOrder(
				allOf(
					hasId(firstBibRecordId),
					hasSourceRecordId("798472"),
					hasHostLmsCode("SECOND-HOST-LMS")
				),
				allOf(
					hasId(secondBibRecordId),
					hasSourceRecordId("896857"),
					hasHostLmsCode("FIRST-HOST-LMS")
				)
			));
	}

	@Test
	void canFindClusterRecordWithNoAssociatedBibs() {
		final var clusterRecordId = randomUUID();

		clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var clusteredBib = sharedIndexService
			.findClusteredBib(clusterRecordId).block();

		assertThat(clusteredBib, is(notNullValue()));
		assertThat(clusteredBib, hasId(clusterRecordId));

		assertThat("Should have no bibs", clusteredBib.getBibs(), hasSize(0));
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

		assertThat(exception, hasMessage("No Host LMS found for ID: " + unknownHostId));
	}
}

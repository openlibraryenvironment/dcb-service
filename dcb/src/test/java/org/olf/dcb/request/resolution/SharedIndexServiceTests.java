package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.olf.dcb.test.matchers.BibRecordMatchers.hasSourceRecordId;
import static org.olf.dcb.test.matchers.BibRecordMatchers.hasSourceSystemIdFor;
import static org.olf.dcb.test.matchers.ModelMatchers.hasId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
					hasSourceSystemIdFor(secondHostLms)
				),
				allOf(
					hasId(secondBibRecordId),
					hasSourceRecordId("896857"),
					hasSourceSystemIdFor(firstHostLms)
				)
			));
	}
}

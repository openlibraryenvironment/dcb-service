package org.olf.dcb.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.availability.job.BibAvailabilityCount;
import org.olf.dcb.availability.job.BibAvailabilityCount.Status;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;

@DcbTest
class BibAvailabilityCountRepositoryTests {
	@Inject
	private BibAvailabilityCountRepository counts;
	@Inject
	private BibRepository bibs;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@Test
	void removesOnlyStaleCountsForOneBibAndHostLms() {
		final var bibId = UUID.randomUUID();
		final var hostLms = UUID.randomUUID();
		final var retained = count(bibId, hostLms, "CURRENT");
		final var stale = count(bibId, hostLms, "STALE");
		final var anotherBib = count(UUID.randomUUID(), hostLms, "OTHER-BIB");

		singleValueFrom(counts.save(retained));
		singleValueFrom(counts.save(stale));
		singleValueFrom(counts.save(anotherBib));

		final var deleted = singleValueFrom(counts.deleteAllByBibIdAndHostLmsAndIdNotIn(
			bibId, hostLms, List.of(retained.getId())));

		assertThat(deleted, is(1L));
		assertThat(singleValueFrom(counts.existsById(retained.getId())), is(true));
		assertThat(singleValueFrom(counts.existsById(stale.getId())), is(false));
		assertThat(singleValueFrom(counts.existsById(anotherBib.getId())), is(true));
	}

	@Test
	void rechecksOnlyLegacyCountsOlderThanTheGraceCutoff() {
		final var host = hostLmsFixture.createDummyHostLms(
			"legacy-grace-" + UUID.randomUUID().toString().substring(0, 8));
		final var oldBibId = UUID.randomUUID();
		final var recentBibId = UUID.randomUUID();
		final var cutoff = Instant.now().minusSeconds(14 * 24 * 60 * 60);

		bibRecordFixture.createBibRecord(oldBibId, host.getId(), "old", cluster());
		bibRecordFixture.createBibRecord(recentBibId, host.getId(), "recent", cluster());
		singleValueFrom(counts.save(legacyCount(oldBibId, host, cutoff.minusSeconds(1))));
		singleValueFrom(counts.save(legacyCount(recentBibId, host, cutoff.plusSeconds(1))));

		final var selected = manyValuesFrom(bibs.findMissingAvailability(100, cutoff)).stream()
			.map(info -> info.bibId())
			.toList();

		assertThat(selected, hasItem(oldBibId));
		assertThat(selected, not(hasItem(recentBibId)));
	}

	private ClusterRecord cluster() {
		return clusterRecordFixture.createClusterRecord(UUID.randomUUID(), UUID.randomUUID());
	}

	private static BibAvailabilityCount count(UUID bibId, UUID hostLms, String locationCode) {
		return BibAvailabilityCount.builder()
			.id(UUID.randomUUID())
			.bibId(bibId)
			.hostLms(hostLms)
			.remoteLocationCode(locationCode)
			.count(1)
			.status(Status.MAPPED)
			.lastUpdated(Instant.now())
			.build();
	}

	private static BibAvailabilityCount legacyCount(UUID bibId, DataHostLms host, Instant lastUpdated) {
		return BibAvailabilityCount.builder()
			.id(UUID.randomUUID())
			.bibId(bibId)
			.hostLms(host.getId())
			.remoteLocationCode("LEGACY")
			.count(1)
			.status(Status.MAPPED)
			.lastUpdated(lastUpdated)
			.build();
	}
}

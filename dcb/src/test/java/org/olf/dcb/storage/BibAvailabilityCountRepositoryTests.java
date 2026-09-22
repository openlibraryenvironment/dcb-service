package org.olf.dcb.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.availability.job.BibAvailabilityCount;
import org.olf.dcb.availability.job.BibAvailabilityCount.Status;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
class BibAvailabilityCountRepositoryTests {
	@Inject
	private BibAvailabilityCountRepository counts;

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
}

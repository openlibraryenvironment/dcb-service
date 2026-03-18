package org.olf.dcb.request.resolution;

import static java.util.Collections.emptySet;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.BibRecordMatchers.hasSourceRecordId;
import static org.olf.dcb.test.matchers.BibRecordMatchers.hasSourceSystemIdFor;
import static org.olf.dcb.test.matchers.ModelMatchers.hasId;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.clustering.model.ClusterRecord;
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
	void shouldFindClusterRecordWithAssociatedBibs() {
		final var clusterRecordId = randomUUID();

		final var firstBibRecordId = randomUUID();
		final var secondBibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, firstBibRecordId);

		final var firstHostLms = hostLmsFixture.createDummyHostLms("first-host-lms");
		final var secondHostLms = hostLmsFixture.createDummyHostLms("second-host-lms");

		final var firstHostLmsId = firstHostLms.getId();
		final var secondHostLmsId = secondHostLms.getId();

		final var firstBibId = "798472";

		bibRecordFixture.createBibRecord(firstBibRecordId, secondHostLmsId,
			firstBibId, clusterRecord);

		final var secondBibId = "896857";

		bibRecordFixture.createBibRecord(secondBibRecordId, firstHostLmsId,
			secondBibId, clusterRecord);

		final var clusteredBib = findClusteredBib(clusterRecordId);

		assertThat(clusteredBib, is(notNullValue()));

		assertThat(clusteredBib, allOf(
			hasId(clusterRecordId),
			hasProperty("bibs", containsInAnyOrder(
				allOf(
					hasId(firstBibRecordId),
					hasSourceRecordId(firstBibId),
					hasSourceSystemIdFor(secondHostLms)
				),
				allOf(
					hasId(secondBibRecordId),
					hasSourceRecordId(secondBibId),
					hasSourceSystemIdFor(firstHostLms)
				)
			))
		));
	}

	@Test
	void shouldNotFindUnknownClusterRecord() {
		final var clusterRecordId = randomUUID();

		// Act
		final var exception = assertThrows(CannotFindClusterRecordException.class,
			() -> findClusteredBib(clusterRecordId));

		// Assert
		assertThat(exception, hasMessage("Cannot find cluster record for: " + clusterRecordId));
	}

	@Test
	void shouldNotFindDeletedClusterRecordWhenExcluded() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			ClusterRecord.builder()
				.id(randomUUID())
				.title("Deleted")
				.selectedBib(bibRecordId)
				.bibs(emptySet())
				.isDeleted(true)
				.build());

		final var hostLms = hostLmsFixture.createDummyHostLms("host-lms");

		bibRecordFixture.createBibRecord(bibRecordId, hostLms.getId(),
			"847465", clusterRecord);

		final var clusterRecordId = getValueOrNull(clusterRecord, ClusterRecord::getId);

		// Act
		final var exception = assertThrows(CannotFindClusterRecordException.class,
			() -> findClusteredBib(clusterRecordId, false));

		// Assert
		assertThat(exception, hasMessage("Cannot find cluster record for: " + clusterRecordId));
	}

	@Test
	void shouldFindDeletedClusterRecordWhenIncluded() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			ClusterRecord.builder()
				.id(randomUUID())
				.title("Deleted")
				.selectedBib(bibRecordId)
				.bibs(emptySet())
				.isDeleted(true)
				.build());

		final var hostLms = hostLmsFixture.createDummyHostLms("host-lms");

		bibRecordFixture.createBibRecord(bibRecordId, hostLms.getId(),
			"847465", clusterRecord);

		final var clusterRecordId = getValueOrNull(clusterRecord, ClusterRecord::getId);

		// Act
		final var clusteredBib = findClusteredBib(clusterRecordId, true);

		// Assert
		assertThat(clusteredBib, hasId(clusterRecordId));
	}

	private ClusteredBib findClusteredBib(UUID clusterRecordId) {
		return findClusteredBib(clusterRecordId, true);
	}

	private ClusteredBib findClusteredBib(UUID clusterRecordId, boolean includeDeleted) {
		return singleValueFrom(sharedIndexService.findClusteredBib(clusterRecordId, includeDeleted));
	}
}

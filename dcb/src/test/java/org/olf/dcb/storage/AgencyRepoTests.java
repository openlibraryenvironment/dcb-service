package org.olf.dcb.storage;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;

@DcbTest
class AgencyRepoTests {
	@Inject
	AgencyRepository agencyRepository;

	@Inject
	HostLmsRepository hostLmsRepository;

	@Inject
	BibRepository bibRepository;

	@Inject
	AgencyFixture agencyFixture;
	@Inject
	HostLmsFixture hostLmsFixture;
	@Inject
	BibRecordFixture bibRecordFixture;
	@Inject
	ClusterRecordFixture clusterRecordFixture;

	@BeforeEach
	void beforeEach() {
		bibRecordFixture.deleteAll();
		clusterRecordFixture.deleteAll();
		hostLmsFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	void createAgencyViaRepository() {
		// Arrange
		final var hostLms = hostLmsFixture.createSierraHostLms("example-host-lms");

		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code("agency-code")
			.name("agency name")
			.hostLms(hostLms)
			.latitude(53.383331)
			.longitude(-1.466667)
			.build());

		// Act
		final var fetchedAgencyRecords = manyValuesFrom(agencyRepository.queryAll());

		// Assert
		assertThat(fetchedAgencyRecords, hasSize(1));
	}

	@Test
	void testBibRepoCount() {
		// Arrange
		final var firstHostLms = hostLmsFixture.createSierraHostLms("first-host-lms");
		final var secondHostLms = hostLmsFixture.createSierraHostLms("second-host-lms");

		createBibRecord(firstHostLms, "6737415");
		createBibRecord(secondHostLms, "4623456");

		// Act
		final var summaryReport = manyValuesFrom(bibRepository.getIngestReport());

		// Assert
		assertThat(summaryReport, hasSize(2));
	}

	private void createBibRecord(DataHostLms firstHostLms, String sourceRecordId) {
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			randomUUID(), bibRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, firstHostLms.getId(),
			sourceRecordId, clusterRecord);
	}
}

package org.olf.dcb.test;

import static java.time.Instant.now;
import static java.util.Collections.emptySet;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.storage.ClusterRecordRepository;

import jakarta.inject.Singleton;

@Singleton
public class ClusterRecordFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final ClusterRecordRepository clusterRecordRepository;

	private final BibRecordFixture bibRecordFixture;

	public ClusterRecordFixture(ClusterRecordRepository clusterRecordRepository,
		BibRecordFixture bibRecordFixture) {

		this.clusterRecordRepository = clusterRecordRepository;
		this.bibRecordFixture = bibRecordFixture;
	}

	public ClusterRecord createClusterRecord(UUID clusterRecordId, UUID selectedBibId) {
		final var clusterRecord = ClusterRecord.builder()
			.id(clusterRecordId)
			.title("Brain of the Firm")
			.selectedBib(selectedBibId)
			.bibs(emptySet())
			.isDeleted(false)
			.dateCreated(now())
			.dateUpdated(now())
			.lastIndexed(now())
			.build();

		return createClusterRecord(clusterRecord);
	}

	public ClusterRecord createClusterRecord(ClusterRecord clusterRecord) {
		return singleValueFrom(clusterRecordRepository.save(clusterRecord));
	}

	public void deleteAll() {
		bibRecordFixture.deleteAll();

		dataAccess.deleteAll(clusterRecordRepository.queryAll(),
			clusterRecord -> clusterRecordRepository.delete(clusterRecord.getId()));
	}
}

package org.olf.dcb.test;

import static java.lang.Boolean.FALSE;
import static java.time.Instant.now;

import java.util.Set;
import java.util.UUID;

import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.storage.ClusterRecordRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

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
		return Mono.from(clusterRecordRepository.save(new ClusterRecord(clusterRecordId,
				now(), now(), "Brain of the Firm", Set.of(), selectedBibId, FALSE)))
			.block();
	}

	public void deleteAll() {
		bibRecordFixture.deleteAll();

		dataAccess.deleteAll(clusterRecordRepository.queryAll(),
			clusterRecord -> clusterRecordRepository.delete(clusterRecord.getId()));
	}
}

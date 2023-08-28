package org.olf.dcb.test;

import static java.time.Instant.now;

import java.util.UUID;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.storage.ClusterRecordRepository;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class ClusterRecordFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final ClusterRecordRepository clusterRecordRepository;

	public ClusterRecordFixture(ClusterRecordRepository clusterRecordRepository) {
		this.clusterRecordRepository = clusterRecordRepository;
	}

	public ClusterRecord createClusterRecord(UUID clusterRecordId) {
		return Mono.from(clusterRecordRepository.save(new ClusterRecord(clusterRecordId,
				now(), now(), "Brain of the Firm", new java.util.HashSet(), clusterRecordId, Boolean.FALSE)))
			.block();
	}

	public void deleteAllClusterRecords() {
		dataAccess.deleteAll(clusterRecordRepository.queryAll(),
			clusterRecord -> clusterRecordRepository.delete(clusterRecord.getId()));
	}
}

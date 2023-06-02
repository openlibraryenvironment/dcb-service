package org.olf.reshare.dcb.test;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.model.clustering.ClusterRecord;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static java.time.Instant.now;

@Prototype
public class ClusterRecordFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final ClusterRecordRepository clusterRecordRepository;

	public ClusterRecordFixture(ClusterRecordRepository clusterRecordRepository) {
		this.clusterRecordRepository = clusterRecordRepository;
	}

	public ClusterRecord createClusterRecord(UUID clusterRecordId) {
		return Mono.from(clusterRecordRepository.save(new ClusterRecord(clusterRecordId,
				now(), now(), "Brain of the Firm", new java.util.HashSet(), clusterRecordId)))
			.block();
	}

	public ClusterRecord createClusterRecordNullBibs(UUID clusterRecordId) {
		return Mono.from(clusterRecordRepository.save(new ClusterRecord(clusterRecordId,
				now(), now(), "Brain of the Firm", null, null)))
			.block();
	}

	public void deleteAllClusterRecords() {
		dataAccess.deleteAll(clusterRecordRepository.findAll(),
			clusterRecord -> clusterRecordRepository.delete(clusterRecord.getId()));
	}
}

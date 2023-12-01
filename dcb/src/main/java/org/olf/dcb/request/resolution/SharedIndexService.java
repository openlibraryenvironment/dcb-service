package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.ClusterRecordRepository;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class SharedIndexService {
	private final ClusterRecordRepository clusterRecordRepository;
	private final BibRepository bibRepository;

	public SharedIndexService(ClusterRecordRepository clusterRecordRepository,
		BibRepository bibRepository) {

		this.clusterRecordRepository = clusterRecordRepository;
		this.bibRepository = bibRepository;
	}

	public Mono<ClusteredBib> findClusteredBib(UUID bibClusterId) {
		log.debug("findClusteredBib({})", bibClusterId);

		return Mono.from(clusterRecordRepository.findOneById(bibClusterId))
			.switchIfEmpty(Mono.error(new CannotFindClusterRecordException(bibClusterId)))
			.zipWhen(this::findBibRecords, this::mapToClusteredBibWithBib);
	}

	private ClusteredBib mapToClusteredBibWithBib(ClusterRecord clusterRecord,
		List<BibRecord> bibRecords) {

		return ClusteredBib.builder()
			.id(clusterRecord.getId())
			.title(clusterRecord.getTitle())
			.bibRecords(bibRecords)
			.build();
	}

	private Mono<List<BibRecord>> findBibRecords(ClusterRecord clusteredBib) {
		return Flux.from(bibRepository.findAllByContributesTo(clusteredBib))
			.collectList();
	}
}

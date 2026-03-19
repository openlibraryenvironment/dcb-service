package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.request.CannotFindSelectedBibException;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.ClusterRecordRepository;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class SharedIndexService {
	public static final Boolean INCLUDE_DELETED_CLUSTER_RECORDS_DEFAULT = false;

	private final ClusterRecordRepository clusterRecordRepository;
	private final BibRepository bibRepository;

	public SharedIndexService(ClusterRecordRepository clusterRecordRepository,
		BibRepository bibRepository) {

		this.clusterRecordRepository = clusterRecordRepository;
		this.bibRepository = bibRepository;
	}

	public Mono<ClusteredBib> findClusteredBib(UUID clusterRecordId, boolean includeDeleted) {
		log.debug("findClusteredBib({}, {})", clusterRecordId, includeDeleted);

		return findClusterRecord(clusterRecordId, includeDeleted)
			.zipWhen(this::findBibRecords, this::mapToClusteredBibWithBib);
	}

	private ClusteredBib mapToClusteredBibWithBib(ClusterRecord clusterRecord,
		List<BibRecord> bibRecords) {

		return ClusteredBib.builder()
			.id(clusterRecord.getId())
			.title(clusterRecord.getTitle())
			.bibs(bibRecords)
			.build();
	}

	private Mono<List<BibRecord>> findBibRecords(ClusterRecord clusterRecord) {
		return Flux.from(bibRepository.findAllByContributesTo(clusterRecord))
			.collectList();
	}

	public Mono<BibRecord> findSelectedBib(UUID clusterRecordId) {
		// Include deleted cluster records to preserve previous behaviour
		return findClusterRecord(clusterRecordId, true)
			.flatMap(this::getSelectedBib);
	}

	private Mono<BibRecord> getSelectedBib(ClusterRecord clusterRecord) {
		return Mono.from(bibRepository.findById(clusterRecord.getSelectedBib()))
			.switchIfEmpty(Mono.error(new CannotFindSelectedBibException(clusterRecord)));
	}

	private Mono<? extends ClusterRecord> findClusterRecord(UUID clusterRecordId, boolean includeDeleted) {
		return Mono.from(clusterRecordRepository.findOneById(clusterRecordId))
			.filter(excludeDeleted(includeDeleted))
			.switchIfEmpty(Mono.error(new CannotFindClusterRecordException(clusterRecordId)));
	}

	private static Predicate<ClusterRecord> excludeDeleted(boolean includeDeleted) {
		if (includeDeleted) {
			return clusterRecord -> true;
		}
		// Since we treat a cluster record as being deleted when isDeleted == true, null should mean the record is NOT deleted
		return clusterRecord -> Boolean.FALSE.equals(clusterRecord.getIsDeleted());
	}
}

package org.olf.dcb.core.clustering;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.clustering.model.MatchPoint;
import org.olf.dcb.core.model.BibRecord;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RecordClusteringService {

	// Get cluster record by id
	Mono<ClusterRecord> findById(UUID id);

	Mono<ClusterRecord> softDelete(ClusterRecord theRecord);

	Mono<Void> softDeleteByIdInList(Collection<UUID> ids);

	Mono<Page<UUID>> findNext1000UpdatedBefore(Instant before, Pageable page);

	Flux<ClusterRecord> findAllByIdInListWithBibs(Collection<UUID> ids);

	Mono<ClusterRecord> saveOrUpdate(ClusterRecord cluster);

	Mono<BibRecord> clusterBib(BibRecord bib);

	<T> Mono<Page<T>> getPageAs(Optional<Instant> since, Pageable pageable, Function<ClusterRecord, T> mapper);

	Mono<ClusterRecord> electSelectedBib(ClusterRecord cr, Optional<BibRecord> ignoreBib);

	Mono<UUID> disperseAndRecluster(UUID clusterID);
	
	@Introspected
	public static record MissingAvailabilityInfo (@NonNull UUID clusterId, @NonNull UUID bibId, @NonNull UUID sourceSystemId) {}

	Flux<MatchPoint> generateMatchPoints(BibRecord bibRecord);

}
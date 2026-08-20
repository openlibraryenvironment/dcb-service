package org.olf.dcb.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Query;
import org.olf.dcb.core.api.serde.TopClusterStat;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;


public interface ClusterRecordRepository {
	
	@Vetoed
	@NonNull
	@SingleResult
	Publisher<? extends ClusterRecord> findOneById(@NonNull UUID id);

	@Vetoed
	@NonNull
	@SingleResult
	Publisher<ClusterRecord> findById(@NotNull UUID id);

	@NonNull
	@SingleResult
	Publisher<? extends ClusterRecord> save(@Valid @NonNull ClusterRecord clusterRecord);

	@NonNull
	@SingleResult
	Publisher<? extends ClusterRecord> update(@Valid @NonNull ClusterRecord clusterRecord);

//	@SingleResult
//	@NonNull
//	default Publisher<ClusterRecord> saveOrUpdate(@Valid @NonNull ClusterRecord clusterRecord) {
//		
//		return Mono.defer( () -> Mono.just(clusterRecord.getId()) )
//			.map(this::existsById)
//			.flatMap(Mono::from)
//			.map( update -> (update ? this.update(clusterRecord) : this.save(clusterRecord)) )
//			.flatMap(Mono::from);
//	}

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);
	
	@NonNull
	Publisher<ClusterRecord> queryAll();

	@NonNull
	@SingleResult
	Publisher<Page<ClusterRecord>> queryAll(@Valid Pageable pageable);

	@NonNull
	@SingleResult
	Publisher<Page<ClusterRecord>> findByDateUpdatedGreaterThanOrderByDateUpdated(Instant i, @Valid Pageable pageable);

	@SingleResult
	@NonNull
	Publisher<Page<UUID>> findIdByDateUpdatedLessThanEqualsOrderByDateUpdated(Instant i, @Valid Pageable pageable);
	
	@SingleResult
	@NonNull
	Publisher<Page<UUID>> findIdByLastIndexedIsNullOrLastIndexedLessThanOrderByDateUpdated(Instant i, @Valid Pageable pageable);

	@Vetoed
	@NonNull
	@SingleResult
	Publisher<Long> updateLastIndexed(Collection<UUID> ids, Instant lastIndexed);
	
	@NonNull
	@Vetoed
	Publisher<ClusterRecord> findAllByMatchPoints ( Collection<UUID> points );

	@Vetoed
	@NonNull
	Publisher<ClusterRecord> findAllByDerivedTypeAndMatchPoints ( String derivedType, Collection<UUID> points );
	
	@Vetoed
	@NonNull
	Publisher<ClusterRecord> findAllByBibIdInAndDerivedTypeAndIdNotIn(Collection<UUID> bibIds, String derivedType,
			Collection<UUID> excludeClusters);
	
	@Vetoed
	@NonNull
	default Publisher<ClusterRecord> findAllByBibIdInAndDerivedType(Collection<UUID> bibIds, String derivedType) {
		return this.findAllByBibIdInAndDerivedTypeAndIdNotIn(bibIds, derivedType, Collections.emptySet());
	}

//	@NonNull
//	@Vetoed
//  Publisher<ClusterRecord> findAllByDerivedTypeAndMatchPointsWithISBNExclusion ( String derivedType, Collection<UUID> points, String isbnExclusion );

	@NonNull
	@SingleResult
	Publisher<Void> delete(@NonNull UUID id);
	
	@SingleResult
	@Vetoed
	Publisher<Long> touch( @NonNull UUID id );

	@NonNull
	Publisher<ClusterRecord> findAllByIdInList(@NonNull Collection<UUID> id);

	@Vetoed
	@NonNull
	Publisher<ClusterRecord> findByIdInListWithBibs(@NonNull Collection<UUID> id);

	@Vetoed
	@NonNull
	Publisher<UUID> getClusterIdsWithOutdatedUnprocessedBibs(int version, int max);
	
	@NonNull
	@SingleResult
	default Publisher<UUID> getClusterIdIfOutdated(int version, UUID id) {
		return getClusterIdsWithBibsPriorToVersionInList(version, Collections.singleton(id));
	}

	@Vetoed
	@NonNull
	Publisher<UUID> getClusterIdsWithBibsPriorToVersionInList(int version, Collection<UUID> ids);

	@Vetoed
	@NonNull
	@SingleResult
	Publisher<Integer> reprocessOrphanedBibsWithSource();

	// "Acquisition opportunities": clusters in high CONSORTIUM-WIDE demand to which this
	// library contributes no bib record. Network demand, not just this library's own patrons -
	// the acquisition-development signal. Takes a library code (aligned with every other stats
	// endpoint) rather than a raw host_lms UUID.
	@Query(
		value = """
        SELECT cr.id as cluster_id, cr.title, COUNT(pr.id) as request_count
        FROM cluster_record cr
        JOIN patron_request pr ON cr.id = pr.bib_cluster_id
        WHERE pr.status_code != 'ERROR'
					AND (:startDate IS NULL OR pr.date_created >= :startDate)
					AND (:endDate IS NULL OR pr.date_created <= :endDate)
          AND NOT EXISTS (
              SELECT 1 FROM bib_record br
              JOIN host_lms hl ON br.source_system_id = hl.id
              WHERE br.contributes_to = cr.id
                AND hl.code = :libraryCode
          )
        GROUP BY cr.id, cr.title
        ORDER BY request_count DESC
        LIMIT 20
    """,
		nativeQuery = true
	)
	Flux<TopClusterStat> findAcquisitionOpportunitiesForLibrary(String libraryCode, @Nullable Instant startDate,
																												 @Nullable Instant endDate);



}

package org.olf.dcb.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;


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
	
	@NonNull
	@Vetoed
	Publisher<ClusterRecord> findAllByMatchPoints ( Collection<UUID> points );
	
	@NonNull
	@Vetoed
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
}

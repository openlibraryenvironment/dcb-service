package org.olf.dcb.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;


public interface ClusterRecordRepository {

	@QueryHint(name="javax.persistence.FlushModeType", value="AUTO")
	@NonNull
	@SingleResult
	Publisher<? extends ClusterRecord> findOneById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<ClusterRecord> findById(@NotNull UUID id);

	@NonNull
	@SingleResult
	Publisher<? extends ClusterRecord> save(@Valid @NonNull ClusterRecord clusterRecord);

	@NonNull
	@SingleResult
	Publisher<? extends ClusterRecord> update(@Valid @NonNull ClusterRecord clusterRecord);

	@SingleResult
	@NonNull
	default Publisher<? extends ClusterRecord> saveOrUpdate(@Valid @NonNull ClusterRecord clusterRecord) {
		return Mono.from(this.existsById(clusterRecord.getId()))
				.flatMap(update -> Mono.from(update ? this.update(clusterRecord) : this.save(clusterRecord)));
	}

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
	
	@NonNull
	Publisher<ClusterRecord> findAllByMatchPoints ( Collection<UUID> points );
	
	@NonNull
	Publisher<ClusterRecord> findAllByDerivedTypeAndMatchPoints ( String derivedType, Collection<UUID> points );

	@NonNull
	@SingleResult
	Publisher<Void> delete(@NonNull UUID id);
	
	@SingleResult
	Publisher<Long> touch( @NonNull UUID id );

	@NonNull
	Publisher<ClusterRecord> findAllByIdInList(@NonNull Collection<UUID> id);
}

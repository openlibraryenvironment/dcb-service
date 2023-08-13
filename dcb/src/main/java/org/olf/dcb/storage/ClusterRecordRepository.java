package org.olf.dcb.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Mono;
import io.micronaut.data.annotation.QueryHint;


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
	Publisher<ClusterRecord> findAll();

	@NonNull
	Publisher<Page<ClusterRecord>> findAll(@Valid Pageable pageable);

	@NonNull
	Publisher<Page<ClusterRecord>> findByDateUpdatedGreaterThanOrderByDateUpdated(Instant i, @Valid Pageable pageable);
	
	@NonNull
	Publisher<ClusterRecord> findAllByMatchPoints ( Collection<UUID> points );

	@NonNull
	@SingleResult
	Publisher<Void> delete(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Void> deleteByIdInList(@NonNull Collection<UUID> id);
}

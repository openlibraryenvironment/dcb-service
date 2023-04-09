package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Mono;

public interface ClusterRecordRepository {
	@NonNull
	@SingleResult
	Publisher<? extends ClusterRecord> findOneById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<? extends ClusterRecord> save(@Valid @NotNull @NonNull ClusterRecord clusterRecord);

	@NonNull
	@SingleResult
	Publisher<? extends ClusterRecord> update(@Valid @NotNull @NonNull ClusterRecord clusterRecord);

	@SingleResult
	@NonNull
	default Publisher<? extends ClusterRecord> saveOrUpdate(@Valid @NotNull ClusterRecord clusterRecord) {
		return Mono.from(this.existsById(clusterRecord.getId()))
			.flatMap( update -> Mono.from(update ? this.update(clusterRecord) : this.save(clusterRecord)) )
		;
	}
	
	@NonNull
	@SingleResult
	Publisher<Boolean> existsById( @NonNull UUID id );

	@NonNull
	Publisher<ClusterRecord> findAll();

	Publisher<Page<ClusterRecord>> findAll(@Valid Pageable pageable);

	@NonNull
	@SingleResult
	Publisher<Void> delete(@NonNull UUID id);
}

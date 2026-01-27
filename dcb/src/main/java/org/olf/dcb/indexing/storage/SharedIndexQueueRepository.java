package org.olf.dcb.indexing.storage;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.indexing.model.SharedIndexQueueEntry;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;

public interface SharedIndexQueueRepository {

	@SingleResult
	@NonNull
	public Publisher<Page<SharedIndexQueueEntry>> findAllOrderByClusterDateUpdatedAsc( @Valid @NonNull Pageable pageable );
	
	@SingleResult
	@NonNull
	public Publisher<? extends SharedIndexQueueEntry> save( @Valid @NonNull SharedIndexQueueEntry entry);

	@SingleResult
	@NonNull
	public Publisher<Long> deleteAllByClusterIdIn(List<UUID> clusterIds);
}

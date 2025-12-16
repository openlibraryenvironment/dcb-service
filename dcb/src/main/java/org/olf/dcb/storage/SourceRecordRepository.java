package org.olf.dcb.storage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.RecordCount;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.dataimport.job.model.SourceRecord.ProcessingStatus;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

public interface SourceRecordRepository {
	@NonNull
	@SingleResult
	Publisher<? extends SourceRecord> save(@Valid @NotNull @NonNull SourceRecord rawSource);

	@NonNull
	@SingleResult
	Publisher<? extends SourceRecord> update(@Valid @NotNull @NonNull SourceRecord rawSource);
	
	@SingleResult
	@NonNull
	default Publisher<SourceRecord> saveOrUpdate(@Valid @NotNull SourceRecord rawSource) {
		
		return Mono.deferContextual( context -> {
			return Mono.from(this.existsById(rawSource.getId()))
				.map( update -> update ? this.update(rawSource) : this.save(rawSource) )
				.flatMap(Mono::from);
		})
		.thenReturn(rawSource);
	}
	

	@NonNull
	@SingleResult
	Publisher<SourceRecord> getById( @NonNull UUID id );
	
	@NonNull
	@SingleResult
	Publisher<Boolean> existsById( @NonNull UUID id );
	
	@NonNull
	Publisher<SourceRecord> findAllByProcessingState (@NonNull SourceRecord.ProcessingStatus processingState, @NonNull Pageable pageable);
	
	@SingleResult
	@NonNull
	Publisher<Page<SourceRecord>> findAllByDateUpdatedAfter (@NonNull Instant since, @NonNull Pageable pageable);
	
	@SingleResult
	@NonNull
	Publisher<Integer> updateById (@NonNull UUID id, @NonNull Instant lastProcessed, @NonNull ProcessingStatus processingState, @Nullable String processingInformation);

	@SingleResult
	@NonNull
	Publisher<Integer> updateProcessingStateById (@NonNull UUID id, @NonNull ProcessingStatus processingState);
	
	@Vetoed
	public Publisher<RecordCount> getProcessStatusForHostLms(UUID hostLmsId);
	
	@Vetoed
	@SingleResult
	public Publisher<Long> getCountForHostLms(UUID hostLmsId);

	@NonNull
	Publisher<SourceRecord> findByHostLmsIdAndRemoteIdLike(@NonNull UUID hostLmsId, @NonNull String remoteId);

  @SingleResult
  @NonNull
  Publisher<Integer> deleteAllByHostLmsId( UUID hostLmsId );

}

package org.olf.dcb.storage;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.ingest.model.RawSource;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import reactor.core.publisher.Mono;

public interface RawSourceRepository {
	@NonNull
	@SingleResult
	Publisher<? extends RawSource> save(@Valid @NotNull @NonNull RawSource rawSource);

	@NonNull
	@SingleResult
	Publisher<? extends RawSource> update(@Valid @NotNull @NonNull RawSource rawSource);
	
	@NonNull
	@SingleResult
	Publisher<RawSource> findOneByHostLmsIdAndRemoteId(@NonNull UUID hostLmsID, @NonNull String remoteId);

	@SingleResult
	@NonNull
	default Publisher<RawSource> saveOrUpdate(@Valid @NotNull RawSource rawSource) {
		
		return Mono.deferContextual( context -> {
			return Mono.from(this.existsById(rawSource.getId()))
				.map( update -> update ? this.update(rawSource) : this.save(rawSource) )
				.flatMap(Mono::from);
		})
		.thenReturn(rawSource);
	}
	
	@SingleResult
	@NonNull
	Publisher<Integer> deleteAllByHostLmsId( UUID hostLmsId );
	
	@NonNull
	@SingleResult
	Publisher<Boolean> existsById( @NonNull UUID id );
}

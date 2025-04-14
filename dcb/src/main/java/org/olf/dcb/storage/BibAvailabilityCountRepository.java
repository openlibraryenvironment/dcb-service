package org.olf.dcb.storage;

import java.util.UUID;

import org.olf.dcb.availability.job.BibAvailabilityCount;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.async.annotation.SingleResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

public interface BibAvailabilityCountRepository {

	@SingleResult
	@NonNull
	default Publisher<BibAvailabilityCount> saveOrUpdate(@Valid @NotNull @NonNull BibAvailabilityCount bibAvailabilityCount) {
		return Mono.from(this.existsById(bibAvailabilityCount.getId()))
			.flatMap(update -> Mono.from(update ? this.update(bibAvailabilityCount) : this.save(bibAvailabilityCount)));
	}

	@SingleResult
	@NotNull
	@NonNull
	Publisher<? extends BibAvailabilityCount> update(@Valid @NotNull @NonNull BibAvailabilityCount bibAvailabilityCount);

	@SingleResult
	@NotNull
	@NonNull
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<? extends BibAvailabilityCount> save( @Valid @NotNull @NonNull BibAvailabilityCount bibAvailabilityCount );
	

	@Vetoed
	@NonNull
	Publisher<BibAvailabilityCount> findAllKnownForCluster( @NotNull @NonNull UUID clusterId );
}

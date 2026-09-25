package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.availability.job.BibAvailabilityCount;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
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

	/**
	 * Remove rows absent from a complete availability response. The scope is one
	 * bib on one source system, so a refresh cannot affect another bib's cache.
	 */
	@NonNull
	@SingleResult
	@Query(value = "DELETE FROM bib_availability_count "
		+ "WHERE bib_id = :bibId AND host_lms = :hostLms AND id NOT IN (:ids)",
		nativeQuery = true)
	Publisher<Long> deleteAllByBibIdAndHostLmsAndIdNotIn(@NonNull UUID bibId,
		@NonNull UUID hostLms, @NonNull Collection<UUID> ids);
	

	@Vetoed
	@NonNull
	Publisher<BibAvailabilityCount> findAllKnownForCluster( @NotNull @NonNull UUID clusterId );
}

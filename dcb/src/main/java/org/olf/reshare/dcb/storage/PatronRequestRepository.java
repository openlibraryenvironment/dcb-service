package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import io.micronaut.data.annotation.Query;

public interface PatronRequestRepository {
	@NonNull
	@SingleResult
	Publisher<? extends PatronRequest> save(@Valid @NotNull @NonNull PatronRequest patronRequest);

	@NonNull
	@SingleResult
	Publisher<? extends PatronRequest> update(@Valid @NotNull @NonNull PatronRequest patronRequest);

	@NonNull
	@SingleResult
	Publisher<PatronRequest> findById(@NotNull UUID id);

	@NonNull
	Publisher<PatronRequest> findAll();

	@NonNull
	@SingleResult
	Publisher<Page<PatronRequest>> findAll(Pageable page);

	Publisher<Void> delete(UUID id);

	@Query(value = "SELECT p.* from patron_request p  where p.status_code in ( select code from status_code where model = 'PatronRequest' and tracked = true )", nativeQuery = true)
	Publisher<PatronRequest>  findTrackedPatronHolds();
}

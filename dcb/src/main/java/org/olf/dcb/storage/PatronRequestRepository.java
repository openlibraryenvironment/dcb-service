package org.olf.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.StatusCode;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Mono;
import io.micronaut.data.annotation.Id;
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
	Publisher<PatronRequest> findTrackedPatronHolds();

	@Query(value = "SELECT pr.* from patron_request pr, patron_identity pi, host_lms h where pr.patron_id = pi.patron_id and pi.host_lms_id = h.id and h.code = :patronSystem and pi.local_id = :patronId and pi.home_identity=true order by pr.date_updated", countQuery = "SELECT count(pr.id) from patron_request pr, patron_identity pi, host_lms h where pr.patron_id = pi.patron_id and pi.host_lms_id = h.id and h.code = :patronSystem and pi.local_id = :patronId and pi.home_identity=true", nativeQuery = true)
	Publisher<Page<PatronRequest>> findRequestsForPatron(String patronSystem, String patronId, Pageable pageable);

	@SingleResult
	@NonNull
	default Publisher<PatronRequest> saveOrUpdate(@Valid @NotNull @NonNull PatronRequest pc) {
		return Mono.from(this.existsById(pc.getId()))
				.flatMap(update -> Mono.from(update ? this.update(pc) : this.save(pc)));
	}
	

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	
	@NonNull
	@SingleResult
	Publisher<Void> updateStatus(@Id UUID id, PatronRequest.Status status);
}

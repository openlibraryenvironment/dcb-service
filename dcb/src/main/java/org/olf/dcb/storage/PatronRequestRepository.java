package org.olf.dcb.storage;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest.Status;
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

	@SingleResult
	Publisher<Void> delete(UUID id);

	@Query(value = "SELECT p.* from patron_request p  where p.status_code in ( select code from status_code where model = 'PatronRequest' and tracked = true )", nativeQuery = true)
	Publisher<PatronRequest> findTrackedPatronHolds();


        // Find all the virtual items that we need to track
	@Query(value = "SELECT p.* from patron_request p  where p.local_item_id is not null and ( p.local_item_status is null or p.local_item_status in ( select code from status_code where model = 'VirtualItem' and tracked = true ) )", nativeQuery = true)
	Publisher<PatronRequest> findTrackedVirtualItems();

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
	Publisher<Void> updateStatusAndErrorMessage(@Id UUID id, PatronRequest.Status status, String errorMessage);
	
	@NonNull
	@SingleResult
	default Publisher<Void> updateStatusWithError(@Id UUID id, Throwable t) {
		return this.updateStatusAndErrorMessage(id, Status.ERROR, t.getMessage());
	};

	@NonNull
	@SingleResult
	Publisher<PatronIdentity> findRequestingIdentityById(UUID id);
}

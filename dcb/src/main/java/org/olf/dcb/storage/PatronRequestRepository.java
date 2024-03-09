package org.olf.dcb.storage;

import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static services.k_int.utils.StringUtils.truncate;

import java.util.UUID;

import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;
import java.time.Instant;


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
	Publisher<PatronRequest> queryAll();

	@NonNull
	@SingleResult
	Publisher<Page<PatronRequest>> queryAll(Pageable page);

	@SingleResult
	Publisher<Void> delete(UUID id);

	// @Query(value = "SELECT p.* from patron_request p  where p.local_request_status in ( select code from status_code where model = 'PatronRequest' and tracked = true ) and pr.status_code not in ('ERROR', 'FINALISED' )", nativeQuery = true)
	@Query(value = "SELECT p.* from patron_request p  where p.local_request_status in ( select code from status_code where model = 'PatronRequest' and tracked = true )", nativeQuery = true)
	Publisher<PatronRequest> findTrackedPatronHolds();

	@Query(value = "SELECT p.* from patron_request p  where p.status_code in ( select code from status_code where model = 'DCBRequest' and tracked = true )", nativeQuery = true)
	Publisher<PatronRequest> findProgressibleDCBRequests();

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
	default Publisher<Void> updateStatusWithError(@Id UUID id, String errorMessage) {
		// Truncate message to length shorter than database column
		return updateStatusAndErrorMessage(id, ERROR, truncate(errorMessage, 255));
	}

	@NonNull
	@SingleResult
	Publisher<PatronIdentity> findRequestingIdentityById(UUID id);

	@NotNull
	@SingleResult
	@Query(value = "SELECT pr.* from patron_request pr, supplier_request sr where sr.patron_request_id = pr.id and sr.id = :srid", nativeQuery = true)
	Publisher<PatronRequest> getPRForSRID(UUID srid);

  Publisher<Void> updatePickupItemTracking(@NotNull UUID id, Instant pickupItemLastCheckTimestamp);
  Publisher<Void> updatePickupRequestTracking(@NotNull UUID id, Instant pickupRequestLastCheckTimestamp);
  Publisher<Long> updateLocalRequestTracking(@Id @NotNull UUID id, String localRequestStatus, Instant localRequestLastCheckTimestamp, Long localRequestStatusRepeat);
  Publisher<Long> updateLocalItemTracking(@Id @NotNull UUID id, String localItemStatus, Instant localItemLastCheckTimestamp, Long localItemStatusRepeat);

}

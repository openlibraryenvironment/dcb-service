package org.olf.dcb.storage;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.olf.dcb.core.api.serde.PatronRequestSummary;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static services.k_int.utils.StringUtils.truncate;


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
	
	@Vetoed
	Publisher<PatronRequest> findAllTrackableRequests(Iterable<Status> terminalStates, Iterable<String> supplierStatuses, Iterable<String> supplierItemStatuses);

	// local_request_id must be not null, it must currently be in a tracked state and the request itself must be trackable
	@Query(value = "SELECT p.* from patron_request p  where ( p.local_request_status is null OR p.local_request_status in ( select code from status_code where model = 'PatronRequest' and tracked = true ) ) and p.status_code in ( select code from status_code where model = 'DCBRequest' and tracked = true )", nativeQuery = true)
	Publisher<PatronRequest> findTrackedPatronHolds();



	@Query(value = "SELECT p.* from patron_request p  where p.status_code in ( select code from status_code where model = 'DCBRequest' and tracked = true )", nativeQuery = true)
	Publisher<PatronRequest> findProgressibleDCBRequests();



	// Find all the virtual items that we need to track - there must be an item id, the current item state must be null or a tracked state
  // and the request itself must be in a trackable state (Ie don't track item states for FINALISED requests)
	@Query(value = "SELECT p.* from patron_request p  where ( p.local_item_status is null or p.local_item_status in ( select code from status_code where model = 'VirtualItem' and tracked = true ) ) and p.status_code in ( select code from status_code where model = 'DCBRequest' and tracked = true )", nativeQuery = true)
	Publisher<PatronRequest> findTrackedVirtualItems();

	@Query(value = "SELECT pr.* from patron_request pr, patron_identity pi, host_lms h where pr.patron_id = pi.patron_id and pi.host_lms_id = h.id and h.code = :patronSystem and pi.local_id = :patronId and pi.home_identity=true order by pr.date_updated", countQuery = "SELECT count(pr.id) from patron_request pr, patron_identity pi, host_lms h where pr.patron_id = pi.patron_id and pi.host_lms_id = h.id and h.code = :patronSystem and pi.local_id = :patronId and pi.home_identity=true", nativeQuery = true)
	Publisher<Page<PatronRequest>> findRequestsForPatron(String patronSystem, String patronId, Pageable pageable);


	@Introspected
	public record ScheduledTrackingRecord(UUID id, @Nullable String status_code, @Nullable Instant next_scheduled_poll) {	};

	// If you change the where clause, make sure you keep the index aligned otherwise it will probably do a full table scan 
	@Query(value = "SELECT pr.id, pr.status_code, pr.next_scheduled_poll from patron_request pr where pr.next_scheduled_poll < now() and pr.is_too_long = false order by pr.next_scheduled_poll", nativeQuery = true)
	Publisher<ScheduledTrackingRecord> findScheduledChecks();

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
	Publisher<Void> updateIsTooLongAndNeedsAttention(@Id UUID id, Boolean isTooLong, Boolean needsAttention);
	
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

	@NotNull
	@SingleResult
	@Query(value = "SELECT pr.* from patron_request pr, inactive_supplier_request sr where sr.patron_request_id = pr.id and sr.id = :inactiveSupplierRequestId", nativeQuery = true)
	Publisher<PatronRequest> getPatronRequestByInactiveSupplierRequestId(UUID inactiveSupplierRequestId);

	// Borrowing system tracking updates
	Publisher<Long> updateLocalRequestTracking(@Id @NotNull UUID id, @Nullable String localRequestStatus, @Nullable String rawLocalRequestStatus, Instant localRequestLastCheckTimestamp, Long localRequestStatusRepeat);
	Publisher<Long> updateLocalItemTracking(@Id @NotNull UUID id, @Nullable String localItemStatus, @Nullable String rawLocalItemStatus, Instant localItemLastCheckTimestamp, Long localItemStatusRepeat);

	// Pickup system tracking updates
	Publisher<Long> updatePickupRequestTracking(@Id @NotNull UUID id, @Nullable String pickupRequestStatus, @Nullable String rawPickupRequestStatus, Instant pickupRequestLastCheckTimestamp, Long pickupRequestStatusRepeat);
	Publisher<Long> updatePickupItemTracking(@Id @NotNull UUID id, @Nullable String pickupItemStatus, @Nullable String rawPickupItemStatus, Instant pickupItemLastCheckTimestamp, Long pickupItemStatusRepeat);

	@Join("requestingIdentity")
	Publisher<PatronRequest> findAllByPatronHostlmsCodeAndBibClusterIdOrderByDateCreatedDesc(
		@NotNull @NonNull String patronHostlmsCode, @NotNull @NonNull UUID bibClusterId);

	Publisher<PatronRequest> findAllByPickupLocationCode(String pickupLocationCode);

  @SingleResult
  @Query(value = """
    select count(pr.*) 
    from patron_request pr, 
         patron_identity pi, 
         host_lms hl
    where pr.requesting_identity_id = pi.id 
      and pi.host_lms_id = hl.id
      and pi.local_id=:patronId
      and hl.code = :hostLmsCode
      and pr.status_code in ( 'SUBMITTED_TO_DCB', 'PATRON_VERIFIED', 'RESOLVED', 'REQUEST_PLACED_AT_SUPPLYING_AGENCY',
                              'CONFIRMED', 'REQUEST_PLACED_AT_BORROWING_AGENCY', 'REQUEST_PLACED_AT_PICKUP_AGENCY',
                              'RECEIVED_AT_PICKUP', 'READY_FOR_PICKUP', 'LOANED', 'PICKUP_TRANSIT' )
    """, nativeQuery = true)
  public Publisher<Long> getActiveRequestCountForPatron(String hostLmsCode, String patronId);

	// The following methods provide a limited patron request summary for a patron's active and all-time requests.
	// This is intended for discovery services who cannot make use of the GraphQL APIs available
	// And who need to provide a summary for patrons
	@Query(value = """
        SELECT 
            pr.id, 
            CAST(pr.status_code AS VARCHAR) as status, 
            CAST(pr.next_expected_status AS VARCHAR) as next_expected_status, 
            pr.elapsed_time_in_current_status as time_in_state, 
            pr.error_message, 
            cr.title as title, 
            pr.pickup_location_code, 
            pr.date_created, 
            pr.date_updated
        FROM patron_request pr
        JOIN patron_identity pi ON pr.requesting_identity_id = pi.id
        LEFT JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
        WHERE pr.patron_hostlms_code = :hostLmsCode 
          AND pi.local_barcode LIKE CONCAT('%', :patronBarcode, '%')
          AND pr.status_code IN (
              'SUBMITTED_TO_DCB', 
              'PATRON_VERIFIED', 
              'RESOLVED', 
              'REQUEST_PLACED_AT_SUPPLYING_AGENCY', 
              'CONFIRMED', 
              'REQUEST_PLACED_AT_BORROWING_AGENCY', 
              'REQUEST_PLACED_AT_PICKUP_AGENCY', 
              'RECEIVED_AT_PICKUP', 
              'READY_FOR_PICKUP', 
              'LOANED', 
              'PICKUP_TRANSIT'
          )
        ORDER BY pr.date_updated DESC
    """, nativeQuery = true)
	Flux<PatronRequestSummary> findActiveRequestsForPatronByBarcode(String hostLmsCode, String patronBarcode);

	// While the active requests are most useful for the discovery services, all time data also has its use for consortial admins
	@Query(value = """
        SELECT 
            pr.id, 
            CAST(pr.status_code AS VARCHAR) as status, 
            CAST(pr.next_expected_status AS VARCHAR) as next_expected_status, 
            pr.elapsed_time_in_current_status as time_in_state, 
            pr.error_message, 
            cr.title as title, 
            pr.pickup_location_code, 
            pr.date_created, 
            pr.date_updated
        FROM patron_request pr
        JOIN patron_identity pi ON pr.requesting_identity_id = pi.id
        LEFT JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
        WHERE pr.patron_hostlms_code = :hostLmsCode 
          AND pi.local_barcode LIKE CONCAT('%', :patronBarcode, '%')
        ORDER BY pr.date_updated DESC
    """, nativeQuery = true)
	Flux<PatronRequestSummary> findAllRequestsForPatronByBarcode(String hostLmsCode, String patronBarcode);

}

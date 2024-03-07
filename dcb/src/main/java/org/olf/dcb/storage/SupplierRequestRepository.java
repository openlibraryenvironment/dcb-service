package org.olf.dcb.storage;

import java.util.UUID;

import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;
import java.time.Instant;


public interface SupplierRequestRepository {
	@NonNull
	@SingleResult
	Publisher<? extends SupplierRequest> save(@Valid @NotNull SupplierRequest supplierRequest);

	@NonNull
	@SingleResult
	Publisher<? extends SupplierRequest> update(@Valid @NotNull SupplierRequest supplierRequest);

	@NonNull
	@SingleResult
	Publisher<SupplierRequest> findById(@NotNull UUID id);

	@NonNull
	Publisher<SupplierRequest> findAllByPatronRequest(@NotNull PatronRequest pr);

	Publisher<SupplierRequest> findAllByPatronRequestAndIsActive(@NotNull PatronRequest pr, Boolean isActive);

	@NonNull
	Publisher<SupplierRequest> findByPatronRequest(@NotNull PatronRequest pr);

	@NonNull
	Publisher<SupplierRequest> queryAll();

	Publisher<Void> delete(UUID id);

	@Query(value = "SELECT sr.* from supplier_request sr, patron_request pr where pr.id = sr.patron_request_id and sr.status_code in ( select code from status_code where model = 'SupplierRequest' and tracked = true ) and ( sr.local_id is not null ) and ( sr.local_status <> 'MISSING') and pr.status_code not in ('ERROR', 'FINALISED', 'COMPLETED')", nativeQuery = true)
	Publisher<SupplierRequest>  findTrackedSupplierHolds();

        @NonNull
        @SingleResult
        Publisher<Boolean> existsById(@NonNull UUID id);

        @SingleResult
        @NonNull
        default Publisher<SupplierRequest> saveOrUpdate(@Valid @NotNull SupplierRequest sr) {
                return Mono.from(this.existsById(sr.getId()))
                        .flatMap( update -> Mono.from(update ? this.update(sr) : this.save(sr)) )
                        ;
        }

        @SingleResult
        @NonNull
        Publisher<PatronRequest> findPatronRequestById(UUID supplierRequestId);


	@Query(value = "SELECT s.* from supplier_request s, patron_request pr where pr.id = s.patron_request_id and s.local_item_id is not null and ( s.local_item_status is null or s.local_item_status in ( select code from status_code where model = 'SupplierItem' and tracked = true ) ) and pr.status_code not in ('ERROR', 'FINALISED', 'COMPLETED')", nativeQuery = true)
	Publisher<SupplierRequest> findTrackedSupplierItems();
	Publisher<PatronIdentity> findVirtualIdentityById(UUID supplierRequestId);

	Publisher<Void> updateLocalItemLastCheckTimestamp(@NotNull UUID id, Instant localItemLastCheckTimestamp);
	Publisher<Void> updateLocalRequestLastCheckTimestamp(@NotNull UUID id, Instant localRequestLastCheckTimestamp);
}


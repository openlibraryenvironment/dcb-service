package org.olf.dcb.storage;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.SupplierRequest;
import org.reactivestreams.Publisher;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import reactor.core.publisher.Mono;

public interface SupplierRequestRepository {
	@NonNull
	@SingleResult
	Publisher<? extends SupplierRequest> save(@Valid @NotNull SupplierRequest supplierRequest);

	@NonNull
	@SingleResult
	Publisher<? extends SupplierRequest> update(@Valid @NotNull SupplierRequest supplierRequest);

	@NonNull
	Publisher<SupplierRequest> findAllByPatronRequest(@NotNull PatronRequest pr);

	Publisher<SupplierRequest> findAllByPatronRequestAndIsActive(@NotNull PatronRequest pr, Boolean isActive);

	@NonNull
	Publisher<SupplierRequest> findByPatronRequest(@NotNull PatronRequest pr);

	@NonNull
	Publisher<SupplierRequest> queryAll();

	Publisher<Void> delete(UUID id);

	@Query(value = "SELECT sr.* from supplier_request sr where sr.status_code in ( select code from status_code where model = 'SupplierRequest' and tracked = true ) and ( sr.local_id is not null ) and ( sr.local_status <> 'MISSING') ", nativeQuery = true)
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


	@Query(value = "SELECT s.* from supplier_request s, patron_request pr where pr.id = s.patron_request_id and s.local_item_id is not null and ( s.local_item_status is null or s.local_item_status in ( select code from status_code where model = 'SupplierItem' and tracked = true ) ) and pr.status_code <> 'ERROR'", nativeQuery = true)
	Publisher<SupplierRequest> findTrackedSupplierItems();
	Publisher<PatronIdentity> findVirtualIdentityById(UUID supplierRequestId);
}


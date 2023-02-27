package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

public interface SupplierRequestRepository {

	@NonNull
	@SingleResult
	Publisher<? extends SupplierRequest> save(@Valid @NotNull SupplierRequest supplierRequest);

	@NonNull
	@SingleResult
	Publisher<SupplierRequest> findById(@NotNull UUID id);
	
	@NonNull
	Publisher<SupplierRequest> findAllByPatronRequest(@NotNull PatronRequest pr);

	@NonNull
	Publisher<SupplierRequest> findAll();

	Publisher<Void> delete(UUID id);
}

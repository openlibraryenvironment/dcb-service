package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.olf.dcb.core.model.InactiveSupplierRequest;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.reactivestreams.Publisher;
import java.util.UUID;


public interface InactiveSupplierRequestRepository {
	@NonNull
	@SingleResult
	Publisher<? extends InactiveSupplierRequest> save(@Valid @NotNull InactiveSupplierRequest inactiveSupplierRequest);

	@NonNull
	@SingleResult
	Publisher<InactiveSupplierRequest> findById(@NotNull UUID id);

	@NonNull
	Publisher<InactiveSupplierRequest> findAllByPatronRequest(@NotNull PatronRequest pr);

	@NonNull
	Publisher<InactiveSupplierRequest> findByPatronRequest(@NotNull PatronRequest pr);

	@NonNull
	Publisher<InactiveSupplierRequest> queryAll();

	@Transactional
	Publisher<Void> delete(UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@SingleResult
	@NonNull
	Publisher<PatronRequest> findPatronRequestById(UUID inactiveSupplierRequestId);
}


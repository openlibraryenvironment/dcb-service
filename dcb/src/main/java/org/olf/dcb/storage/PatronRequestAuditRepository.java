package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.reactivestreams.Publisher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface PatronRequestAuditRepository {

	@NonNull
	@SingleResult
	Publisher<? extends PatronRequestAudit> save(@Valid @NotNull @NonNull PatronRequestAudit patronRequestAudit);

	Publisher<PatronRequestAudit> findByPatronRequest(@NotNull @NonNull PatronRequest patronRequest);
	Publisher<PatronRequestAudit> findAllByPatronRequest(@NotNull @NonNull PatronRequest patronRequest);

	@NonNull Publisher<Long> deleteAll();
}

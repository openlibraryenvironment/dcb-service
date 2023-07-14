package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.reactivestreams.Publisher;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public interface PatronRequestAuditRepository {

	@NonNull
	@SingleResult
	Publisher<PatronRequestAudit> save(@Valid @NotNull @NonNull PatronRequestAudit patronRequestAudit);

	Publisher<PatronRequestAudit> findByPatronRequest(@NotNull @NonNull PatronRequest patronRequest);

	@NonNull Publisher<Long> deleteAll();
}

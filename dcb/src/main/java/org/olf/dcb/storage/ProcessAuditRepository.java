package org.olf.dcb.storage;

import java.util.UUID;

import org.olf.dcb.core.audit.model.ProcessAuditLogEntry;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import jakarta.validation.Valid;

public interface ProcessAuditRepository {

	@NonNull
	Publisher<ProcessAuditLogEntry> findAllBySubjectId(@NonNull UUID subjectId);
	
	
	@NonNull
	@SingleResult
	@SuppressWarnings("all")
	<S extends ProcessAuditLogEntry> Publisher<S> save(@NonNull @Valid S entity);
	
	@NonNull
	@SingleResult
	Publisher<Long> deleteAllBySubjectIdAndProcessTypeAndProcessIdNot(@NonNull UUID subjectId, @NonNull String auditProcessType, @NonNull UUID auditProcessId);
	
}

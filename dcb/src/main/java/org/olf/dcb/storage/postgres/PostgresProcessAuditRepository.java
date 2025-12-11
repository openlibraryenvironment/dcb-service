package org.olf.dcb.storage.postgres;

import java.util.UUID;

import org.olf.dcb.core.audit.model.ProcessAuditLogEntry;
import org.olf.dcb.storage.ProcessAuditRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional(propagation = Propagation.MANDATORY)
public interface PostgresProcessAuditRepository extends ReactiveStreamsCrudRepository<ProcessAuditLogEntry, UUID>, ProcessAuditRepository {
}

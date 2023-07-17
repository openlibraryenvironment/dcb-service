package org.olf.dcb.storage.postgres;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;

import javax.transaction.Transactional;
import java.util.UUID;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresPatronRequestAuditRepository
	extends ReactiveStreamsPageableRepository<PatronRequestAudit, UUID>, PatronRequestAuditRepository {
}

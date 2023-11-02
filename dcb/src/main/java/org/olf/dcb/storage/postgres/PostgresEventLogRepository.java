package org.olf.dcb.storage.postgres;

import java.util.UUID;

import org.olf.dcb.core.model.Event;
import org.olf.dcb.storage.EventLogRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.jpa.reactive.ReactiveStreamsJpaSpecificationExecutor;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresEventLogRepository
	extends ReactiveStreamsPageableRepository<Event, UUID>,
	ReactiveStreamsJpaSpecificationExecutor<Event>, EventLogRepository {


}

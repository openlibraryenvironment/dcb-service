package org.olf.dcb.storage.postgres;

import java.util.UUID;

import org.olf.dcb.indexing.model.SharedIndexQueueEntry;
import org.olf.dcb.indexing.storage.SharedIndexQueueRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional()
public interface PostgresSharedIndexQueueRepository extends ReactiveStreamsPageableRepository<SharedIndexQueueEntry, UUID>, SharedIndexQueueRepository {

}

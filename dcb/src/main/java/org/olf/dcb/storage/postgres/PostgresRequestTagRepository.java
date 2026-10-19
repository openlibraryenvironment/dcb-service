package org.olf.dcb.storage.postgres;

import org.olf.dcb.core.model.RequestTag;
import org.olf.dcb.storage.RequestTagRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresRequestTagRepository
	extends ReactiveStreamsPageableRepository<RequestTag, String>, RequestTagRepository {
}

package org.olf.dcb.storage.postgres;

import java.util.UUID;

import jakarta.transaction.Transactional;

import org.olf.dcb.core.model.StatusCode;
import org.olf.dcb.storage.StatusCodeRepository;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;

@SuppressWarnings("unchecked")
@Singleton
@BootstrapContextCompatible
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresStatusCodeRepository extends ReactiveStreamsPageableRepository<StatusCode, UUID>, StatusCodeRepository {
}


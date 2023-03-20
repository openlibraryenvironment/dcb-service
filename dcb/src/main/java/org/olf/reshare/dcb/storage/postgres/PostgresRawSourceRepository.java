package org.olf.reshare.dcb.storage.postgres;

import java.util.UUID;

import javax.transaction.Transactional;

import org.olf.reshare.dcb.ingest.model.RawSource;
import org.olf.reshare.dcb.storage.RawSourceRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import jakarta.inject.Singleton;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresRawSourceRepository extends ReactiveStreamsCrudRepository<RawSource, UUID>, RawSourceRepository {
}

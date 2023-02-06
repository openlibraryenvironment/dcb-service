package org.olf.reshare.dcb.storage.postgres;

import java.util.UUID;

import org.olf.reshare.dcb.core.model.Agency;
import org.olf.reshare.dcb.storage.AgencyRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresAgencyRepository extends ReactiveStreamsPageableRepository<Agency, UUID>, AgencyRepository {
}

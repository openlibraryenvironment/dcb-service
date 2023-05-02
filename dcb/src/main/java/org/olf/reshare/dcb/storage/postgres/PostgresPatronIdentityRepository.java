package org.olf.reshare.dcb.storage.postgres;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.storage.PatronIdentityRepository;

import javax.transaction.Transactional;
import java.util.UUID;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresPatronIdentityRepository extends ReactiveStreamsPageableRepository<PatronIdentity, UUID>, PatronIdentityRepository {
	
}

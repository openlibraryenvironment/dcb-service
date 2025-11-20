package org.olf.dcb.storage.postgres;

import java.util.UUID;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.HostLmsRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.jpa.reactive.ReactiveStreamsJpaSpecificationExecutor;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresHostLmsRepository extends HostLmsRepository,
																									 ReactiveStreamsPageableRepository<DataHostLms, UUID>, 
                                                   ReactiveStreamsJpaSpecificationExecutor<DataHostLms>{
	
}

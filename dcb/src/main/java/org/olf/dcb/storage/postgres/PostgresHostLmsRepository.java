package org.olf.dcb.storage.postgres;

import java.util.UUID;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.HostLmsRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
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
public interface PostgresHostLmsRepository extends ReactiveStreamsPageableRepository<DataHostLms, UUID>, 
                                                        ReactiveStreamsJpaSpecificationExecutor<DataHostLms>,
                                                        HostLmsRepository {
	
	@NonNull
	@Override
	@SingleResult
	Publisher<DataHostLms> findById(@NonNull UUID id);
}

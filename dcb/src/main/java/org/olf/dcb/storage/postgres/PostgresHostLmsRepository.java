package org.olf.dcb.storage.postgres;

import java.util.UUID;

import jakarta.transaction.Transactional;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.HostLmsRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;

import org.reactivestreams.Publisher;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.micronaut.data.repository.jpa.reactive.ReactiveStreamsJpaSpecificationExecutor;

import org.reactivestreams.Publisher;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresHostLmsRepository extends ReactiveStreamsPageableRepository<DataHostLms, UUID>, 
                                                        ReactiveStreamsJpaSpecificationExecutor<DataHostLms>,
                                                        HostLmsRepository {

        @NonNull
        @SingleResult
        Publisher<DataHostLms> findById(@NonNull UUID id);
}

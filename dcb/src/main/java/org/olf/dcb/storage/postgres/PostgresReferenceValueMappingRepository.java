package org.olf.dcb.storage.postgres;

import java.util.UUID;

import jakarta.transaction.Transactional;

import org.olf.dcb.core.audit.*;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import io.micronaut.data.repository.jpa.reactive.ReactiveStreamsJpaSpecificationExecutor;


@Singleton
@Audit
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresReferenceValueMappingRepository extends 
        ReactiveStreamsPageableRepository<ReferenceValueMapping, UUID>, 
        ReactiveStreamsJpaSpecificationExecutor<ReferenceValueMapping>,
        ReferenceValueMappingRepository {
}

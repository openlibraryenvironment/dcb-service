package org.olf.dcb.storage.postgres;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.jpa.reactive.ReactiveStreamsJpaSpecificationExecutor;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.olf.dcb.core.model.InactiveSupplierRequest;
import org.olf.dcb.storage.InactiveSupplierRequestRepository;

import java.util.UUID;


@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresInactiveSupplierRequestRepository extends
	ReactiveStreamsPageableRepository<InactiveSupplierRequest, UUID>,
	ReactiveStreamsJpaSpecificationExecutor<InactiveSupplierRequest>,
	InactiveSupplierRequestRepository {
}

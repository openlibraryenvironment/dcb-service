//package org.olf.dcb.storage.postgres;
//
//import java.util.UUID;
//
//import org.olf.dcb.core.model.AgencyDataImpl;
//import org.olf.dcb.storage.AgencyRepository;
//
//import io.micronaut.data.model.query.builder.sql.Dialect;
//import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
//import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
//import jakarta.inject.Singleton;
//@SuppressWarnings("unchecked")
//@Singleton
//@R2dbcRepository(dialect = Dialect.POSTGRES)
//public interface PostgresAgencyRepository extends ReactiveStreamsPageableRepository<AgencyDataImpl, UUID>, AgencyRepository {
//}

package org.olf.dcb.storage.postgres;

import java.util.UUID;

import jakarta.transaction.Transactional;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor;


import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.reactivestreams.Publisher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;


@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresAgencyRepository extends ReactiveStreamsPageableRepository<DataAgency, UUID>, AgencyRepository {
}

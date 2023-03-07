//package org.olf.reshare.dcb.storage.postgres;
//
//import java.util.UUID;
//
//import org.olf.reshare.dcb.core.model.AgencyDataImpl;
//import org.olf.reshare.dcb.storage.AgencyRepository;
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

package org.olf.reshare.dcb.storage.postgres;

import java.util.UUID;

import javax.transaction.Transactional;

import org.olf.reshare.dcb.core.model.DataAgency;
import org.olf.reshare.dcb.storage.AgencyRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresAgencyRepository extends ReactiveStreamsPageableRepository<DataAgency, UUID>, AgencyRepository {
}

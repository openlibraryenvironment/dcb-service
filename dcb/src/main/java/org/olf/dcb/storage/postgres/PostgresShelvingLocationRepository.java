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

import javax.transaction.Transactional;

import org.olf.dcb.core.model.ShelvingLocation;
import org.olf.dcb.storage.ShelvingLocationRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresShelvingLocationRepository extends ReactiveStreamsPageableRepository<ShelvingLocation, UUID>, ShelvingLocationRepository {
}

package org.olf.dcb.storage.postgres;

import org.olf.dcb.core.model.Syslog;
import org.olf.dcb.storage.SyslogRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
@Transactional
@SuppressWarnings("unchecked")
@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresSyslogRepository extends ReactiveStreamsPageableRepository<Syslog, Long>, ReactorJpaSpecificationExecutor<Syslog>, SyslogRepository {
}

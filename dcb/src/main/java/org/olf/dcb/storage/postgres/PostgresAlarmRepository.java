package org.olf.dcb.storage.postgres;

import java.util.UUID;

import jakarta.transaction.Transactional;

import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.storage.AlarmRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import org.reactivestreams.Publisher;
import jakarta.validation.constraints.NotNull;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresAlarmRepository extends ReactiveStreamsPageableRepository<Alarm, UUID>, ReactorJpaSpecificationExecutor<Alarm>, AlarmRepository {

	@NonNull
	@SingleResult
	Publisher<Alarm> findById(@NotNull UUID id);

}

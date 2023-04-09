package org.olf.reshare.dcb.storage.postgres;

import java.util.UUID;

import javax.transaction.Transactional;

import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;

import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.reactivestreams.Publisher;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import javax.validation.Valid;


@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresClusterRecordRepository extends ReactiveStreamsPageableRepository<ClusterRecord, UUID>, ClusterRecordRepository {

	@Join("bibs")
        Publisher<Page<ClusterRecord>> findAll(@Valid Pageable pageable);
}


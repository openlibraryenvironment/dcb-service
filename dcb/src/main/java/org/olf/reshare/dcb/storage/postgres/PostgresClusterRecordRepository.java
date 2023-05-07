package org.olf.reshare.dcb.storage.postgres;

import java.util.UUID;

import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import io.micronaut.core.annotation.NonNull;


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
import java.time.Instant;


@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresClusterRecordRepository extends ReactiveStreamsPageableRepository<ClusterRecord, UUID>, ClusterRecordRepository {

	/* @Join("bibs")  we don't need the bibs, just the record from the primary bib*/
        Publisher<Page<ClusterRecord>> findAll(@Valid Pageable pageable);

	/*
	// Doing the join can cause recursion in the graph
	@Join("bibs")
        Publisher<? extends ClusterRecord> findOneById(@NonNull UUID id);

	@Join("bibs")
        Publisher<ClusterRecord> findById(@NotNull UUID id);
	*/

	/* @Join("bibs") */
        Publisher<Page<ClusterRecord>> findByDateUpdatedGreaterThanOrderByDateUpdated(Instant i, @Valid Pageable pageable);

}


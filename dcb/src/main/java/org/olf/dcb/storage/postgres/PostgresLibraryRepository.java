package org.olf.dcb.storage.postgres;

import java.util.UUID;

import jakarta.transaction.Transactional;

import org.olf.dcb.core.model.Library;
import org.olf.dcb.storage.LibraryRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import org.reactivestreams.Publisher;
import jakarta.validation.constraints.NotNull;


@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresLibraryRepository extends ReactiveStreamsPageableRepository<Library, UUID>,
	ReactorJpaSpecificationExecutor<Library>,
	LibraryRepository {
	@NonNull
	@SingleResult
	Publisher<Library> findById(@NotNull UUID id);

}

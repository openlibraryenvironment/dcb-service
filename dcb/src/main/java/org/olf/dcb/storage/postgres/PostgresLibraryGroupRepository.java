package org.olf.dcb.storage.postgres;

import java.util.UUID;

import io.micronaut.core.annotation.*;
import io.micronaut.core.async.annotation.*;
import jakarta.transaction.Transactional;

import org.olf.dcb.core.model.*;
import org.olf.dcb.storage.LibraryGroupRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import io.micronaut.data.repository.jpa.reactive.ReactiveStreamsJpaSpecificationExecutor;
import org.reactivestreams.*;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresLibraryGroupRepository extends
	ReactiveStreamsPageableRepository<LibraryGroup, UUID>,
	ReactiveStreamsJpaSpecificationExecutor<LibraryGroup>,
	LibraryGroupRepository {
	@NonNull
	@SingleResult
	Publisher<LibraryGroup> findById(@NonNull UUID id);
}

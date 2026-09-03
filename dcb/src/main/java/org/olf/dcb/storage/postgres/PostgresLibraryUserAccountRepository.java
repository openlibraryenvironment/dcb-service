package org.olf.dcb.storage.postgres;

import java.util.UUID;

import org.olf.dcb.core.model.LibraryUserAccount;
import org.olf.dcb.storage.LibraryUserAccountRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresLibraryUserAccountRepository
	extends ReactiveStreamsPageableRepository<LibraryUserAccount, UUID>, LibraryUserAccountRepository {
}

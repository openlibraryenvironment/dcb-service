package org.olf.dcb.storage.postgres;

import java.util.UUID;

import jakarta.transaction.Transactional;

import org.olf.dcb.core.model.LibraryGroupMember;
import org.olf.dcb.storage.LibraryGroupMemberRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresLibraryGroupMemberRepository extends ReactiveStreamsPageableRepository<LibraryGroupMember, UUID>, LibraryGroupMemberRepository {
}
